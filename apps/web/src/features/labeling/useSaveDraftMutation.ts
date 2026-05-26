import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import type { Draft } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';
import { latestDraftQueryKey } from './useLatestDraftQuery';

export type SaveDraftInput = {
  sessionId: number;
  payload: AnswerPayload;
};

export function useSaveDraftMutation() {
  const queryClient = useQueryClient();

  return useMutation<Draft, Error, SaveDraftInput>({
    mutationFn: async ({ sessionId, payload }) => {
      const { data, error } = await apiClient.PUT('/sessions/{sessionId}/draft', {
        params: { path: { sessionId } },
        body: { payload },
      });

      if (error || !data) {
        throw new Error(error?.message ?? '草稿保存失败。');
      }

      return data;
    },
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: latestDraftQueryKey(variables.sessionId) });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
    },
  });
}
