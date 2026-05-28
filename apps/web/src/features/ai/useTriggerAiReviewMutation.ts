import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AiReviewResult } from '../../entities/ai/aiTypes';
import { apiClient } from '../../shared/api/client';
import { aiProvenanceQueryKey } from './useSubmissionAiProvenanceQuery';

export class TriggerAiReviewFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

type TriggerAiReviewVariables = {
  submissionId: number;
  promptVersionId: number;
};

export async function triggerAiReview({ submissionId, promptVersionId }: TriggerAiReviewVariables): Promise<AiReviewResult> {
  const { data, error, response } = await apiClient.POST('/submissions/{submissionId}/ai-review', {
    params: { path: { submissionId } },
    body: { promptVersionId },
  });

  if (error || !data) {
    const body = error as { code?: string; message?: string } | undefined;
    throw new TriggerAiReviewFailure(
      response.status,
      body?.code,
      mapTriggerErrorMessage(response.status, body?.code, body?.message),
    );
  }

  return data;
}

export function useTriggerAiReviewMutation() {
  const queryClient = useQueryClient();

  return useMutation<AiReviewResult, TriggerAiReviewFailure, TriggerAiReviewVariables>({
    mutationFn: triggerAiReview,
    onSuccess: async (_data, variables) => {
      await queryClient.invalidateQueries({ queryKey: aiProvenanceQueryKey(variables.submissionId) });
    },
  });
}

function mapTriggerErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (status === 502 && code === 'AI_PROVIDER_FAILURE') return 'AI 服务暂时不可用,请稍后重试';
  if (status === 409 && code === 'AI_PROVIDER_INPUT_HASH_MISMATCH') return 'Submission 内容已变更,无法复用历史 AI 结果';
  if (status === 404) return 'Submission 不存在或无权访问';
  if (status === 403) return '没有触发权限';
  return message ?? 'AI 检查失败,请稍后重试';
}
