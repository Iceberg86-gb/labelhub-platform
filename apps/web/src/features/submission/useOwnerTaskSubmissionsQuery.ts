import { useQuery } from '@tanstack/react-query';
import type { PagedOwnerSubmissions } from '../../entities/submission/ownerTypes';
import { apiClient } from '../../shared/api/client';

type OwnerTaskSubmissionsOptions = {
  page?: number;
  size?: number;
  enabled?: boolean;
};

export const ownerTaskSubmissionsQueryKey = (taskId: number, page: number, size: number) =>
  ['tasks', taskId, 'submissions', page, size] as const;

export function useOwnerTaskSubmissionsQuery(taskId: number, options?: OwnerTaskSubmissionsOptions) {
  const page = options?.page ?? 1;
  const size = options?.size ?? 20;

  return useQuery<PagedOwnerSubmissions>({
    queryKey: ownerTaskSubmissionsQueryKey(taskId, page, size),
    enabled: (options?.enabled ?? true) && taskId > 0,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/submissions', {
        params: { path: { taskId }, query: { page, size } },
      });
      if (error || !data) {
        throw new Error(error?.message ?? '提交记录加载失败。');
      }
      return data;
    },
  });
}
