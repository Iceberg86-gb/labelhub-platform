import type { components } from '../shared/api/generated/schema';

type AiReviewResult = components['schemas']['AiReviewResult'];
type AiCallUsage = NonNullable<AiReviewResult['usage']>;

export function M6P3aAiUsageContract() {
  const usage: AiCallUsage = {
    promptTokens: 100,
    completionTokens: 50,
    totalTokens: 150,
    cacheHitTokens: null,
  };
  usage satisfies AiCallUsage;
  return null;
}
