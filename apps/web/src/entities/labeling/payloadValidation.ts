import type { SchemaField } from '../schema/schemaTypes';
import { validateCustomFunctionValue } from '../schema/customValidation';
import type { AnswerPayload } from '../submission/answerPayload';
import { getFieldValue, isAnswerPayload } from '../submission/answerPayload';
import { buildFlatValueIndex, isFieldConditionallyRequired, isFieldVisible, type FlatValueIndex } from './linkageEvaluator';
import { isPayloadValueEmpty } from './payloadValueSemantics';

export interface PayloadValidationError {
  stableId: string;
  reason: string;
}

export function validatePayload(fields: SchemaField[], payload: AnswerPayload): PayloadValidationError[] {
  const errors: PayloadValidationError[] = [];
  const flatValues = buildFlatValueIndex(fields, payload);
  fields.forEach((field) => {
    validateField(field, getFieldValue(payload, field.stableId), errors, flatValues, payload);
  });
  return errors;
}

function validateField(
  field: SchemaField,
  value: unknown,
  errors: PayloadValidationError[],
  flatValues: FlatValueIndex,
  rootPayload: AnswerPayload,
) {
  if (!isFieldVisible(field, flatValues)) {
    return;
  }
  if (field.type === 'tab_container') {
    field.tabs?.forEach((tab) => {
      tab.children?.forEach((child) => validateField(child, getFieldValue(rootPayload, child.stableId), errors, flatValues, rootPayload));
    });
    return;
  }
  const required = (field.validation?.required ?? false) || isFieldConditionallyRequired(field, flatValues);
  const isEmpty = isPayloadValueEmpty(value);

  if (required && isEmpty) {
    errors.push({ stableId: field.stableId, reason: '此字段必填' });
    return;
  }

  if (isEmpty) return;

  switch (field.type) {
    case 'text':
    case 'textarea':
      validateText(field, value, errors);
      validateCustomFunction(field, value, errors);
      return;
    case 'number':
      validateNumber(field, value, errors);
      return;
    case 'single_select':
      if (typeof value !== 'string' || !field.options?.some((option) => option.value === value)) {
        errors.push({ stableId: field.stableId, reason: '请从选项中选择' });
      }
      return;
    case 'multi_select':
      if (!Array.isArray(value) || !value.every((item) => typeof item === 'string' && field.options?.some((option) => option.value === item))) {
        errors.push({ stableId: field.stableId, reason: '请从选项中选择' });
      }
      return;
    case 'date':
      if (typeof value !== 'string') {
        errors.push({ stableId: field.stableId, reason: '必须是文本' });
      }
      validateCustomFunction(field, value, errors);
      return;
    case 'rich_text':
      validateText(field, value, errors);
      validateCustomFunction(field, value, errors);
      return;
    case 'file_upload':
      if (!isFileUploadValue(value)) {
        errors.push({ stableId: field.stableId, reason: '请上传文件' });
      }
      return;
    case 'json_editor':
      validateCustomFunction(field, value, errors);
      return;
    case 'llm_interaction':
      if (!isAnswerPayload(value)) {
        errors.push({ stableId: field.stableId, reason: '必须是对象' });
      }
      validateCustomFunction(field, value, errors);
      return;
    case 'show_item':
      return;
    case 'nested_object':
      if (!isAnswerPayload(value)) {
        errors.push({ stableId: field.stableId, reason: '必须是对象' });
        return;
      }
      {
        const childPayload = value;
        field.children?.forEach((child) => validateField(child, childPayload[child.stableId], errors, flatValues, childPayload));
      }
      return;
    default: {
      const _exhaustive: never = field.type;
      return _exhaustive;
    }
  }
}

function validateCustomFunction(field: SchemaField, value: unknown, errors: PayloadValidationError[]) {
  if (!field.validation?.customFunction || errors.some((error) => error.stableId === field.stableId)) {
    return;
  }
  const reason = validateCustomFunctionValue(field.type, field.validation.customFunction, value);
  if (reason) {
    errors.push({ stableId: field.stableId, reason });
  }
}

function isFileUploadValue(value: unknown): boolean {
  return isAnswerPayload(value)
    && typeof value.objectKey === 'string'
    && typeof value.fileName === 'string'
    && typeof value.sizeBytes === 'number';
}

function validateText(field: SchemaField, value: unknown, errors: PayloadValidationError[]) {
  if (typeof value !== 'string') {
    errors.push({ stableId: field.stableId, reason: '必须是文本' });
    return;
  }

  const validation = field.validation;
  if (validation?.minLength != null && value.length < validation.minLength) {
    errors.push({ stableId: field.stableId, reason: `最少 ${validation.minLength} 字` });
  }
  if (validation?.maxLength != null && value.length > validation.maxLength) {
    errors.push({ stableId: field.stableId, reason: `最多 ${validation.maxLength} 字` });
  }
  if (validation?.pattern) {
    try {
      if (!new RegExp(validation.pattern).test(value)) {
        errors.push({ stableId: field.stableId, reason: '格式不正确' });
      }
    } catch {
      errors.push({ stableId: field.stableId, reason: '正则表达式无效' });
    }
  }
}

function validateNumber(field: SchemaField, value: unknown, errors: PayloadValidationError[]) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    errors.push({ stableId: field.stableId, reason: '必须是数字' });
    return;
  }

  if (field.validation?.min != null && value < field.validation.min) {
    errors.push({ stableId: field.stableId, reason: `不能小于 ${field.validation.min}` });
  }
  if (field.validation?.max != null && value > field.validation.max) {
    errors.push({ stableId: field.stableId, reason: `不能大于 ${field.validation.max}` });
  }
}

export function errorsByStableId(errors: PayloadValidationError[]): Map<string, string[]> {
  const map = new Map<string, string[]>();
  errors.forEach((error) => {
    const list = map.get(error.stableId) ?? [];
    list.push(error.reason);
    map.set(error.stableId, list);
  });
  return map;
}
