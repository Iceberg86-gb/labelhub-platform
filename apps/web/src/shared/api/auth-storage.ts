import type { components } from './generated/schema';

export type UserProfile = components['schemas']['LoginUserProfile'];

const ACCESS_TOKEN_KEY = 'labelhub.access_token';
const EXPIRES_AT_KEY = 'labelhub.expires_at';
const USER_KEY = 'labelhub.user';
const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';
export const SESSION_CHANGED_EVENT = 'labelhub:session-changed';
const EXPIRY_BUFFER_MS = 30_000;
// Refresh tokens are held only in the server-set HttpOnly cookie and are never stored in localStorage.

function notifySessionChanged(): void {
  window.dispatchEvent(new CustomEvent(SESSION_CHANGED_EVENT));
}

function readItem(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function saveSession(accessToken: string, expiresAt: string, user: UserProfile): void {
  try {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    window.localStorage.setItem(EXPIRES_AT_KEY, expiresAt);
    window.localStorage.setItem(USER_KEY, JSON.stringify(user));
    notifySessionChanged();
  } catch (error) {
    console.error('Failed to save LabelHub session.', error);
    throw new Error('无法保存登录状态，请关闭隐私浏览模式或清理浏览器存储后重试。');
  }
}

export function clearSession(): void {
  revokeRefreshCookie();
  clearStoredSession();
}

function clearStoredSession(): void {
  try {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
    window.localStorage.removeItem(EXPIRES_AT_KEY);
    window.localStorage.removeItem(USER_KEY);
    notifySessionChanged();
  } catch {
    // Clearing session is best effort; auth middleware will treat missing token as logged out.
  }
}

function revokeRefreshCookie(): void {
  void fetch(apiUrl('/auth/logout'), {
    method: 'POST',
    credentials: 'include',
    keepalive: true,
  }).catch(() => {
    // Server-side logout is best effort; local session state is still cleared.
  });
}

function apiUrl(path: string): string {
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
  return `${normalizedBase}${path}`;
}

export function isSessionExpired(now = Date.now()): boolean {
  const expiresAt = readItem(EXPIRES_AT_KEY);
  if (!expiresAt) {
    return true;
  }

  const expiresAtMs = Date.parse(expiresAt);
  if (Number.isNaN(expiresAtMs)) {
    return true;
  }

  return expiresAtMs - EXPIRY_BUFFER_MS <= now;
}

export function getAccessToken(): string | null {
  if (isSessionExpired()) {
    clearStoredSession();
    return null;
  }

  return readItem(ACCESS_TOKEN_KEY);
}

export function getUser(): UserProfile | null {
  const raw = readItem(USER_KEY);
  if (!raw || isSessionExpired()) {
    return null;
  }

  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    clearSession();
    return null;
  }
}
