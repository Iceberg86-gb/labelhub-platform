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
    if (rawValue === undefined || isUnansweredFieldValue(field, rawValue)) {
      continue;
    }

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

    const snapshotValue = snapshotAnswerValue(rawValue);
    if (snapshotValue !== undefined) {
      payload[field.stableId] = snapshotValue;
    }
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
  // Tab containers and panes are Formily void fields, so active child fields
  // write to their top-level stableId. The nested tab value is a hydration
  // mirror and must not overwrite a newer top-level value during controlled
  // parent echoes.
  const merged = { ...source };
  for (const child of children) {
    const tabHasChild = Object.hasOwn(tabValue, child.stableId);
    const tabChildValue = tabValue[child.stableId];
    if (!tabHasChild || tabChildValue === undefined) {
      continue;
    }

    if (
      !Object.hasOwn(source, child.stableId)
      || (
        isEmptyPlainObject(source[child.stableId])
        && isNonEmptyPlainObject(tabChildValue)
      )
    ) {
      merged[child.stableId] = tabChildValue;
    }
  }
  return merged;
}

function snapshotAnswerValue(value: unknown): AnswerValue | undefined {
  const serialized = JSON.stringify(value);
  if (serialized === undefined) {
    return undefined;
  }
  return JSON.parse(serialized) as AnswerValue;
}

function isInternalKey(key: string): boolean {
  return key.startsWith('$') || key.startsWith('_void_');
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isUnansweredFieldValue(field: SchemaField, value: unknown): boolean {
  return field.type === 'file_upload' && isEmptyPlainObject(value);
}

function isEmptyPlainObject(value: unknown): value is Record<string, never> {
  return isPlainObject(value) && Object.keys(value).length === 0;
}

function isNonEmptyPlainObject(value: unknown): value is Record<string, unknown> {
  return isPlainObject(value) && Object.keys(value).length > 0;
}
