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
  pass: '通过',
  reject: '打回',
  manual_review: '人工复核',
} as const satisfies Record<AiReviewResult['overallSuggestion'], string>;

export const OVERALL_SUGGESTION_COLORS = {
  pass: 'green',
  reject: 'red',
  manual_review: 'orange',
} as const satisfies Record<AiReviewResult['overallSuggestion'], 'green' | 'red' | 'orange'>;
