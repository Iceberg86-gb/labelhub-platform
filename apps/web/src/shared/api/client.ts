import createClient from 'openapi-fetch';
import type { paths } from './generated/schema';
import { clearSession, getAccessToken, saveSession } from './auth-storage';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';
export const UNAUTHORIZED_EVENT = 'labelhub:unauthorized';

let refreshPromise: Promise<boolean> | null = null;

export const apiClient = createClient<paths>({
  baseUrl,
  fetch: authFetch,
});

async function authFetch(input: Request): Promise<Response> {
  const request = withCredentials(withAccessToken(input));
  const retryBase = request.clone();
  const response = await fetch(request);

  if (response.status !== 401 || isAuthSessionRequest(request)) {
    if (response.status === 401) {
      handleUnauthorized();
    }
    return response;
  }

  const refreshed = await refreshAccessToken();
  if (!refreshed) {
    handleUnauthorized();
    return response;
  }

  const retryResponse = await fetch(withCredentials(withAccessToken(retryBase)));
  if (retryResponse.status === 401) {
    handleUnauthorized();
  }
  return retryResponse;
}

function withAccessToken(request: Request): Request {
  const accessToken = getAccessToken();
  if (!accessToken) {
    return request;
  }

  const headers = new Headers(request.headers);
  headers.set('Authorization', `Bearer ${accessToken}`);
  return new Request(request, { headers });
}

function withCredentials(request: Request): Request {
  return new Request(request, { credentials: 'include' });
}

function isAuthSessionRequest(request: Request): boolean {
  const pathname = new URL(request.url, window.location.origin).pathname;
  return pathname.endsWith('/auth/login')
    || pathname.endsWith('/auth/register')
    || pathname.endsWith('/auth/refresh')
    || pathname.endsWith('/auth/logout');
}

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performRefresh(): Promise<boolean> {
  const response = await fetch(apiUrl('/auth/refresh'), {
    method: 'POST',
    credentials: 'include',
  });
  if (!response.ok) {
    return false;
  }

  const data = await response.json() as paths['/auth/refresh']['post']['responses']['200']['content']['application/json'];
  saveSession(data.accessToken, data.expiresAt, data.user);
  return true;
}

function apiUrl(path: string): string {
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
  return `${normalizedBase}${path}`;
}

function handleUnauthorized(): void {
  clearSession();
  if (!window.location.pathname.startsWith('/login')) {
    window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
  }
}
