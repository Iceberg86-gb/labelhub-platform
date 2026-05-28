import type { LinkageAtomicCondition, LinkageCondition, LinkageConditionGroup, SchemaField } from '../schema/schemaTypes';
import type { AnswerPayload } from '../submission/answerPayload';
import { isAnswerPayload } from '../submission/answerPayload';
import { isPayloadValueEmpty } from './payloadValueSemantics';

export type FlatValueIndex = ReadonlyMap<string, unknown>;

type FlatValueSource = FlatValueIndex | Record<string, unknown>;

export function evaluateLinkageCondition(
  condition: LinkageCondition | null | undefined,
  flatValues: FlatValueSource,
): boolean {
  if (isAtomicCondition(condition)) {
    return evaluateAtomic(condition, flatValues);
  }
  if (isGroupCondition(condition)) {
    return evaluateGroup(condition, flatValues);
  }
  return false;
}

export function buildFlatValueIndex(fields: SchemaField[], payload: AnswerPayload): Map<string, unknown> {
  const values = new Map<string, unknown>();
  indexFieldValues(fields, payload, values);
  return values;
}

export function isFieldVisible(field: SchemaField, flatValues: FlatValueSource): boolean {
  return !field.visibleWhen || evaluateLinkageCondition(field.visibleWhen, flatValues);
}

export function isFieldConditionallyRequired(field: SchemaField, flatValues: FlatValueSource): boolean {
  return Boolean(field.requiredWhen && evaluateLinkageCondition(field.requiredWhen, flatValues));
}

function evaluateGroup(group: LinkageConditionGroup, flatValues: FlatValueSource): boolean {
  if (group.allOf?.length) {
    return group.allOf.every((condition) => evaluateAtomic(condition, flatValues));
  }
  if (group.anyOf?.length) {
    return group.anyOf.some((condition) => evaluateAtomic(condition, flatValues));
  }
  return false;
}

function evaluateAtomic(condition: LinkageAtomicCondition, flatValues: FlatValueSource): boolean {
  if (!condition.field || !condition.op) {
    return false;
  }

  const fieldValue = getFlatValue(flatValues, condition.field);
  if (condition.op === 'empty') {
    return isPayloadValueEmpty(fieldValue);
  }
  if (condition.op === 'notEmpty') {
    return !isPayloadValueEmpty(fieldValue);
  }
  if (isPayloadValueEmpty(fieldValue)) {
    return false;
  }

  switch (condition.op) {
    case 'eq':
      return compareEquality(fieldValue, condition.value, false);
    case 'neq':
      return compareEquality(fieldValue, condition.value, true);
    case 'in':
      return matchesAny(fieldValue, condition.value);
    case 'notIn':
      return matchesNone(fieldValue, condition.value);
    case 'gt':
      return compareNumbers(fieldValue, condition.value, (comparison) => comparison > 0);
    case 'gte':
      return compareNumbers(fieldValue, condition.value, (comparison) => comparison >= 0);
    case 'lt':
      return compareNumbers(fieldValue, condition.value, (comparison) => comparison < 0);
    case 'lte':
      return compareNumbers(fieldValue, condition.value, (comparison) => comparison <= 0);
    default: {
      const _exhaustive: never = condition.op;
      return _exhaustive;
    }
  }
}

function compareEquality(fieldValue: unknown, conditionValue: unknown, negate: boolean): boolean {
  if (!isScalarLike(fieldValue) || !isScalarLike(conditionValue)) {
    return false;
  }
  const equal = valuesEqual(fieldValue, conditionValue);
  return negate ? !equal : equal;
}

function matchesAny(fieldValue: unknown, conditionValue: unknown): boolean {
  const conditionItems = asScalarArray(conditionValue);
  if (!conditionItems) {
    return false;
  }

  if (Array.isArray(fieldValue)) {
    return fieldValue.some((selected) => conditionItems.some((item) => valuesEqual(selected, item)));
  }

  return isScalarLike(fieldValue) && conditionItems.some((item) => valuesEqual(fieldValue, item));
}

function matchesNone(fieldValue: unknown, conditionValue: unknown): boolean {
  const conditionItems = asScalarArray(conditionValue);
  if (!conditionItems) {
    return false;
  }

  if (Array.isArray(fieldValue)) {
    return fieldValue.every((selected) => conditionItems.every((item) => !valuesEqual(selected, item)));
  }

  return isScalarLike(fieldValue) && conditionItems.every((item) => !valuesEqual(fieldValue, item));
}

function compareNumbers(fieldValue: unknown, conditionValue: unknown, predicate: (comparison: number) => boolean): boolean {
  const left = asFiniteNumber(fieldValue);
  const right = asFiniteNumber(conditionValue);
  return left != null && right != null && predicate(left === right ? 0 : left > right ? 1 : -1);
}

function valuesEqual(left: unknown, right: unknown): boolean {
  const leftNumber = asFiniteNumber(left);
  const rightNumber = asFiniteNumber(right);
  if (leftNumber != null || rightNumber != null) {
    return leftNumber != null && rightNumber != null && leftNumber === rightNumber;
  }
  return typeof left === 'string' && typeof right === 'string' && left === right
    || typeof left === 'boolean' && typeof right === 'boolean' && left === right;
}

function isScalarLike(value: unknown): value is string | number | boolean {
  return typeof value === 'string' || typeof value === 'boolean' || asFiniteNumber(value) != null;
}

function asScalarArray(value: unknown): Array<string | number | boolean> | null {
  if (!Array.isArray(value) || value.some((item) => !isScalarLike(item))) {
    return null;
  }
  return value;
}

function asFiniteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function getFlatValue(flatValues: FlatValueSource, stableId: string): unknown {
  return isReadonlyMap(flatValues) ? flatValues.get(stableId) : flatValues[stableId];
}

function isAtomicCondition(condition: LinkageCondition | null | undefined): condition is LinkageAtomicCondition {
  return Boolean(condition && ('field' in condition || 'op' in condition));
}

function isGroupCondition(condition: LinkageCondition | null | undefined): condition is LinkageConditionGroup {
  return Boolean(condition && ('allOf' in condition || 'anyOf' in condition));
}

function isReadonlyMap(value: FlatValueSource): value is ReadonlyMap<string, unknown> {
  return value instanceof Map;
}

function indexFieldValues(fields: SchemaField[] | undefined, source: AnswerPayload | undefined, values: Map<string, unknown>) {
  (fields ?? []).forEach((field) => {
    const value = source?.[field.stableId];
    values.set(field.stableId, value);
    if (field.type === 'nested_object') {
      indexFieldValues(field.children, isAnswerPayload(value) ? value : undefined, values);
    }
  });
}
