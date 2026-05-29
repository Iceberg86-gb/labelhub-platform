import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { AiReviewRuleMutationFailure, mapAiReviewRuleErrorMessage } from './useSaveAiReviewRuleMutation';

export type PublishAiReviewRuleVariables = {
  ruleId: number;
};

export async function publishAiReviewRule({ ruleId }: PublishAiReviewRuleVariables): Promise<AiReviewRule> {
  const { data, error, response } = await apiClient.POST('/ai-review/rules/{ruleId}/publish', {
    params: { path: { ruleId } },
  });

  if (error || !data) {
    const body = error as { code?: string; message?: string } | undefined;
    throw new AiReviewRuleMutationFailure(
      response.status,
      body?.code,
      mapAiReviewRuleErrorMessage(response.status, body?.message, 'AI 审核规则发布失败'),
    );
  }

  return data;
}

export function usePublishAiReviewRuleMutation() {
  return useMutation<AiReviewRule, AiReviewRuleMutationFailure, PublishAiReviewRuleVariables>({
    mutationFn: publishAiReviewRule,
  });
}

