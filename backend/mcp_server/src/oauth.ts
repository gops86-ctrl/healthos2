import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const ISSUER = (process.env.MCP_OAUTH_ISSUER ?? 'https://healthos-mcp.onrender.com').replace(/\/$/, '');
const RESOURCE = `${ISSUER}/mcp`;
const OAUTH_PASSWORD = process.env.MCP_OAUTH_PASSWORD ?? process.env.MCP_API_KEY ?? '';
const SIGNING_SECRET = process.env.MCP_OAUTH_SIGNING_SECRET ?? process.env.MCP_API_KEY ?? '';
const ACCESS_TOKEN_TTL_SECONDS = 3600;
const CODE_TTL_MS = 5 * 60 * 1000;

type Client = {
  clientId: string;
  clientName?: string;
  redirectUris: string[];
};

type AuthorizationCode = {
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  scope: string;
  expiresAt: number;
};

type RefreshToken = {
  clientId: string;
  scope: string;
  expiresAt: number;
};

const clients = new Map<string, Client>();
const authorizationCodes = new Map<string, AuthorizationCode>();
const refreshTokens = new Map<string, RefreshToken>();

if (!OAUTH_PASSWORD) throw new Error('MCP_OAUTH_PASSWORD or MCP_API_KEY is required');
if (!SIGNING_SECRET) throw new Error('MCP_OAUTH_SIGNING_SECRET or MCP_API_KEY is required');

function base64Url(value: Buffer): string {
  return value.toString('base64url');
}

function randomId(bytes = 32): string {
  return base64Url(randomBytes(bytes));
}

function sign(value: string): string {
  return base64Url(createHmac('sha256', SIGNING_SECRET).update(value).digest());
}

function createSignedToken(payload: Record<string, unknown>): string {
  const body = base64Url(Buffer.from(JSON.stringify(payload), 'utf8'));
  return `${body}.${sign(body)}`;
}

function verifySignedToken(token: string): Record<string, unknown> | null {
  const [body, signature] = token.split('.');
  if (!body || !signature) return null;

  const expected = sign(body);
  const a = Buffer.from(signature);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;

  try {
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8')) as Record<string, unknown>;
    if (typeof payload.exp !== 'number' || payload.exp <= Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function json(res: import('node:http').ServerResponse, status: number, value: unknown) {
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*'
  });
  res.end(JSON.stringify(value));
}

function redirect(res: import('node:http').ServerResponse, location: string) {
  res.writeHead(302, { Location: location, 'Cache-Control': 'no-store' });
  res.end();
}

export function oauthMetadata(): Record<string, unknown> {
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
    protected_resources: [RESOURCE],
    client_id_metadata_document_supported: false
  };
}

export function protectedResourceMetadata(): Record<string, unknown> {
  return {
    resource: RESOURCE,
    authorization_servers: [ISSUER],
    scopes_supported: ['healthos.read'],
    bearer_methods_supported: ['header']
  };
}

export async function handleOAuthRequest(
  req: import('node:http').IncomingMessage,
  res: import('node:http').ServerResponse
): Promise<boolean> {
  const url = new URL(req.url ?? '/', ISSUER);

  if (req.method === 'OPTIONS' && url.pathname.startsWith('/oauth/')) {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type,Authorization'
    });
    res.end();
    return true;
  }

  if (req.method === 'GET' && (url.pathname === '/.well-known/oauth-authorization-server' || url.pathname === '/.well-known/openid-configuration')) {
    json(res, 200, oauthMetadata());
    return true;
  }

  if (req.method === 'GET' && (url.pathname === '/.well-known/oauth-protected-resource' || url.pathname === '/.well-known/oauth-protected-resource/mcp')) {
    json(res, 200, protectedResourceMetadata());
    return true;
  }

  if (url.pathname === '/oauth/register' && req.method === 'POST') {
    const body = await readBody(req);
    let input: Record<string, unknown>;
    try {
      input = JSON.parse(body) as Record<string, unknown>;
    } catch {
      json(res, 400, { error: 'invalid_client_metadata' });
      return true;
    }

    const redirectUris = Array.isArray(input.redirect_uris) ? input.redirect_uris.filter((value): value is string => typeof value === 'string') : [];
    if (redirectUris.length === 0) {
      json(res, 400, { error: 'invalid_redirect_uri' });
      return true;
    }

    const clientId = randomId(24);
    clients.set(clientId, {
      clientId,
      clientName: typeof input.client_name === 'string' ? input.client_name : undefined,
      redirectUris
    });

    json(res, 201, {
      client_id: clientId,
      client_name: input.client_name ?? 'MCP client',
      redirect_uris: redirectUris,
      grant_types: ['authorization_code', 'refresh_token'],
      response_types: ['code'],
      token_endpoint_auth_method: 'none
'
    });
    return true;
  }

  if (url.pathname === '/oauth/authorize' && req.method === 'GET') {
    const clientId = url.searchParams.get('client_id') ?? '';
    const redirectUri = url.searchParams.get('redirect_uri') ?? '';
    const responseType = url.searchParams.get('response_type') ?? '';
    const state = url.searchParams.get('state') ?? '';
    const codeChallenge = url.searchParams.get('code_challenge') ?? '';
    const codeChallengeMethod = url.searchParams.get('code_challenge_method') ?? '';
    const scope = url.searchParams.get('scope') ?? 'healthos.read';
    const client = clients.get(clientId);

    if (!client || responseType !== 'code' || !client.redirectUris.includes(redirectUri) || !codeChallenge || codeChallengeMethod !== 'S256') {
      json(res, 400, { error: 'invalid_request', error_description: 'Invalid OAuth authorization request' });
      return true;
    }

    const hidden = [
      ['client_id', clientId], ['redirect_uri', redirectUri], ['response_type', 'code'],
      ['state', state], ['code_challenge', codeChallenge], ['code_challenge_method', 'S256'], ['scope', scope]
    ].map(([key, value]) => `<input type="hidden" name="${escapeHtml(key)}" value="${escapeHtml(value)}">`).join('');

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>HealthOS Authorization</title></head><body style="font-family:system-ui;max-width:520px;margin:60px auto;padding:24px"><h1>Connect HealthOS</h1><p>${escapeHtml(client.clientName ?? 'This AI client')} is requesting read-only access to your HealthOS data.</p><p><strong>Permission:</strong> healthos.read</p><form method="post" action="/oauth/authorize">${hidden}<label>HealthOS access key<br><input name="password" type="password" autocomplete="current-password" required style="width:100%;padding:10px;margin-top:6px"></label><button type="submit" style="margin-top:18px;padding:10px 16px">Authorize</button></form></body></html>`);
    return true;
  }

  if (url.pathname === '/oauth/authorize' && req.method === 'POST') {
    const body = await readBody(req);
    const params = new URLSearchParams(body);
    const clientId = params.get('client_id') ?? '';
    const redirectUri = params.get('redirect_uri') ?? '';
    const client = clients.get(clientId);
    const state = params.get('state') ?? '';

    if (!client || !client.redirectUris.includes(redirectUri)) {
      json(res, 400, { error: 'invalid_request' });
      return true;
    }

    if (!timingSafeEqual(Buffer.from(params.get('password') ?? ''), Buffer.from(OAUTH_PASSWORD))) {
      res.writeHead(401, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end('<h1>Authorization failed</h1><p>Invalid HealthOS access key.</p><p><a href="javascript:history.back()">Try again</a></p>');
      return true;
    }

    const code = randomId(32);
    authorizationCodes.set(code, {
      clientId,
      redirectUri,
      codeChallenge: params.get('code_challenge') ?? '',
      scope: params.get('scope') ?? 'healthos.read',
      expiresAt: Date.now() + CODE_TTL_MS
    });

    const callback = new URL(redirectUri);
    callback.searchParams.set('code', code);
    if (state) callback.searchParams.set('state', state);
    redirect(res, callback.toString());
    return true;
  }

  if (url.pathname === '/oauth/token' && req.method === 'POST') {
    const body = new URLSearchParams(await readBody(req));
    const grantType = body.get('grant_type');

    if (grantType === 'refresh_token') {
      const refreshToken = body.get('refresh_token') ?? '';
      const stored = refreshTokens.get(refreshToken);
      if (!stored || stored.expiresAt <= Date.now()) {
        json(res, 400, { error: 'invalid_grant' });
        return true;
      }
      const accessToken = createSignedToken({ sub: stored.clientId, scope: stored.scope, exp: Math.floor(Date.now() / 1000) + ACCESS_TOKEN_TTL_SECONDS });
      json(res, 200, { access_token: accessToken, token_type: 'Bearer', expires_in: ACCESS_TOKEN_TTL_SECONDS, scope: stored.scope, refresh_token: refreshToken });
      return true;
    }

    if (grantType !== 'authorization_code') {
      json(res, 400, { error: 'unsupported_grant_type' });
      return true;
    }

    const code = body.get('code') ?? '';
    const stored = authorizationCodes.get(code);
    authorizationCodes.delete(code);
    if (!stored || stored.expiresAt <= Date.now()) {
      json(res, 400, { error: 'invalid_grant' });
      return true;
    }

    const clientId = body.get('client_id') ?? '';
    const redirectUri = body.get('redirect_uri') ?? '';
    const verifier = body.get('code_verifier') ?? '';
    const challenge = base64Url(createHash('sha256').update(verifier).digest());
    if (clientId !== stored.clientId || redirectUri !== stored.redirectUri || !verifier || challenge !== stored.codeChallenge) {
      json(res, 400, { error: 'invalid_grant' });
      return true;
    }

    const accessToken = createSignedToken({ sub: clientId, scope: stored.scope, exp: Math.floor(Date.now() / 1000) + ACCESS_TOKEN_TTL_SECONDS });
    const refreshToken = randomId(32);
    refreshTokens.set(refreshToken, { clientId, scope: stored.scope, expiresAt: Date.now() + 30 * 24 * 60 * 60 * 1000 });
    json(res, 200, { access_token: accessToken, token_type: 'Bearer', expires_in: ACCESS_TOKEN_TTL_SECONDS, scope: stored.scope, refresh_token: refreshToken });
    return true;
  }

  return false;
}

export function verifyOAuthAccessToken(token: string): boolean {
  const payload = verifySignedToken(token);
  return Boolean(payload && payload.scope === 'healthos.read');
}

async function readBody(req: import('node:http').IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  return Buffer.concat(chunks).toString('utf8');
}
