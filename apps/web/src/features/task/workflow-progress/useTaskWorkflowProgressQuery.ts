import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../../shared/api/client';
import type { components } from '../../../shared/api/generated/schema';

export type TaskWorkflowProgress = components['schemas']['TaskWorkflowProgress'];

export const taskWorkflowProgressQueryKey = (taskId: number) => ['tasks', taskId, 'workflow-progress'] as const;

export function useTaskWorkflowProgressQuery(taskId: number) {
  return useQuery<TaskWorkflowProgress>({
    queryKey: taskWorkflowProgressQueryKey(taskId),
    enabled: taskId > 0,
    staleTime: 15_000,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/workflow-progress', {
        params: {
          path: { taskId },
        },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '全过程进度加载失败。');
      }

      return data;
    },
  });
}
