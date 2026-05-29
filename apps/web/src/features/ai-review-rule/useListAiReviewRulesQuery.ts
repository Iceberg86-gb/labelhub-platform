import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { mapAiReviewRuleErrorMessage } from './useSaveAiReviewRuleMutation';

export class AiReviewRuleQueryError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

export const aiReviewRulesQueryKey = (taskId: number) => ['ai-review-rules', taskId] as const;

export async function fetchAiReviewRules(taskId: number): Promise<AiReviewRule[]> {
  const { data, error, response } = await apiClient.GET('/ai-review/rules', {
    params: { query: { taskId } },
  });

  if (error || !data) {
    const body = error as { code?: string; message?: string } | undefined;
    const status = (response as { status?: number } | undefined)?.status ?? 0;
    throw new AiReviewRuleQueryError(
      status,
      body?.code,
      mapAiReviewRuleErrorMessage(status, body?.message, 'AI 审核规则列表加载失败'),
    );
  }

  return data;
}

export function useListAiReviewRulesQuery(taskId: number) {
  return useQuery<AiReviewRule[], AiReviewRuleQueryError>({
    queryKey: aiReviewRulesQueryKey(taskId),
    queryFn: () => fetchAiReviewRules(taskId),
    enabled: Number.isFinite(taskId) && taskId > 0,
    staleTime: 30_000,
  });
}
