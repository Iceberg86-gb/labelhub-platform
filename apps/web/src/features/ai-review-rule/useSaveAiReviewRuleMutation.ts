import { useMutation } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type { AiReviewRule, AiReviewRuleRequest } from './aiReviewRuleTypes';

export class AiReviewRuleMutationFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

export async function saveAiReviewRule(request: AiReviewRuleRequest): Promise<AiReviewRule> {
  const { data, error, response } = await apiClient.POST('/ai-review/rules', {
    body: request,
  });

  if (error || !data) {
    const body = error as { code?: string; message?: string } | undefined;
    const status = (response as { status?: number } | undefined)?.status ?? 0;
    throw new AiReviewRuleMutationFailure(
      status,
      body?.code,
      mapAiReviewRuleErrorMessage(status, body?.message, 'AI 审核规则保存失败'),
    );
  }

  return data;
}

export function useSaveAiReviewRuleMutation() {
  return useMutation<AiReviewRule, AiReviewRuleMutationFailure, AiReviewRuleRequest>({
    mutationFn: saveAiReviewRule,
  });
}

export function mapAiReviewRuleErrorMessage(status: number, message: string | undefined, fallback: string): string {
  if (status === 400) return message ?? 'AI 审核规则校验失败';
  if (status === 403) return '没有配置权限';
  if (status === 404) return message ?? '任务或规则不存在';
  return message ?? fallback;
}
