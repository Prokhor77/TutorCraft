import { z } from 'zod';

/** WebSocket messages from notifier (docs/events/README.md). Unknown types are ignored. */
export const realtimeMessageSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('notification'),
    data: z.object({
      id: z.string(),
      title: z.string(),
      body: z.string(),
      link: z.string().nullable().optional(),
      category: z.string(),
    }),
  }),
  z.object({ type: z.literal('counter'), data: z.object({ name: z.string(), value: z.number() }) }),
]);
export type RealtimeMessage = z.infer<typeof realtimeMessageSchema>;

export function parseRealtimeMessage(raw: string): RealtimeMessage | null {
  try {
    const parsed = realtimeMessageSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    console.warn('[realtime] non-JSON message ignored');
    return null;
  }
}

export const RECONNECT_BASE_MS = 1000;
export const RECONNECT_MAX_MS = 30_000;
/** Close codes the notifier may use for an invalid/expired token → refresh before reconnecting. */
export const AUTH_CLOSE_CODES = new Set([1008, 4001, 4401]);

export function reconnectDelay(attempt: number): number {
  const exponential = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** attempt);
  const jitter = Math.random() * RECONNECT_BASE_MS;
  return exponential + jitter;
}
