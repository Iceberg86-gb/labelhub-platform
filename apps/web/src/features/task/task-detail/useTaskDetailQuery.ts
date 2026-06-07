import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { Task } from '../list-tasks/useTasksQuery';

export const taskDetailQueryKey = (taskId: number) => ['tasks', taskId] as const;

export function useTaskDetailQuery(taskId: number, options: { enabled?: boolean } = {}) {
  return useQuery<Task>({
    queryKey: taskDetailQueryKey(taskId),
    enabled: options.enabled ?? true,
    staleTime: 30_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}', {
        params: {
          path: { taskId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '任务详情加载失败。');
      }

      return data;
    },
  });
}
