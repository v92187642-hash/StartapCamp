import createClient from "openapi-fetch";
import type { components, paths } from "./schema";

// schema.d.ts is generated from api/openapi.yaml by `npm run generate`.
// Never edit it and never write request code by hand: change the contract.

export type Schemas = components["schemas"];

const ACCESS_KEY = "otmetka.accessToken";
const REFRESH_KEY = "otmetka.refreshToken";

/** Fired on window when the session ended and the user must log in again. */
export const LOGGED_OUT_EVENT = "otmetka:logged-out";

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  save(tokens: Schemas["Tokens"]) {
    localStorage.setItem(ACCESS_KEY, tokens.accessToken);
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

const baseUrl = new URL("/api", window.location.origin).href;

/** One refresh at a time: several requests that fail together wait for the same one. */
let refreshing: Promise<boolean> | null = null;

function refreshTokens(): Promise<boolean> {
  refreshing ??= (async () => {
    const refreshToken = tokenStore.refresh();
    if (!refreshToken) return false;
    try {
      const response = await fetch(`${baseUrl}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) return false;
      tokenStore.save(await response.json());
      return true;
    } catch {
      return false;
    }
  })().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

/**
 * Adds the access token to every request. When the server answers 401 the
 * access token has expired: get a new one with the refresh token and repeat
 * the request once. If that fails too, the session is over.
 */
/** Login and refresh must not trigger another refresh. logout-all still should. */
function skipRefreshOn401(pathname: string): boolean {
  const path = pathname.replace(/\/$/, "");
  return (
    path.endsWith("/auth/login") ||
    path.endsWith("/auth/register") ||
    path.endsWith("/auth/refresh") ||
    path.endsWith("/auth/logout")
  );
}

async function authorizedFetch(request: Request): Promise<Response> {
  const retry = request.clone();

  const token = tokenStore.access();
  if (token) request.headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(request);
  if (response.status !== 401 || skipRefreshOn401(new URL(request.url).pathname)) return response;

  if (await refreshTokens()) {
    retry.headers.set("Authorization", `Bearer ${tokenStore.access()}`);
    return fetch(retry);
  }
  tokenStore.clear();
  window.dispatchEvent(new Event(LOGGED_OUT_EVENT));
  return response;
}

export const api = createClient<paths>({ baseUrl, fetch: authorizedFetch });

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/**
 * Turns the client's `{ data, error }` into a value or a thrown ApiError whose
 * message is Russian text ready to show:
 *
 *   const plans = await unwrap(api.GET("/plans"));
 */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  let result;
  try {
    result = await call;
  } catch {
    throw new ApiError("Нет связи с сервером. Проверьте интернет и попробуйте ещё раз", 0);
  }
  if (!result.response.ok) {
    const detail = (result.error as Schemas["Problem"] | undefined)?.detail;
    throw new ApiError(detail ?? "Что-то пошло не так. Попробуйте ещё раз", result.response.status);
  }
  return result.data as T;
}
