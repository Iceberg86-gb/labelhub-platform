import type { components } from '../../shared/api/generated/schema';

export type AiCall = components['schemas']['AiCall'];
export type AiCallStatus = components['schemas']['AiCallStatus'];
export type AiCallInField = components['schemas']['AiCallInField'];
export type FieldFinding = components['schemas']['FieldFinding'];
export type AiReviewResult = components['schemas']['AiReviewResult'];
export type TriggerAiReviewRequest = components['schemas']['TriggerAiReviewRequest'];
export type SubmissionAiProvenance = components['schemas']['SubmissionAiProvenance'];

export const SEVERITY_LABELS = {
  info: '提示',
  warning: '警告',
  error: '错误',
} as const satisfies Record<FieldFinding['severity'], string>;

export const SEVERITY_COLORS = {
  info: 'blue',
  warning: 'orange',
  error: 'red',
} as const satisfies Record<FieldFinding['severity'], string>;

export const OVERALL_SUGGESTION_LABELS = {
  looks_good: '看起来良好',
  needs_review: '需要复查',
  issues_found: '发现问题',
} as const satisfies Record<AiReviewResult['overallSuggestion'], string>;
