import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AnswerPayload } from '../../entities/submission/answerPayload';
import type { Submission } from '../../entities/submission/submissionTypes';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';

type ApiFieldError = components['schemas']['ApiFieldError'];

export type SubmitSessionInput = {
  sessionId: number;
  answerPayload: AnswerPayload;
};

export class SubmitValidationError extends Error {
  constructor(
    public readonly fieldErrors: ApiFieldError[],
    message = '提交失败,请检查字段错误',
  ) {
    super(message);
  }
}

export async function submitSession({ sessionId, answerPayload }: SubmitSessionInput): Promise<Submission> {
  const { data, error, response } = await apiClient.POST('/sessions/{sessionId}/submit', {
    params: { path: { sessionId } },
    body: { answerPayload },
  });

  if (response.status === 422 && error?.fieldErrors?.length) {
    throw new SubmitValidationError(error.fieldErrors, error.message ?? '提交失败,请检查字段错误');
  }

  if (error || !data) {
    throw new Error(error?.message ?? '提交失败。');
  }

  return data;
}

export function useSubmitMutation() {
  const queryClient = useQueryClient();

  return useMutation<Submission, Error, SubmitSessionInput>({
    mutationFn: submitSession,
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: ['sessions', variables.sessionId] });
      await queryClient.invalidateQueries({ queryKey: ['my', 'sessions'] });
    },
  });
}
