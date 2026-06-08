import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { components } from '../../shared/api/generated/schema';
import { aiProvenanceQueryKey } from './useSubmissionAiProvenanceQuery';
import { taskAiPrereviewSummaryQueryKey } from './useTaskAiPrereviewSummaryQuery';

export type SubmissionAiPrereviewEnqueueResult = components['schemas']['TaskAiPrereviewEnqueueResult'];

export class EnqueueSubmissionAiPrereviewFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

type EnqueueSubmissionAiPrereviewVariables = {
  submissionId: number;
};

export async function enqueueSubmissionAiPrereview({
  submissionId,
}: EnqueueSubmissionAiPrereviewVariables): Promise<SubmissionAiPrereviewEnqueueResult> {
  const { data, error, response } = await apiClient.POST('/submissions/{submissionId}/ai-prereview/enqueue', {
    params: { path: { submissionId } },
  });

  if (error || !data) {
    const body = error as { code?: string; message?: string } | undefined;
    throw new EnqueueSubmissionAiPrereviewFailure(
      response.status,
      body?.code,
      mapEnqueueErrorMessage(response.status, body?.message),
    );
  }

  return data;
}

export function useEnqueueSubmissionAiPrereviewMutation(taskId: number) {
  const queryClient = useQueryClient();

  return useMutation<SubmissionAiPrereviewEnqueueResult, EnqueueSubmissionAiPrereviewFailure, EnqueueSubmissionAiPrereviewVariables>({
    mutationFn: enqueueSubmissionAiPrereview,
    onSuccess: async (_data, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: aiProvenanceQueryKey(variables.submissionId) }),
        queryClient.invalidateQueries({ queryKey: taskAiPrereviewSummaryQueryKey(taskId) }),
        queryClient.invalidateQueries({ queryKey: ['tasks', taskId, 'submissions'] }),
      ]);
    },
  });
}

function mapEnqueueErrorMessage(status: number, message: string | undefined): string {
  if (status === 404) return 'Submission 不存在或无权访问';
  if (status === 403) return '没有发起 AI 预审的权限';
  return message ?? 'AI 预审发起失败,请稍后重试';
}
