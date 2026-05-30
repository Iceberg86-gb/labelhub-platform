import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../schema/schemaTypes';
import type { AnswerPayload } from '../submission/answerPayload';
import { validatePayload } from './payloadValidation';

function field(overrides: Partial<SchemaField> & Pick<SchemaField, 'stableId' | 'type'>): SchemaField {
  return {
    stableId: overrides.stableId,
    type: overrides.type,
    label: overrides.label ?? overrides.stableId,
    validation: overrides.validation,
    visibleWhen: overrides.visibleWhen,
    requiredWhen: overrides.requiredWhen,
    options: overrides.options,
    children: overrides.children,
  };
}

describe('payloadValidation linkage integration', () => {
  it('skips all validation for hidden static-required fields', () => {
    const fields = [
      field({ stableId: 'driver', type: 'single_select', options: [{ label: 'No', value: 'no' }] }),
      field({
        stableId: 'hidden_text',
        type: 'text',
        validation: { required: true, minLength: 5 },
        visibleWhen: { field: 'driver', op: 'eq', value: 'yes' },
      }),
    ];

    expect(validatePayload(fields, { driver: 'no', hidden_text: '' })).toEqual([]);
  });

  it('keeps existing required behavior when a field is visible', () => {
    const fields = [
      field({ stableId: 'driver', type: 'single_select', options: [{ label: 'Yes', value: 'yes' }] }),
      field({
        stableId: 'visible_text',
        type: 'text',
        validation: { required: true },
        visibleWhen: { field: 'driver', op: 'eq', value: 'yes' },
      }),
    ];

    expect(validatePayload(fields, { driver: 'yes', visible_text: '' })).toEqual([
      { stableId: 'visible_text', reason: '此字段必填' },
    ]);
  });

  it('applies requiredWhen only when the field is visible', () => {
    const fields = [
      field({ stableId: 'type', type: 'text' }),
      field({
        stableId: 'details',
        type: 'text',
        requiredWhen: { field: 'type', op: 'eq', value: 'other' },
      }),
    ];

    expect(validatePayload(fields, { type: 'other', details: '' })).toEqual([
      { stableId: 'details', reason: '此字段必填' },
    ]);
    expect(validatePayload(fields, { type: 'standard', details: '' })).toEqual([]);
  });

  it('uses a flat value index for nested requiredWhen references', () => {
    const fields = [
      field({ stableId: 'type', type: 'text' }),
      field({
        stableId: 'parent',
        type: 'nested_object',
        children: [
          field({
            stableId: 'child_note',
            type: 'text',
            requiredWhen: { field: 'type', op: 'eq', value: 'other' },
          }),
        ],
      }),
    ];
    const payload: AnswerPayload = { type: 'other', parent: { child_note: '' } };

    expect(validatePayload(fields, payload)).toEqual([{ stableId: 'child_note', reason: '此字段必填' }]);
  });

  it('hides a nested parent and therefore skips parent shape and child validation', () => {
    const fields = [
      field({ stableId: 'type', type: 'text' }),
      field({
        stableId: 'parent',
        type: 'nested_object',
        validation: { required: true },
        visibleWhen: { field: 'type', op: 'eq', value: 'show' },
        children: [field({ stableId: 'child_note', type: 'text', validation: { required: true } })],
      }),
    ];

    expect(validatePayload(fields, { type: 'hide' })).toEqual([]);
  });

  it('applies named custom validation functions after type validation', () => {
    const fields = [
      field({ stableId: 'url', type: 'text', validation: { customFunction: 'httpsUrl' } }),
      field({ stableId: 'metadata', type: 'json_editor', validation: { customFunction: 'jsonObject' } }),
    ];

    expect(validatePayload(fields, { url: 'https://example.com', metadata: { score: 1 } })).toEqual([]);
    expect(validatePayload(fields, { url: 'http://example.com', metadata: ['not-object'] })).toEqual([
      { stableId: 'url', reason: '必须是 HTTPS URL' },
      { stableId: 'metadata', reason: '必须是 JSON 对象' },
    ]);
  });
});
