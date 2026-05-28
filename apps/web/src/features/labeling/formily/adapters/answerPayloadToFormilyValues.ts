import type { AnswerPayload } from '../../../../entities/submission/answerPayload';

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
export function answerPayloadToFormilyValues(payload: AnswerPayload | null | undefined): Record<string, unknown> {
  return deepCloneJsonObject(payload ?? {});
}

function deepCloneJsonObject(value: AnswerPayload): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

