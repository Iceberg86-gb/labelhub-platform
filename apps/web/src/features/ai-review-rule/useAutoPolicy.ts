import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/client';
import type {
  AiReviewAutoPolicyEnableRequest,
  AiReviewAutoPolicyState,
  AiReviewRuleShadowStats,
} from './aiReviewRuleTypes';
import { mapAiReviewRuleErrorMessage } from './useSaveAiReviewRuleMutation';

/** ADR-020 auto-pass policy: shadow evidence, current state, enable / disable. */
export class AutoPolicyFailure extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    public readonly userMessage: string,
  ) {
    super(userMessage);
  }
}

export const shadowStatsQueryKey = (ruleId: number) => ['ai-review-rules', ruleId, 'shadow-stats'] as const;
export const autoPolicyQueryKey = (ruleId: number) => ['ai-review-rules', ruleId, 'auto-policy'] as const;

function failure(status: number, error: unknown, fallback: string): AutoPolicyFailure {
  const body = error as { code?: string; message?: string } | undefined;
  // 409 AUTO_GATE_NOT_MET carries the measured evidence in its message; show it verbatim.
  const message = status === 409 ? body?.message ?? '未达到开启门槛' : mapAiReviewRuleErrorMessage(status, body?.message, fallback);
  return new AutoPolicyFailure(status, body?.code, message);
}

export function useShadowStatsQuery(ruleId: number | undefined) {
  return useQuery<AiReviewRuleShadowStats, AutoPolicyFailure>({
    queryKey: shadowStatsQueryKey(ruleId ?? 0),
    enabled: typeof ruleId === 'number' && ruleId > 0,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/ai-review/rules/{ruleId}/shadow-stats', {
        params: { path: { ruleId: ruleId as number } },
      });
      if (error || !data) throw failure(response.status, error, '影子统计加载失败');
      return data;
    },
  });
}

export function useAutoPolicyQuery(ruleId: number | undefined) {
  return useQuery<AiReviewAutoPolicyState, AutoPolicyFailure>({
    queryKey: autoPolicyQueryKey(ruleId ?? 0),
    enabled: typeof ruleId === 'number' && ruleId > 0,
    queryFn: async () => {
      const { data, error, response } = await apiClient.GET('/ai-review/rules/{ruleId}/auto-policy', {
        params: { path: { ruleId: ruleId as number } },
      });
      if (error || !data) throw failure(response.status, error, '自动通过状态加载失败');
      return data;
    },
  });
}

export function useEnableAutoPolicyMutation(ruleId: number) {
  const queryClient = useQueryClient();
  return useMutation<AiReviewAutoPolicyState, AutoPolicyFailure, AiReviewAutoPolicyEnableRequest>({
    mutationFn: async (body) => {
      const { data, error, response } = await apiClient.POST('/ai-review/rules/{ruleId}/auto-policy/enable', {
        params: { path: { ruleId } },
        body,
      });
      if (error || !data) throw failure(response.status, error, '开启自动通过失败');
      return data;
    },
    onSuccess: (state) => queryClient.setQueryData(autoPolicyQueryKey(ruleId), state),
  });
}

export function useDisableAutoPolicyMutation(ruleId: number) {
  const queryClient = useQueryClient();
  return useMutation<AiReviewAutoPolicyState, AutoPolicyFailure, { reason?: string }>({
    mutationFn: async ({ reason }) => {
      const { data, error, response } = await apiClient.POST('/ai-review/rules/{ruleId}/auto-policy/disable', {
        params: { path: { ruleId } },
        body: { reason: reason ?? null },
      });
      if (error || !data) throw failure(response.status, error, '关闭自动通过失败');
      return data;
    },
    onSuccess: (state) => queryClient.setQueryData(autoPolicyQueryKey(ruleId), state),
  });
}
