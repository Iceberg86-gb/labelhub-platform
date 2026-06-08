import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

export type SubmitTaskDraftsResult = components['schemas']['SubmitTaskDraftsResult'];

export type SubmitTaskDraftsInput = {
  taskId: number;
  currentSessionId: number;
  answerPayload: AnswerPayload;
};

export async function submitTaskDrafts({
  answerPayload,
  currentSessionId,
  taskId,
}: SubmitTaskDraftsInput): Promise<SubmitTaskDraftsResult> {
  const { data, error } = await apiClient.POST('/tasks/{taskId}/submit-drafts', {
    params: { path: { taskId } },
    body: { currentSessionId, answerPayload },
  });

  if (error || !data) {
    throw new Error(error?.message ?? '批次提交失败。');
  }

  return data;
}

export function useSubmitTaskDraftsMutation() {
  const queryClient = useQueryClient();

  return useMutation<SubmitTaskDraftsResult, Error, SubmitTaskDraftsInput>({
    mutationFn: submitTaskDrafts,
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['sessions', variables.currentSessionId] });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
      await queryClient.invalidateQueries({ queryKey: ['owner', 'task', variables.taskId] });
      await queryClient.invalidateQueries({ queryKey: ['owner', 'task', variables.taskId, 'submissions'] });
    },
  });
}
