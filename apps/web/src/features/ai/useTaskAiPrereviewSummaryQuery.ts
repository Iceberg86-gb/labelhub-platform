import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type TaskAiPrereviewSummary = components['schemas']['TaskAiPrereviewSummary'];

export const taskAiPrereviewSummaryQueryKey = (taskId: number) => ['tasks', taskId, 'ai-prereview', 'summary'] as const;

export function useTaskAiPrereviewSummaryQuery(taskId: number) {
  return useQuery<TaskAiPrereviewSummary>({
    queryKey: taskAiPrereviewSummaryQueryKey(taskId),
    enabled: taskId > 0,
    staleTime: 5_000,
    refetchInterval: (query) => Number(query.state.data?.processingCount ?? 0) > 0 ? 5_000 : false,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/tasks/{taskId}/ai-prereview/summary', {
        params: { path: { taskId } },
      });

      if (error || !data) {
        throw new Error(error?.message ?? 'AI 预审进度加载失败。');
      }

      return data;
    },
  });
}
