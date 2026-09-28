import { http } from '../http';
import { pageSchema } from '../schemas/common';
import {
  activityEntrySchema,
  activitySummarySchema,
  activityTrailSchema,
  type ActivityKind,
  type ActivityOutcome,
} from '../schemas/activity';

export type ActivityQuery = {
  actor?: string;
  kind?: ActivityKind;
  outcome?: ActivityOutcome;
  status?: number;
  route?: string;
  requestId?: string;
  sessionId?: string;
  from?: string;
  to?: string;
  includeAnonymous?: boolean;
  cursor?: string | null;
};

/** Browser event for the activity log (POST /activity/events). Limits mirror the server validation. */
export type ClientActivityEvent = {
  kind: 'page_view' | 'client_error';
  page: string;
  name?: string;
  message?: string;
  stack?: string;
  requestId?: string;
  occurredAt: string;
};

export const activityApi = {
  log: (query: ActivityQuery) =>
    http.request('/activity-log', { query, schema: pageSchema(activityEntrySchema) }),
  trail: (entryId: string) =>
    http.request(`/activity-log/${encodeURIComponent(entryId)}/trail`, {
      schema: activityTrailSchema,
    }),
  summary: (query: { includeAnonymous?: boolean }) =>
    http.request('/activity-log/summary', { query, schema: activitySummarySchema }),
  sendEvents: (events: ClientActivityEvent[]) =>
    http.request('/activity/events', { method: 'POST', body: { events } }),
};
