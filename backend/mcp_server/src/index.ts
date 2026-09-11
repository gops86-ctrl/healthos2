```ts
import { createServer } from 'node:http';
import { McpServer, createMcpHandler } from '@modelcontextprotocol/server';
import { toNodeHandler } from '@modelcontextprotocol/node';
import * as z from 'zod/v4';

const PORT = Number(process.env.PORT ?? 3000);
const HEALTHOS_API_URL = (process.env.HEALTHOS_API_URL ?? '').replace(/\/$/, '');
const HEALTHOS_API_KEY = process.env.HEALTHOS_API_KEY ?? '';
const MCP_API_KEY = process.env.MCP_API_KEY ?? '';

if (!HEALTHOS_API_URL) throw new Error('HEALTHOS_API_URL is required');
if (!HEALTHOS_API_KEY) throw new Error('HEALTHOS_API_KEY is required');
if (!MCP_API_KEY) throw new Error('MCP_API_KEY is required');

async function healthApi(
  path: string,
  params?: Record<string, string | number | undefined>
): Promise<unknown> {
  const url = new URL(`${HEALTHOS_API_URL}${path}`);

  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  }

  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
      'X-HealthOS-API-Key': HEALTHOS_API_KEY
    }
  });

  if (!response.ok) {
    throw new Error(`HealthOS API returned HTTP ${response.status}`);
  }

  return response.json();
}

function textResult(value: unknown) {
  return {
    content: [
      {
        type: 'text' as const,
        text: JSON.stringify(value)
      }
    ]
  };
}

function latestMetrics(records: unknown): unknown {
  if (!Array.isArray(records)) return records;

  const latestByType = new Map<
    string,
    {
      timestamp: number;
      index: number;
      record: unknown;
    }
  >();

  records.forEach((record, index) => {
    if (!record || typeof record !== 'object') return;

    const value = record as Record<string, unknown>;

    const type = String(
      value.metricType ?? value.metric_type ?? ''
    ).toUpperCase();

    const timestamp = Number(
      value.recordedAtMillis ?? value.recorded_at_millis
    );

    if (!type || !Number.isFinite(timestamp)) return;

    const previous = latestByType.get(type);

    if (
      !previous ||
      timestamp > previous.timestamp ||
      (timestamp === previous.timestamp && index > previous.index)
    ) {
      latestByType.set(type, {
        timestamp,
        index,
        record
      });
    }
  });

  return [...latestByType.values()]
    .sort((a, b) => a.index - b.index)
    .map(entry => entry.record);
}

function createHealthOsServer(): McpServer {
  const server = new McpServer(
    { name: 'healthos', version: '0.1.0' },
    { capabilities: { tools: {} } }
  );

  const rangeSchema = z.object({
    startMillis: z
      .number()
      .int()
      .optional()
      .describe('Inclusive Unix timestamp in milliseconds'),

    endMillis: z
      .number()
      .int()
      .optional()
      .describe('Inclusive Unix timestamp in milliseconds')
  });

  server.registerTool(
    'get_current_metrics',
    {
      title: 'Current health metrics',

      description:
        'Get the latest canonical HealthOS metric value for each metric type, optionally filtered by metric type.',

      annotations: { readOnlyHint: true },

      inputSchema: z.object({
        metricType: z
          .string()
          .optional()
          .describe('Optional metric type such as RESTING_HR or VO2_MAX')
      })
    },

    async ({ metricType }) =>
      textResult(
        latestMetrics(
          await healthApi('/health/metrics', {
            metric_type: metricType
          })
        )
      )
  );

  server.registerTool(
    'get_metric_history',
    {
      title: 'Metric history',

      description:
        'Get canonical HealthOS metric history, optionally filtered by metric type and timestamp range.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema.extend({
        metricType: z.string().optional()
      })
    },

    async ({ metricType, startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/metrics', {
          metric_type: metricType,
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_activity_history',
    {
      title: 'Activity history',

      description:
        'Get canonical activities from Garmin and Strava, with source provenance and route data when available.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema.extend({
        activityType: z.string().optional()
      })
    },

    async ({ activityType, startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/activities', {
          activity_type: activityType,
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_strength_history',
    {
      title: 'Strength history',

      description:
        'Get canonical strength workout summaries. Hevy remains the authoritative detailed strength source.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema
    },

    async ({ startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/workouts', {
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_nutrition_history',
    {
      title: 'Nutrition history',

      description:
        'Get canonical MyFitnessPal nutrition entries including calories and macro/nutrient fields.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema
    },

    async ({ startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/nutrition', {
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_weight_history',
    {
      title: 'Weight history',

      description:
        'Get canonical body measurements, including HealthifyMe weight history.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema
    },

    async ({ startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/body', {
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_lab_results',
    {
      title: 'Lab results',

      description:
        'Get manually entered canonical laboratory results and reference ranges.',

      annotations: { readOnlyHint: true },

      inputSchema: rangeSchema
    },

    async ({ startMillis, endMillis }) =>
      textResult(
        await healthApi('/health/labs', {
          start_millis: startMillis,
          end_millis: endMillis
        })
      )
  );

  server.registerTool(
    'get_user_profile',
    {
      title: 'User profile',

      description:
        'Get the canonical HealthOS profile currently synchronized from the Android app.',

      annotations: { readOnlyHint: true }
    },

    async () =>
      textResult(
        await healthApi('/health/profile')
      )
  );

  server.registerTool(
    'get_data_sources',
    {
      title: 'Data sources',

      description:
        'Describe the canonical provider mapping used by HealthOS.',

      annotations: { readOnlyHint: true }
    },

    async () =>
      textResult({
        GARMIN: [
          'VO2_MAX',
          'RESTING_HR',
          'HRV',
          'SLEEP',
          'STRESS',
          'STEPS',
          'recent activities'
        ],

        STRAVA: [
          'historical/bulk activities'
        ],

        HEVY: [
          'detailed strength workouts'
        ],

        HEALTHIFYME: [
          'weight'
        ],

        MYFITNESSPAL: [
          'nutrition/calories/macros'
        ],

        LABS: [
          'manual lab results'
        ],

        MANUAL: [
          'manual HealthOS entries'
        ]
      })
  );

  return server;
}

const mcpHandler = createMcpHandler(createHealthOsServer);

const nodeHandler = toNodeHandler(mcpHandler, {
  onerror: error => console.error(error)
});

createServer(async (req, res) => {
  if (req.url === '/health' && req.method === 'GET') {
    res.writeHead(200, {
      'Content-Type': 'application/json'
    });

    res.end(
      JSON.stringify({
        status: 'ok'
      })
    );

    return;
  }

  if (!req.url?.startsWith('/mcp')) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }

  const authorization = req.headers.authorization ?? '';

  if (authorization !== `Bearer ${MCP_API_KEY}`) {
    res.writeHead(401, {
      'WWW-Authenticate': 'Bearer'
    });

    res.end('Unauthorized');
    return;
  }

  await nodeHandler(req, res);
}).listen(PORT, '0.0.0.0', () => {
  console.log(
    `HealthOS MCP server listening on port ${PORT}`
  );
});
```
