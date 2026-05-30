export type AnswerPrimitive = string | number | boolean | string[] | null;
export type JsonAnswerValue = AnswerPrimitive | JsonAnswerValue[] | { [key: string]: JsonAnswerValue };
export type AnswerValue = JsonAnswerValue | AnswerPayload;

export interface AnswerPayload {
  [stableId: string]: AnswerValue;
}

export const EMPTY_ANSWER_PAYLOAD: AnswerPayload = {};

export function getFieldValue(payload: AnswerPayload, stableId: string): AnswerValue | undefined {
  return payload[stableId];
}

export function setFieldValue(payload: AnswerPayload, stableId: string, value: AnswerValue): AnswerPayload {
  return { ...payload, [stableId]: value };
}

export function getNestedValue(
  payload: AnswerPayload,
  parentStableId: string,
  childStableId: string,
): AnswerValue | undefined {
  const parent = payload[parentStableId];
  if (isAnswerPayload(parent)) {
    return parent[childStableId];
  }
  return undefined;
}

export function setNestedValue(
  payload: AnswerPayload,
  parentStableId: string,
  childStableId: string,
  value: AnswerValue,
): AnswerPayload {
  const existingParent = payload[parentStableId];
  const parentPayload = isAnswerPayload(existingParent) ? existingParent : {};

  return {
    ...payload,
    [parentStableId]: {
      ...parentPayload,
      [childStableId]: value,
    },
  };
}

export function isAnswerPayload(value: unknown): value is AnswerPayload {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function coerceAnswerPayload(value: unknown): AnswerPayload {
  return isAnswerPayload(value) ? value : EMPTY_ANSWER_PAYLOAD;
}

export function isEmptyAnswerValue(value: unknown): boolean {
  return (
    value === null ||
    value === undefined ||
    value === '' ||
    (Array.isArray(value) && value.length === 0) ||
    (isAnswerPayload(value) && Object.keys(value).length === 0)
  );
}
