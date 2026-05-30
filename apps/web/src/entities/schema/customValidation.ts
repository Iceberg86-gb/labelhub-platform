import type { SchemaFieldType } from './schemaTypes';

export const CUSTOM_VALIDATION_FUNCTIONS = ['nonBlankTrimmed', 'httpsUrl', 'jsonObject'] as const;

export type CustomValidationFunction = (typeof CUSTOM_VALIDATION_FUNCTIONS)[number];

const STRING_FUNCTIONS = new Set<SchemaFieldType>(['text', 'rich_text', 'date']);
const OBJECT_FUNCTIONS = new Set<SchemaFieldType>(['json_editor', 'llm_interaction']);

export function isSupportedCustomValidationFunction(value: unknown): value is CustomValidationFunction {
  return typeof value === 'string' && CUSTOM_VALIDATION_FUNCTIONS.includes(value as CustomValidationFunction);
}

export function isCustomValidationCompatible(type: SchemaFieldType, customFunction: string): boolean {
  if (customFunction === 'jsonObject') {
    return OBJECT_FUNCTIONS.has(type);
  }
  if (customFunction === 'httpsUrl') {
    return type === 'text';
  }
  if (customFunction === 'nonBlankTrimmed') {
    return STRING_FUNCTIONS.has(type);
  }
  return false;
}

export function validateCustomFunctionValue(type: SchemaFieldType, customFunction: string | undefined, value: unknown): string | undefined {
  if (!customFunction || !isSupportedCustomValidationFunction(customFunction) || !isCustomValidationCompatible(type, customFunction)) {
    return undefined;
  }

  if (customFunction === 'jsonObject') {
    return isPlainJsonObject(value) ? undefined : '必须是 JSON 对象';
  }
  if (customFunction === 'httpsUrl') {
    return isHttpsUrl(value) ? undefined : '必须是 HTTPS URL';
  }
  if (customFunction === 'nonBlankTrimmed') {
    return typeof value === 'string' && value.trim().length > 0 ? undefined : '内容不能为空白';
  }
  return undefined;
}

function isPlainJsonObject(value: unknown): boolean {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isHttpsUrl(value: unknown): boolean {
  if (typeof value !== 'string' || !value.trim()) {
    return false;
  }
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}
