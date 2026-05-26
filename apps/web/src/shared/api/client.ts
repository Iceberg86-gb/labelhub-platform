import createClient from 'openapi-fetch';
import type { paths } from './generated/schema';
import { clearSession, getAccessToken } from './auth-storage';

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';
export const UNAUTHORIZED_EVENT = 'labelhub:unauthorized';

export const apiClient = createClient<paths>({
  baseUrl,
});

apiClient.use({
  onRequest({ request }) {
    const accessToken = getAccessToken();
    if (!accessToken) {
      return request;
    }

    const headers = new Headers(request.headers);
    headers.set('Authorization', `Bearer ${accessToken}`);

    return new Request(request, { headers });
  },
  onResponse({ response }) {
    if (response.status === 401) {
      clearSession();
      if (!window.location.pathname.startsWith('/login')) {
        window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
      }
    }

    return response;
  },
});
