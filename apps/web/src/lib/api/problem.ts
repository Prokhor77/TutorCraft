import { z } from 'zod';

/** RFC 9457 Problem Details as defined by the contract (API-03). */
export const problemFieldErrorSchema = z.object({
  field: z.string(),
  code: z.string(),
  message: z.string(),
});
export type ProblemFieldError = z.infer<typeof problemFieldErrorSchema>;

export const problemSchema = z.object({
  type: z.string().default('about:blank'),
  title: z.string(),
  status: z.number(),
  detail: z.string().optional(),
  code: z.string().default('unknown'),
  errors: z.array(problemFieldErrorSchema).optional(),
  traceId: z.string().optional(),
  args: z.record(z.unknown()).optional(),
});

/** Machine codes the UI reacts to. Server codes come from the contract; `client.*` are produced locally. */
export const PROBLEM_CODES = {
  network: 'client.network',
  invalidResponse: 'client.invalid_response',
  unknown: 'unknown',
  versionConflict: 'conflict.version',
  tenantRequired: 'auth.tenant_required',
  invalidCredentials: 'auth.invalid_credentials',
  accountSuspended: 'auth.account_suspended',
  tooManyAttempts: 'auth.too_many_attempts',
  refreshInvalid: 'auth.refresh_invalid',
  tokenInvalid: 'auth.token_invalid',
  providerDisabled: 'auth.provider_disabled',
  validationFailed: 'validation.failed',
  quizTimeExpired: 'quiz.time_expired',
  filesNotUploaded: 'files.not_uploaded',
} as const;

export const HTTP_STATUS = {
  noContent: 204,
  unauthorized: 401,
  forbidden: 403,
  notFound: 404,
  conflict: 409,
  preconditionFailed: 412,
  unprocessable: 422,
  tooManyRequests: 429,
  serverError: 500,
} as const;

export class ApiProblem extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  readonly detail?: string;
  readonly errors: ProblemFieldError[];
  readonly traceId?: string;
  readonly args: Record<string, unknown>;
  readonly retryAfterSec?: number;

  constructor(init: {
    status: number;
    code: string;
    title: string;
    detail?: string;
    errors?: ProblemFieldError[];
    traceId?: string;
    args?: Record<string, unknown>;
    retryAfterSec?: number;
  }) {
    super(init.detail ?? init.title);
    this.name = 'ApiProblem';
    this.status = init.status;
    this.code = init.code;
    this.title = init.title;
    this.detail = init.detail;
    this.errors = init.errors ?? [];
    this.traceId = init.traceId;
    this.args = init.args ?? {};
    this.retryAfterSec = init.retryAfterSec;
  }

  /** True for failures where retrying the same request later may succeed (offline queues, NFR-REL-05). */
  get isTransient(): boolean {
    return (
      this.code === PROBLEM_CODES.network ||
      this.status >= HTTP_STATUS.serverError ||
      this.status === HTTP_STATUS.tooManyRequests
    );
  }
}

export function isApiProblem(error: unknown): error is ApiProblem {
  return error instanceof ApiProblem;
}

export function hasProblemCode(error: unknown, code: string): boolean {
  return isApiProblem(error) && error.code === code;
}

function parseRetryAfter(response: Response): number | undefined {
  const header = response.headers.get('Retry-After');
  if (!header) return undefined;
  const seconds = Number(header);
  return Number.isFinite(seconds) ? seconds : undefined;
}

/** Converts any non-2xx response into ApiProblem, tolerating non-JSON bodies (e.g. proxy errors). */
export async function problemFromResponse(response: Response): Promise<ApiProblem> {
  const retryAfterSec = parseRetryAfter(response);
  const fallback = {
    status: response.status,
    code: PROBLEM_CODES.unknown,
    title: response.statusText || `HTTP ${response.status}`,
    retryAfterSec,
  };
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    return new ApiProblem(fallback);
  }
  const parsed = problemSchema.safeParse(body);
  if (!parsed.success) return new ApiProblem(fallback);
  return new ApiProblem({
    ...parsed.data,
    status: parsed.data.status || response.status,
    retryAfterSec,
  });
}

export function networkProblem(cause: unknown): ApiProblem {
  const detail = cause instanceof Error ? cause.message : undefined;
  return new ApiProblem({ status: 0, code: PROBLEM_CODES.network, title: 'Network error', detail });
}

export function invalidResponseProblem(path: string, issues: string): ApiProblem {
  return new ApiProblem({
    status: 0,
    code: PROBLEM_CODES.invalidResponse,
    title: 'Unexpected server response',
    detail: `${path}: ${issues}`,
  });
}

/** Maps Problem `errors[]` to form fields: returns { fieldName: message }. Nested paths keep dots. */
export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!isApiProblem(error)) return {};
  return Object.fromEntries(
    error.errors.map((fieldError) => [fieldError.field, fieldError.message]),
  );
}
