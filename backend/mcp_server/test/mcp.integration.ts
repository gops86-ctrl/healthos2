import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { Client, StreamableHTTPClientTransport } from '@modelcontextprotocol/client';

const HEALTHOS_API_KEY = 'test-healthos-key';
const MCP_API_KEY = 'test-mcp-key';
const MCP_PORT = 43127;
const TEST_TIMEOUT_MS = 15_000;

const expectedMetrics = [
  { metricType: 'RESTING_HR', value: 52, recordedAtMillis: 1000 },
  { metricType: 'RESTING_HR', value: 50, recordedAtMillis: 2000 },
  { metricType: 'RESTING_HR', value: 48, recordedAtMillis: 3000 }
];
const expectedActivities = [{ activityType: 'RUN', distanceMeters: 5000, source: 'STRAVA', recordedAtMillis: 2000 }];
const expectedWorkouts = [{ name: 'Upper Body', source: 'HEVY', recordedAtMillis: 3000 }];
const expectedNutrition = [{ calories: 2200, proteinGrams: 140, recordedAtMillis: 4000 }];
const expectedBody = [{ weightKg: 75.5, source: 'HEALTHIFYME', recordedAtMillis: 5000 }];
const expectedLabs = [{ name: 'HbA1c', value: 5.2, unit: '%', recordedAtMillis: 6000 }];
const expectedProfile = { name: 'Test User', age: 30 };
const expectedSources = {
  GARMIN: ['VO2_MAX', 'RESTING_HR', 'HRV', 'SLEEP', 'STRESS', 'STEPS', 'recent activities'],
  STRAVA: ['historical/bulk activities'],
  HEVY: ['detailed strength workouts'],
  HEALTHIFYME: ['weight'],
  MYFITNESSPAL: ['nutrition/calories/macros'],
  LABS: ['manual lab results'],
  MANUAL: ['manual HealthOS entries']
};

function startMockHealthApi(): Promise<ReturnType<typeof createServer>> {
  const server = createServer((req, res) => {
    if (req.headers['x-healthos-api-key'] !== HEALTHOS_API_KEY) {
      res.writeHead(401);
      res.end('Unauthorized');
      return;
    }

    const url = new URL(req.url ?? '/', 'http://127.0.0.1');
    const start = Number(url.searchParams.get('start_millis') ?? Number.NEGATIVE_INFINITY);
    const end = Number(url.searchParams.get('end_millis') ?? Number.POSITIVE_INFINITY);
    const range = <T extends { recordedAtMillis: number }>(records: T[]) =>
      records.filter(record => record.recordedAtMillis >= start && record.recordedAtMillis <= end);

    let body: unknown;
    switch (url.pathname) {
      case '/health/metrics': {
        const metricType = url.searchParams.get('metric_type');
        body = range(expectedMetrics).filter(metric => !metricType || metric.metricType === metricType);
        break;
      }
      case '/health/activities': body = range(expectedActivities); break;
      case '/health/workouts': body = range(expectedWorkouts); break;
      case '/health/nutrition': body = range(expectedNutrition); break;
      case '/health/body': body = range(expectedBody); break;
      case '/health/labs': body = range(expectedLabs); break;
      case '/health/profile': body = expectedProfile; break;
      default:
        res.writeHead(404);
        res.end('Not found');
        return;
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(body));
  });

  return new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server)));
}

function serverPort(server: ReturnType<typeof createServer>): number {
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Mock API did not expose a TCP port');
  return address.port;
}

async function waitForMcpServer(): Promise<void> {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${MCP_PORT}/health`, {
        signal: AbortSignal.timeout(1_000)
      });
      if (response.ok) return;
    } catch {
      // Server is still starting.
    }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('MCP server did not become healthy within 5 seconds');
}

async function withTimeout<T>(promise: Promise<T>, label: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<T>((_, reject) => {
        timer = setTimeout(() => reject(new Error(`${label} timed out after ${TEST_TIMEOUT_MS}ms`)), TEST_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

async function callJson(client: Client, name: string, args?: Record<string, unknown>): Promise<unknown> {
  const result = await withTimeout(client.callTool({ name, arguments: args }), `MCP ${name}`);
  const text = result.content?.find(item => item.type === 'text');
  if (!text || text.type !== 'text') throw new Error(`${name} returned no text content`);
  return JSON.parse(text.text);
}

const mockApi = await startMockHealthApi();
const mockApiUrl = `http://127.0.0.1:${serverPort(mockApi)}`;
const mcpProcess = spawn(process.execPath, ['--import', 'tsx/esm', 'src/index.ts'], {
  cwd: new URL('..', import.meta.url).pathname,
  env: {
    ...process.env,
    PORT: String(MCP_PORT),
    HEALTHOS_API_URL: mockApiUrl,
    HEALTHOS_API_KEY,
    MCP_API_KEY
  },
  stdio: ['ignore', 'pipe', 'pipe']
});
mcpProcess.stdout?.resume();
mcpProcess.stderr?.resume();

let client: Client | undefined;
let transport: StreamableHTTPClientTransport | undefined;
try {
  await waitForMcpServer();

  client = new Client({ name: 'healthos-integration-test', version: '1.0.0' });
  transport = new StreamableHTTPClientTransport(new URL(`http://127.0.0.1:${MCP_PORT}/mcp`), {
    requestInit: { headers: { Authorization: `Bearer ${MCP_API_KEY}` } }
  });
  await withTimeout(client.connect(transport), 'MCP connect');

  const { tools } = await withTimeout(client.listTools(), 'MCP listTools');
  const expectedToolNames = [
    'get_current_metrics', 'get_metric_history', 'get_activity_history',
    'get_strength_history', 'get_nutrition_history', 'get_weight_history',
    'get_lab_results', 'get_user_profile', 'get_data_sources'
  ];
  const actualToolNames = tools.map(tool => tool.name).sort();
  const expectedSorted = [...expectedToolNames].sort();
  if (JSON.stringify(actualToolNames) !== JSON.stringify(expectedSorted)) {
    throw new Error(`Unexpected tool list: ${JSON.stringify(actualToolNames)}`);
  }

  const metricHistory = await callJson(client, 'get_metric_history', {
    metricType: 'RESTING_HR', startMillis: 2000, endMillis: 3000
  });
  if (JSON.stringify(metricHistory) !== JSON.stringify(expectedMetrics.slice(1))) {
    throw new Error(`Unexpected metric history: ${JSON.stringify(metricHistory)}`);
  }

  const currentMetrics = await callJson(client, 'get_current_metrics', { metricType: 'RESTING_HR' });
  if (JSON.stringify(currentMetrics) !== JSON.stringify(expectedMetrics)) {
    throw new Error(`Unexpected current metrics: ${JSON.stringify(currentMetrics)}`);
  }

  const checks: Array<[string, unknown]> = [
    ['get_activity_history', expectedActivities],
    ['get_strength_history', expectedWorkouts],
    ['get_nutrition_history', expectedNutrition],
    ['get_weight_history', expectedBody],
    ['get_lab_results', expectedLabs],
    ['get_user_profile', expectedProfile],
    ['get_data_sources', expectedSources]
  ];
  for (const [toolName, expected] of checks) {
    const actual = await callJson(client, toolName);
    if (JSON.stringify(actual) !== JSON.stringify(expected)) {
      throw new Error(`Unexpected ${toolName} result: ${JSON.stringify(actual)}`);
    }
  }

  console.log(`MCP integration test passed: ${expectedToolNames.length} tools verified end-to-end.`);
} finally {
  await withTimeout(transport?.terminateSession() ?? Promise.resolve(), 'MCP terminateSession').catch(() => undefined);
  await withTimeout(client?.close() ?? Promise.resolve(), 'MCP close').catch(() => undefined);
  mockApi.closeAllConnections();
  mockApi.close();
  if (mcpProcess.exitCode === null) mcpProcess.kill('SIGTERM');
  if (mcpProcess.exitCode === null) {
    await new Promise<void>(resolve => {
      const timer = setTimeout(() => {
        if (mcpProcess.exitCode === null) mcpProcess.kill('SIGKILL');
        resolve();
      }, 2_000);
      mcpProcess.once('exit', () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
}
