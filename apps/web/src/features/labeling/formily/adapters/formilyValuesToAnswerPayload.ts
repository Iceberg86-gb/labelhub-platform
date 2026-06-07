import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload, AnswerValue } from '../../../../entities/submission/answerPayload';

/**
 * Inbound save policy: Formily form.values -> AnswerPayload.
 *
 * Filter to keys present in the current SchemaField[] and drop Formily/internal
 * keys such as "$..." and "_void_...".
 *
 * Reason: only schema-validated fields should enter the persistence path, and
 * trusted-export reproducibility depends on saving the current schema's answer
 * fact without leaking runtime-only Formily state. This is intentionally
 * asymmetric with answerPayloadToFormilyValues(), which preserves historical
 * keys when loading immutable submissions.
 */
export function formilyValuesToAnswerPayload(values: unknown, fields: SchemaField[]): AnswerPayload {
  const source = isPlainObject(values) ? values : {};
  return extractFields(source, fields);
}

function extractFields(source: Record<string, unknown>, fields: SchemaField[]): AnswerPayload {
  const payload: AnswerPayload = {};

  for (const field of fields) {
    if (field.type === 'tab_container') {
      Object.assign(payload, extractTabFields(source, field));
      continue;
    }

    if (field.type === 'show_item' || isInternalKey(field.stableId) || !Object.hasOwn(source, field.stableId)) {
      continue;
    }

    const rawValue = source[field.stableId];
    if (field.type === 'nested_object') {
      if (!isPlainObject(rawValue)) {
        continue;
      }
      const nestedPayload = extractFields(rawValue, field.children ?? []);
      if (Object.keys(nestedPayload).length > 0 || Object.hasOwn(source, field.stableId)) {
        payload[field.stableId] = nestedPayload;
      }
      continue;
    }

    payload[field.stableId] = snapshotAnswerValue(rawValue);
  }

  return payload;
}

function extractTabFields(source: Record<string, unknown>, field: SchemaField): AnswerPayload {
  const payload: AnswerPayload = {};
  const containerValue = source[field.stableId];
  const container = isPlainObject(containerValue) ? containerValue : {};

  for (const tab of field.tabs ?? []) {
    const tabValue = container[tab.stableId];
    const tabSource = isPlainObject(tabValue) ? mergeTabValueSource(source, tabValue, tab.children ?? []) : source;
    Object.assign(payload, extractFields(tabSource, tab.children ?? []));
  }

  return payload;
}

function mergeTabValueSource(
  source: Record<string, unknown>,
  tabValue: Record<string, unknown>,
  children: SchemaField[],
): Record<string, unknown> {
  const merged = { ...source, ...tabValue };
  for (const child of children) {
    if (
      isEmptyPlainObject(tabValue[child.stableId])
      && isNonEmptyPlainObject(source[child.stableId])
    ) {
      merged[child.stableId] = source[child.stableId];
    }
  }
  return merged;
}

function snapshotAnswerValue(value: unknown): AnswerValue {
  return JSON.parse(JSON.stringify(value)) as AnswerValue;
}

function isInternalKey(key: string): boolean {
  return key.startsWith('$') || key.startsWith('_void_');
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isEmptyPlainObject(value: unknown): value is Record<string, never> {
  return isPlainObject(value) && Object.keys(value).length === 0;
}

function isNonEmptyPlainObject(value: unknown): value is Record<string, unknown> {
  return isPlainObject(value) && Object.keys(value).length > 0;
}
