import type { AiReviewRule, AiReviewRuleRequest } from './aiReviewRuleTypes';

export const AI_REVIEW_RULE_FORM_MESSAGES = {
  promptRequired: 'Prompt 模板不能为空',
  dimensionsRequired: '评分维度不能为空',
  dimensionsDuplicate: '评分维度不能重复',
  thresholdRange: '阈值必须在 0 到 1 之间',
  thresholdOrder: '打回阈值必须小于通过阈值',
} as const;

export type AiReviewRuleFormState = {
  promptTemplate: string;
  dimensions: string[];
  passThreshold: string;
  rejectThreshold: string;
};

export type AiReviewRuleFormErrors = Partial<Record<keyof AiReviewRuleFormState, string>>;

type BuildRequestResult =
  | { ok: true; request: AiReviewRuleRequest }
  | { ok: false; errors: AiReviewRuleFormErrors };

type SubmitResult =
  | { ok: true; rule: AiReviewRule }
  | { ok: false; errors: AiReviewRuleFormErrors }
  | { ok: false; errorMessage: string };

type SaveAiReviewRule = (request: AiReviewRuleRequest) => Promise<AiReviewRule>;

export function createDefaultAiReviewRuleFormState(): AiReviewRuleFormState {
  return {
    promptTemplate: '',
    dimensions: [''],
    passThreshold: '0.8',
    rejectThreshold: '0.2',
  };
}

export function validateAiReviewRuleForm(state: AiReviewRuleFormState): AiReviewRuleFormErrors {
  const errors: AiReviewRuleFormErrors = {};
  const dimensions = state.dimensions.map((dimension) => dimension.trim());
  const passThreshold = Number(state.passThreshold);
  const rejectThreshold = Number(state.rejectThreshold);

  if (state.promptTemplate.trim().length === 0) {
    errors.promptTemplate = AI_REVIEW_RULE_FORM_MESSAGES.promptRequired;
  }

  if (dimensions.length === 0 || dimensions.some((dimension) => dimension.length === 0)) {
    errors.dimensions = AI_REVIEW_RULE_FORM_MESSAGES.dimensionsRequired;
  } else if (new Set(dimensions).size !== dimensions.length) {
    errors.dimensions = AI_REVIEW_RULE_FORM_MESSAGES.dimensionsDuplicate;
  }

  if (!Number.isFinite(passThreshold) || passThreshold < 0 || passThreshold > 1) {
    errors.passThreshold = AI_REVIEW_RULE_FORM_MESSAGES.thresholdRange;
  }

  if (!Number.isFinite(rejectThreshold) || rejectThreshold < 0 || rejectThreshold > 1) {
    errors.rejectThreshold = AI_REVIEW_RULE_FORM_MESSAGES.thresholdRange;
  } else if (Number.isFinite(passThreshold) && passThreshold >= 0 && passThreshold <= 1 && rejectThreshold >= passThreshold) {
    errors.rejectThreshold = AI_REVIEW_RULE_FORM_MESSAGES.thresholdOrder;
  }

  return errors;
}

export function buildAiReviewRuleRequest(taskId: number, state: AiReviewRuleFormState): BuildRequestResult {
  const errors = validateAiReviewRuleForm(state);
  if (Object.keys(errors).length > 0) {
    return { ok: false, errors };
  }

  return {
    ok: true,
    request: {
      taskId,
      promptTemplate: state.promptTemplate,
      dimensions: state.dimensions.map((dimension) => dimension.trim()),
      threshold: Number(state.passThreshold),
      passThreshold: Number(state.passThreshold),
      rejectThreshold: Number(state.rejectThreshold),
    },
  };
}

export async function submitAiReviewRuleForm(
  taskId: number,
  state: AiReviewRuleFormState,
  save: SaveAiReviewRule,
): Promise<SubmitResult> {
  const result = buildAiReviewRuleRequest(taskId, state);
  if (!result.ok) {
    return result;
  }

  try {
    return { ok: true, rule: await save(result.request) };
  } catch (error) {
    return { ok: false, errorMessage: getUserMessage(error) };
  }
}

function getUserMessage(error: unknown) {
  if (error && typeof error === 'object' && 'userMessage' in error) {
    return String((error as { userMessage?: unknown }).userMessage ?? 'AI 审核规则保存失败');
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'AI 审核规则保存失败';
}
