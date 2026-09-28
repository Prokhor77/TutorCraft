import type { z } from 'zod';
import {
  ApiProblem,
  HTTP_STATUS,
  invalidResponseProblem,
  networkProblem,
  problemFromResponse,
} from './problem';
import { authResponseSchema, type AuthResponse } from './schemas/auth';

export const API_BASE_PATH = '/api/v1';
export const REFRESH_PATH = '/auth/refresh';

export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | QueryValue[]>;

export type RequestOptions<S extends z.ZodTypeAny | undefined = undefined> = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: QueryParams;
  body?: unknown;
  schema?: S;
  /** API-05: required by contract for submit / finish / orders. */
  idempotencyKey?: string;
  /** API-06: optimistic locking version. */
  ifMatch?: number;
  /** Set false for public endpoints (no bearer, no refresh retry). */
  auth?: boolean;
  signal?: AbortSignal;
};

export type RefreshOutcome = 'refreshed' | 'expired' | 'network';

export type ResponseWithMeta<T> = { data: T; headers: Headers };

/** Session storage abstraction: implemented by the in-memory auth store (never localStorage). */
export interface SessionAccess {
  getAccessToken(): string | null;
  setSession(auth: AuthResponse): void;
  clearSession(): void;
}

export type ApiClientConfig = {
  baseUrl?: string;
  session: SessionAccess;
  fetchImpl?: typeof fetch;
  getLocale?: () => string | undefined;
  /** School chosen by the platform administrator; sent as `X-Tenant-Id` (ignored by the API for everyone else). */
  getTenantOverride?: () => string | undefined;
  onSessionExpired?: () => void;
};

type Parsed<S> = S extends z.ZodTypeAny ? z.output<S> : void;

export function buildQueryString(query?: QueryParams): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value];
    for (const single of values) {
      if (single === undefined || single === null || single === '') continue;
      params.append(key, String(single));
    }
  }
  const serialized = params.toString();
  return serialized ? `?${serialized}` : '';
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly session: SessionAccess;
  private readonly fetchImpl: typeof fetch;
  private readonly getLocale?: () => string | undefined;
  private readonly getTenantOverride?: () => string | undefined;
  private readonly onSessionExpired?: () => void;
  private refreshInFlight: Promise<RefreshOutcome> | null = null;

  constructor(config: ApiClientConfig) {
    this.baseUrl = config.baseUrl ?? API_BASE_PATH;
    this.session = config.session;
    this.fetchImpl = config.fetchImpl ?? ((...args) => fetch(...args));
    this.getLocale = config.getLocale;
    this.getTenantOverride = config.getTenantOverride;
    this.onSessionExpired = config.onSessionExpired;
  }

  async request<S extends z.ZodTypeAny | undefined = undefined>(
    path: string,
    options: RequestOptions<S> = {},
  ): Promise<Parsed<S>> {
    const { data } = await this.requestWithMeta(path, options);
    return data;
  }

  async requestWithMeta<S extends z.ZodTypeAny | undefined = undefined>(
    path: string,
    options: RequestOptions<S> = {},
  ): Promise<ResponseWithMeta<Parsed<S>>> {
    const response = await this.send(path, options);
    const data = (await this.parseBody(path, response, options.schema)) as Parsed<S>;
    return { data, headers: response.headers };
  }

  /** Raw response for binary downloads (export CSV/XLSX). */
  async requestBlob(
    path: string,
    options: RequestOptions = {},
  ): Promise<{ blob: Blob; fileName: string | null }> {
    const response = await this.send(path, options);
    return {
      blob: await response.blob(),
      fileName: fileNameFromDisposition(response.headers.get('Content-Disposition')),
    };
  }

  /**
   * Single-flight silent refresh (ADR-003): concurrent 401s share one POST /auth/refresh.
   * `expired` clears the session; `network` keeps it (the user may simply be offline).
   */
  refreshSession(): Promise<RefreshOutcome> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.performRefresh().finally(() => {
        this.refreshInFlight = null;
      });
    }
    return this.refreshInFlight;
  }

  private async performRefresh(): Promise<RefreshOutcome> {
    let response: Response;
    try {
      response = await this.fetchImpl(`${this.baseUrl}${REFRESH_PATH}`, {
        method: 'POST',
        credentials: 'include',
        headers: this.baseHeaders(),
      });
    } catch {
      return 'network';
    }
    if (response.status >= HTTP_STATUS.serverError) return 'network';
    const parsed = response.ok
      ? authResponseSchema.safeParse(await response.json().catch(() => null))
      : null;
    if (!parsed?.success) {
      this.session.clearSession();
      return 'expired';
    }
    this.session.setSession(parsed.data);
    return 'refreshed';
  }

  private async send(
    path: string,
    options: RequestOptions<z.ZodTypeAny | undefined>,
  ): Promise<Response> {
    const useAuth = options.auth !== false;
    const response = await this.fetchOnce(path, options, useAuth);
    if (response.status !== HTTP_STATUS.unauthorized || !useAuth) return this.ensureOk(response);

    const outcome = await this.refreshSession();
    if (outcome === 'network') throw networkProblem(new Error('session refresh failed'));
    if (outcome === 'expired') {
      this.onSessionExpired?.();
      return this.ensureOk(response);
    }
    return this.ensureOk(await this.fetchOnce(path, options, useAuth));
  }

  private async ensureOk(response: Response): Promise<Response> {
    if (response.ok) return response;
    throw await problemFromResponse(response);
  }

  private async fetchOnce(
    path: string,
    options: RequestOptions<z.ZodTypeAny | undefined>,
    useAuth: boolean,
  ): Promise<Response> {
    const headers = this.baseHeaders();
    const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
    if (options.body !== undefined && !isFormData) headers.set('Content-Type', 'application/json');
    if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey);
    if (options.ifMatch !== undefined) headers.set('If-Match', `"${options.ifMatch}"`);
    const token = useAuth ? this.session.getAccessToken() : null;
    if (token) headers.set('Authorization', `Bearer ${token}`);

    const body =
      options.body === undefined
        ? undefined
        : isFormData
          ? (options.body as FormData)
          : JSON.stringify(options.body);
    try {
      return await this.fetchImpl(`${this.baseUrl}${path}${buildQueryString(options.query)}`, {
        method: options.method ?? 'GET',
        headers,
        body,
        credentials: 'include',
        signal: options.signal,
      });
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
      throw networkProblem(cause);
    }
  }

  private baseHeaders(): Headers {
    const headers = new Headers({ Accept: 'application/json, application/problem+json' });
    const locale = this.getLocale?.();
    if (locale) headers.set('Accept-Language', locale);
    const tenantId = this.getTenantOverride?.();
    if (tenantId) headers.set('X-Tenant-Id', tenantId);
    return headers;
  }

  private async parseBody(
    path: string,
    response: Response,
    schema?: z.ZodTypeAny,
  ): Promise<unknown> {
    if (response.status === HTTP_STATUS.noContent || !schema) return undefined;
    const json: unknown = await response.json().catch(() => {
      throw invalidResponseProblem(path, 'body is not JSON');
    });
    const parsed = schema.safeParse(json);
    if (parsed.success) return parsed.data;
    const issues = parsed.error.issues
      .slice(0, 3)
      .map((issue) => `${issue.path.join('.')}: ${issue.message}`)
      .join('; ');
    console.error(`[api] contract mismatch at ${path}: ${issues}`);
    throw invalidResponseProblem(path, issues);
  }
}

function fileNameFromDisposition(header: string | null): string | null {
  if (!header) return null;
  const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8Match?.[1]) return decodeURIComponent(utf8Match[1]);
  const plainMatch = /filename="?([^";]+)"?/i.exec(header);
  return plainMatch?.[1] ?? null;
}

export { ApiProblem };
