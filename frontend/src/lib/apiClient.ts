// Thin fetch wrapper around the backend REST API.
//
// The base URL defaults to the relative "/api" path, which the Vite dev server
// proxies to the Spring Boot backend (see vite.config.ts) and which works in
// production when the SPA is served from the same origin as the API. Override
// with VITE_API_BASE_URL when the API lives elsewhere.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "/api").replace(/\/$/, "");

/** RFC-7807 ProblemDetail payload returned by the backend on errors. */
interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: string[];
  /** App-specific machine code, e.g. "EMAIL_NOT_VERIFIED". */
  code?: string;
}

/** Error thrown for any non-2xx response, carrying a user-presentable message. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: string[];
  readonly code: string | null;

  constructor(status: number, message: string, fieldErrors: string[] = [], code: string | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.code = code;
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  token?: string | null;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, token } = options;

  const headers: Record<string, string> = { "Accept-Language": "en" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Couldn't reach the server. Check your connection and try again.");
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    // Body was empty or not JSON — fall back to a status-based message below.
  }

  const fieldErrors = Array.isArray(problem.errors) ? problem.errors : [];
  const message =
    problem.detail ??
    problem.title ??
    fallbackMessage(response.status);
  return new ApiError(response.status, message, fieldErrors, problem.code ?? null);
}

function fallbackMessage(status: number): string {
  if (status === 429) {
    return "Too many attempts. Please wait a moment and try again.";
  }
  if (status >= 500) {
    return "Internal server error. Please try again later.";
  }
  return "The request failed. Please try again.";
}
