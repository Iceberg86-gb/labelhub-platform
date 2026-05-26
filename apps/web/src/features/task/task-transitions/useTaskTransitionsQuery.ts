import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';

export type TaskTransition = components['schemas']['TaskTransition'];

export const taskTransitionsQueryKey = (taskId: number) => ['tasks', taskId, 'transitions'] as const;

export function useTaskTransitionsQuery(taskId: number) {
  return useQuery<TaskTransition[]>({
    queryKey: taskTransitionsQueryKey(taskId),
    staleTime: 15_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/transitions', {
        params: {
          path: { taskId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '状态迁移记录加载失败。');
      }

      return data;
    },
  });
}
