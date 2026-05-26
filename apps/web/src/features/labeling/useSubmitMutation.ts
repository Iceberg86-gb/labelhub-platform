import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import type { Submission } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';

export type SubmitSessionInput = {
  sessionId: number;
  answerPayload: AnswerPayload;
};

export function useSubmitMutation() {
  const queryClient = useQueryClient();

  return useMutation<Submission, Error, SubmitSessionInput>({
    mutationFn: async ({ sessionId, answerPayload }) => {
      const { data, error } = await apiClient.POST('/sessions/{sessionId}/submit', {
        params: { path: { sessionId } },
        body: { answerPayload },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '提交失败。');
      }

      return data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['sessions', variables.sessionId] });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
    },
  });
}
