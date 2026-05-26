import { useQuery } from '@tanstack/react-query';
import type { PagedSessions, SessionStatus } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export type MySessionsQueryParams = {
  page: number;
  size: number;
  status?: SessionStatus;
};

export const mySessionsQueryKey = (params: MySessionsQueryParams) => ['my', 'sessions', params] as const;

export function useMySessionsQuery(params: MySessionsQueryParams) {
  return useQuery<PagedSessions>({
    queryKey: mySessionsQueryKey(params),
    staleTime: 20_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/my/sessions', {
        params: { query: params },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '我的数据加载失败。');
      }

      return data;
    },
  });
}
