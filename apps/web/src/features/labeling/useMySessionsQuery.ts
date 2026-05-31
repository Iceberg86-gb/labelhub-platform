import { useQuery } from '@tanstack/react-query';
import type { LabelerSessionWorkStatus, PagedSessions, SessionStatus } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export type MySessionsQueryParams = {
  page: number;
  size: number;
  status?: SessionStatus;
  workStatus?: LabelerSessionWorkStatus;
  enabled?: boolean;
};

export const mySessionsQueryKey = (params: MySessionsQueryParams) => ['my', 'sessions', params] as const;

export function useMySessionsQuery(params: MySessionsQueryParams) {
  const { enabled = true, ...queryParams } = params;

  return useQuery<PagedSessions>({
    queryKey: mySessionsQueryKey(queryParams),
    enabled,
    staleTime: 20_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/my/sessions', {
        params: { query: queryParams },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '我的数据加载失败。');
      }

      return data;
    },
  });
}
