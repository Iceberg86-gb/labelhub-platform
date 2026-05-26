import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';

export type TaskStatus = components['schemas']['TaskStatus'];
export type Task = components['schemas']['Task'];
export type PagedTasks = components['schemas']['PagedTasks'];

export type TaskListParams = {
  page: number;
  size: number;
  status?: TaskStatus;
};

export const taskListQueryKey = (params?: Partial<TaskListParams>) =>
  params ? ['tasks', params] : ['tasks'];

export function useTasksQuery(params: TaskListParams) {
  return useQuery<PagedTasks>({
    queryKey: taskListQueryKey(params),
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks', {
        params: {
          query: {
            page: params.page,
            size: params.size,
            status: params.status,
          },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '任务列表加载失败。');
      }

      return data;
    },
  });
}
