import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import type { IncomingMessage, ServerResponse } from 'node:http';

const ISSUER = (process.env.MCP_OAUTH_ISSUER ?? 'https://healthos-mcp.onrender.com').replace(/\/$/, '');
const RESOURCE = `${ISSUER}/mcp`;
const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID ?? '';
const GOOGLE_CLIENT_SECRET = process.env.GOOGLE_CLIENT_SECRET ?? '';
const SIGNING_SECRET = process.env.MCP_OAUTH_SIGNING_SECRET ?? process.env.MCP_API_KEY ?? '';
const GOOGLE_CALLBACK = `${ISSUER}/oauth/google/callback`;
const ACCESS_TOKEN_TTL_SECONDS = 3600;
const CODE_TTL_MS = 5 * 60 * 1000;

type Client = { clientId: string; clientName?: string; redirectUris: string[] };
type PendingGoogle = { clientId: string; redirectUri: string; codeChallenge: string; scope: string; state: string; expiresAt: number };
type AuthorizationCode = { clientId: string; redirectUri: string; codeChallenge: string; scope: string; subject: string; expiresAt: number };
type RefreshToken = { clientId: string; scope: string; subject: string; expiresAt: number };

const clients = new Map<string, Client>();
const pendingGoogle = new Map<string, PendingGoogle>();
const authorizationCodes = new Map<string, AuthorizationCode>();
const refreshTokens = new Map<string, RefreshToken>();

if (!GOOGLE_CLIENT_ID) throw new Error('GOOGLE_CLIENT_ID is required');
if (!GOOGLE_CLIENT_SECRET) throw new Error('GOOGLE_CLIENT_SECRET is required');
if (!SIGNING_SECRET) throw new Error('MCP_OAUTH_SIGNING_SECRET or MCP_API_KEY is required');

function randomId(bytes = 32) { return randomBytes(bytes).toString('base64url'); }
function b64(value: Buffer) { return value.toString('base64url'); }
function safeEqual(a: string, b: string) {
  const aa = Buffer.from(a); const bb = Buffer.from(b);
  return aa.length === bb.length && timingSafeEqual(aa, bb);
}
function sign(value: string) { return b64(createHmac('sha256', SIGNING_SECRET).update(value).digest()); }
function createToken(payload: Record<string, unknown>) {
  const body = b64(Buffer.from(JSON.stringify(payload), 'utf8'));
  return `${body}.${sign(body)}`;
}
function verifyToken(token: string): Record<string, unknown> | null {
  const [body, signature] = token.split('.');
  if (!body || !signature || !safeEqual(signature, sign(body))) return null;
  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as Record<string, unknown>;
    if (typeof payload.exp !== 'number' || payload.exp <= Math.floor(Date.now() / 1000)) return null;
    if (payload.iss !== ISSUER || payload.aud !== RESOURCE || payload.scope !== 'healthos.read') return null;
    return payload;
  } catch { return null; }
}
function json(res: ServerResponse, status: number, value: unknown) {
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
  res.end(JSON.stringify(value));
}
function redirect(res: ServerResponse, location: string) {
  res.writeHead(302, { Location: location, 'Cache-Control': 'no-store' }); res.end();
}
async function readBody(req: IncomingMessage) {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks).toString('utf8');
}
function cleanup() {
  const now = Date.now();
  for (const [k, v] of pendingGoogle) if (v.expiresAt <= now) pendingGoogle.delete(k);
  for (const [k, v] of authorizationCodes) if (v.expiresAt <= now) authorizationCodes.delete(k);
  for (const [k, v] of refreshTokens) if (v.expiresAt <= now) refreshTokens.delete(k);
}
function validRedirect(client: Client | undefined, redirectUri: string) {
  return !!client && client.redirectUris.includes(redirectUri);
}

export function oauthMetadata() {
  return {
    issuer: ISSUER,
    authorization_endpoint: `${ISSUER}/oauth/authorize`,
    token_endpoint: `${ISSUER}/oauth/token`,
    registration_endpoint: `${ISSUER}/oauth/register`,
    response_types_supported: ['code'],
    grant_types_supported: ['authorization_code', 'refresh_token'],
    code_challenge_methods_supported: ['S256'],
    token_endpoint_auth_methods_supported: ['none'],
    scopes_supported: ['healthos.read'],
    client_id_metadata_document_supported: false
  };
}

export function protectedResourceMetadata() {
  return {
    resource: RESOURCE,
    authorization_servers: [ISSUER],
    scopes_supported: ['healthos.read'],
    bearer_methods_supported: ['header']
  };
}

export function verifyOAuthAccessToken(token: string) {
  return verifyToken(token) !== null;
}

export async function handleOAuthRequest(req: IncomingMessage, res: ServerResponse): Promise<boolean> {
  cleanup();
  const url = new URL(req.url ?? '/', ISSUER);

  if (req.method === 'GET' && (url.pathname === '/.well-known/oauth-authorization-server' || url.pathname === '/.well-known/openid-configuration')) {
    json(res, 200, oauthMetadata()); return true;
  }
  if (req.method === 'GET' && (url.pathname === '/.well-known/oauth-protected-resource' || url.pathname === '/.well-known/oauth-protected-resource/mcp')) {
    json(res, 200, protectedResourceMetadata()); return true;
  }

  if (req.method === 'POST' && url.pathname === '/oauth/register') {
    let input: Record<string, unknown>;
    try { input = JSON.parse(await readBody(req)) as Record<string, unknown>; } catch { json(res, 400, { error: 'invalid_client_metadata' }); return true; }
    const redirectUris = Array.isArray(input.redirect_uris) ? input.redirect_uris.filter((x): x is string => typeof x === 'string') : [];
    if (!redirectUris.length) { json(res, 400, { error: 'invalid_redirect_uri' }); return true; }
    const clientId = randomId(24);
    clients.set(clientId, { clientId, clientName: typeof input.client_name === 'string' ? input.client_name : 'MCP client', redirectUris });
    json(res, 201, { client_id: clientId, client_name: input.client_name ?? 'MCP client', redirect_uris: redirectUris, grant_types: ['authorization_code', 'refresh_token'], response_types: ['code'], token_endpoint_auth_method: 'none' });
    return true;
  }

  if (req.method === 'GET' && url.pathname === '/oauth/authorize') {
    const clientId = url.searchParams.get('client_id') ?? '';
    const redirectUri = url.searchParams.get('redirect_uri') ?? '';
    const codeChallenge = url.searchParams.get('code_challenge') ?? '';
    const method = url.searchParams.get('code_challenge_method') ?? '';
    const responseType = url.searchParams.get('response_type') ?? '';
    const scope = url.searchParams.get('scope') ?? 'healthos.read';
    const state = url.searchParams.get('state') ?? '';
    const client = clients.get(clientId);
    if (!validRedirect(client, redirectUri) || responseType !== 'code' || !codeChallenge || method !== 'S256' || scope !== 'healthos.read') {
      json(res, 400, { error: 'invalid_request', error_description: 'Invalid OAuth request' }); return true;
    }
    const stateId = randomId(32);
    pendingGoogle.set(stateId, { clientId, redirectUri, codeChallenge, scope, state, expiresAt: Date.now() + CODE_TTL_MS });
    const google = new URL('https://accounts.google.com/o/oauth2/v2/auth');
    google.searchParams.set('client_id', GOOGLE_CLIENT_ID);
    google.searchParams.set('redirect_uri', GOOGLE_CALLBACK);
    google.searchParams.set('response_type', 'code');
    google.searchParams.set('scope', 'openid email profile');
    google.searchParams.set('state', stateId);
    google.searchParams.set('access_type', 'online');
    redirect(res, google.toString());
    return true;
  }

  if (req.method === 'GET' && url.pathname === '/oauth/google/callback') {
    const stateId = url.searchParams.get('state') ?? '';
    const pending = pendingGoogle.get(stateId);
    pendingGoogle.delete(stateId);
    if (!pending || pending.expiresAt <= Date.now()) { json(res, 400, { error: 'invalid_request', error_description: 'Expired OAuth state' }); return true; }
    const error = url.searchParams.get('error');
    if (error) { json(res, 400, { error: 'access_denied', error_description: error }); return true; }
    const code = url.searchParams.get('code') ?? '';
    if (!code) { json(res, 400, { error: 'invalid_request' }); return true; }

    const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ code, client_id: GOOGLE_CLIENT_ID, client_secret: GOOGLE_CLIENT_SECRET, redirect_uri: GOOGLE_CALLBACK, grant_type: 'authorization_code' })
    });
    if (!tokenResponse.ok) { json(res, 502, { error: 'invalid_grant', error_description: 'Google token exchange failed' }); return true; }
    const tokenJson = await tokenResponse.json() as { access_token?: string };
    if (!tokenJson.access_token) { json(res, 502, { error: 'invalid_grant' }); return true; }

    const userResponse = await fetch('https://openidconnect.googleapis.com/v1/userinfo', { headers: { Authorization: `Bearer ${tokenJson.access_token}` } });
    if (!userResponse.ok) { json(res, 502, { error: 'invalid_grant', error_description: 'Google userinfo lookup failed' }); return true; }
    const user = await userResponse.json() as { sub?: string; email?: string };
    if (!user.sub) { json(res, 502, { error: 'invalid_grant', error_description: 'Google account identity missing' }); return true; }

    const localCode = randomId(32);
    authorizationCodes.set(localCode, { clientId: pending.clientId, redirectUri: pending.redirectUri, codeChallenge: pending.codeChallenge, scope: pending.scope, subject: user.sub, expiresAt: Date.now() + CODE_TTL_MS });
    const callback = new URL(pending.redirectUri);
    callback.searchParams.set('code', localCode);
    if (pending.state) callback.searchParams.set('state', pending.state);
    redirect(res, callback.toString());
    return true;
  }

  if (req.method === 'POST' && url.pathname === '/oauth/token') {
    const body = new URLSearchParams(await readBody(req));
    const grantType = body.get('grant_type');
    if (grantType === 'refresh_token') {
      const refreshToken = body.get('refresh_token') ?? '';
      const stored = refreshTokens.get(refreshToken);
      if (!stored || stored.expiresAt <= Date.now()) { json(res, 400, { error: 'invalid_grant' }); return true; }
      const accessToken = createToken({ iss: ISSUER, aud: RESOURCE, sub: stored.subject, scope: stored.scope, exp: Math.floor(Date.now() / 1000) + ACCESS_TOKEN_TTL_SECONDS });
      json(res, 200, { access_token: accessToken, token_type: 'Bearer', expires_in: ACCESS_TOKEN_TTL_SECONDS, scope: stored.scope, refresh_token: refreshToken }); return true;
    }
    if (grantType !== 'authorization_code') { json(res, 400, { error: 'unsupported_grant_type' }); return true; }
    const code = body.get('code') ?? '';
    const stored = authorizationCodes.get(code);
    authorizationCodes.delete(code);
    if (!stored || stored.expiresAt <= Date.now()) { json(res, 400, { error: 'invalid_grant' }); return true; }
    const verifier = body.get('code_verifier') ?? '';
    const challenge = b64(createHash('sha256').update(verifier).digest());
    if (body.get('client_id') !== stored.clientId || body.get('redirect_uri') !== stored.redirectUri || !verifier || challenge !== stored.codeChallenge) { json(res, 400, { error: 'invalid_grant' }); return true; }
    const accessToken = createToken({ iss: ISSUER, aud: RESOURCE, sub: stored.subject, scope: stored.scope, exp: Math.floor(Date.now() / 1000) + ACCESS_TOKEN_TTL_SECONDS });
    const refreshToken = randomId(32);
    refreshTokens.set(refreshToken, { clientId: stored.clientId, scope: stored.scope, subject: stored.subject, expiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000 });
    json(res, 200, { access_token: accessToken, token_type: 'Bearer', expires_in: ACCESS_TOKEN_TTL_SECONDS, scope: stored.scope, refresh_token: refreshToken }); return true;
  }
  return false;
}
