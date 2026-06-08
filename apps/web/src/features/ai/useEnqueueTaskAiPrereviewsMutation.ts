import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { taskAiPrereviewSummaryQueryKey } from './useTaskAiPrereviewSummaryQuery';

export type TaskAiPrereviewEnqueueResult = components['schemas']['TaskAiPrereviewEnqueueResult'];

export class EnqueueTaskAiPrereviewsFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

export function useEnqueueTaskAiPrereviewsMutation(taskId: number) {
  const queryClient = useQueryClient();

  return useMutation<TaskAiPrereviewEnqueueResult, EnqueueTaskAiPrereviewsFailure>({
    mutationFn: async () => {
      const { data, error, response } = await apiClient.POST('/tasks/{taskId}/ai-prereview/enqueue', {
        params: { path: { taskId } },
      });

      if (error || !data) {
        const body = error as { code?: string; message?: string } | undefined;
        throw new EnqueueTaskAiPrereviewsFailure(
          response.status,
          body?.code,
          mapEnqueueErrorMessage(response.status, body?.message),
        );
      }

      return data;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: taskAiPrereviewSummaryQueryKey(taskId) }),
        queryClient.invalidateQueries({ queryKey: ['tasks', taskId, 'submissions'] }),
      ]);
    },
  });
}

function mapEnqueueErrorMessage(status: number, message: string | undefined): string {
  if (status === 404) return '任务不存在或无权访问';
  if (status === 403) return '没有发起 AI 预审的权限';
  return message ?? 'AI 预审发起失败,请稍后重试';
}
