import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

/**
 * Outbound load policy: AnswerPayload -> Formily initialValues.
 *
 * Preserve ALL keys from the payload, even keys not present in the current
 * SchemaField[].
 *
 * Reason: M6-P0.5 immutability means historical submissions may reference
 * fields removed in newer schema versions, but the immutable answer fact must
 * round-trip without data loss. This adapter only snapshots into plain JSON
 * values for Formily; it does not apply current-schema filtering.
 */
export function answerPayloadToFormilyValues(
  payload: AnswerPayload | null | undefined,
  schemaFields: SchemaField[] = [],
): Record<string, unknown> {
  const values = deepCloneJsonObject(payload ?? {});
  hydrateTabContainerValues(values, schemaFields);
  return values;
}

function deepCloneJsonObject(value: AnswerPayload): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function hydrateTabContainerValues(values: Record<string, unknown>, fields: SchemaField[]) {
  fields.forEach((field) => {
    if (field.type === 'nested_object') {
      const nested = values[field.stableId];
      if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
        hydrateTabContainerValues(nested as Record<string, unknown>, field.children ?? []);
      }
      return;
    }
    if (field.type !== 'tab_container') {
      return;
    }

    const container = asPlainObject(values[field.stableId]) ?? {};
    values[field.stableId] = container;
    field.tabs?.forEach((tab) => {
      const tabValues = asPlainObject(container[tab.stableId]) ?? {};
      container[tab.stableId] = tabValues;
      tab.children?.forEach((child) => {
        if (Object.hasOwn(values, child.stableId)) {
          tabValues[child.stableId] = values[child.stableId];
        }
      });
      hydrateTabContainerValues(tabValues, tab.children ?? []);
    });
  });
}

function asPlainObject(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? value as Record<string, unknown> : null;
}
