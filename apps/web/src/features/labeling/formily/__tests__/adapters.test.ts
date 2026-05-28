import { createForm } from '@formily/core';
import { describe, expect, it } from 'vitest';
import { validatePayload } from '../../../../entities/labeling/payloadValidation';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { answerPayloadToFormilyValues } from '../adapters/answerPayloadToFormilyValues';
import { LABEL_HUB_COMPONENTS } from '../adapters/componentRegistry';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from '../adapters/schemaToFormilyISchema';
import { schemaToFormilyValidators } from '../adapters/schemaToFormilyValidators';

function field(overrides: Partial<SchemaField> & Pick<SchemaField, 'stableId' | 'type'>): SchemaField {
  return {
    stableId: overrides.stableId,
    type: overrides.type,
    label: overrides.label ?? overrides.stableId,
    placeholder: overrides.placeholder,
    help: overrides.help,
    validation: overrides.validation,
    options: overrides.options,
    children: overrides.children,
  };
}

const fields: SchemaField[] = [
  field({
    stableId: 'text_1',
    type: 'text',
    label: 'Text',
    placeholder: 'Enter text',
    help: 'Text help',
    validation: { required: true, minLength: 2, maxLength: 12, pattern: '^[a-z ]+$' },
  }),
  field({ stableId: 'number_1', type: 'number', validation: { min: 0, max: 100 } }),
  field({
    stableId: 'single_1',
    type: 'single_select',
    options: [
      { label: 'Alpha', value: 'a' },
      { label: 'Beta', value: 'b' },
    ],
  }),
  field({
    stableId: 'multi_1',
    type: 'multi_select',
    options: [
      { label: 'X', value: 'x' },
      { label: 'Y', value: 'y' },
    ],
  }),
  field({ stableId: 'date_1', type: 'date' }),
  field({ stableId: 'file_1', type: 'file_upload' }),
  field({
    stableId: 'parent',
    type: 'nested_object',
    children: [field({ stableId: 'child_a', type: 'text' }), field({ stableId: 'child_b', type: 'number' })],
  }),
];

const historicalPayload: AnswerPayload = {
  text_1: 'hello',
  number_1: 42,
  single_1: 'a',
  multi_1: ['x', 'y'],
  date_1: '2026-05-27',
  file_1: 's3://bucket/file.png',
  parent: {
    child_a: 'nested-a',
    child_b: 7,
    removed_nested_child: 'historical nested value',
  },
  removed_top_level: 'historical top-level value',
};

async function validateFormilyField(fieldSpec: SchemaField, value: unknown): Promise<string[]> {
  const form = createForm();
  const formilyField = form.createField({
    name: fieldSpec.stableId,
    validator: schemaToFormilyValidators(fieldSpec),
  });
  formilyField.setValue(value);
  try {
    await formilyField.validate();
  } catch {
    // Formily stores the user-facing messages on the field; tests assert those.
  }
  return formilyField.selfErrors;
}

describe('Formily adapters', () => {
  it('maps all current schema field types to LabelHub x-component names', () => {
    expect(LABEL_HUB_COMPONENTS).toEqual({
      text: 'LabelHubTextField',
      number: 'LabelHubNumberField',
      single_select: 'LabelHubSelectField',
      multi_select: 'LabelHubSelectField',
      date: 'LabelHubDateField',
      file_upload: 'LabelHubFileUploadField',
      nested_object: 'LabelHubNestedObjectField',
    });
  });

  it('maps stableId properties and recursive nested_object children', () => {
    const schema = schemaToFormilyISchema(fields);
    const properties = schema.properties as Record<string, any>;
    expect(schema.type).toBe('object');
    expect(properties.text_1?.['x-component']).toBe('LabelHubTextField');
    expect(properties.parent?.properties?.child_a?.['x-component']).toBe('LabelHubTextField');
    expect(properties.text_1?.title).toBe('Text');
    expect(properties.text_1?.description).toBe('Text help');
    expect(properties.text_1?.['x-component-props']?.placeholder).toBe('Enter text');
    expect(schema.required).toEqual(['text_1']);
    expect(properties.single_1?.enum).toEqual([
      { label: 'Alpha', value: 'a' },
      { label: 'Beta', value: 'b' },
    ]);
  });

  it('preserves historical payload keys on outbound load', () => {
    const values = answerPayloadToFormilyValues(historicalPayload);
    expect(values).toEqual(historicalPayload);
    expect(values).not.toBe(historicalPayload);
    expect(values.parent).not.toBe(historicalPayload.parent);
    expect(values.removed_top_level).toBe('historical top-level value');
  });

  it('updates nested stableId paths and filters inbound save to current schema', () => {
    const form = createForm({ initialValues: answerPayloadToFormilyValues(historicalPayload) });
    form.setValuesIn('parent.child_a', 'nested changed');
    form.setValuesIn('$internal', 'drop me');
    form.setValuesIn('_void_hidden', 'drop me too');
    const payload = formilyValuesToAnswerPayload(form.values, fields);
    expect(payload.text_1).toBe('hello');
    expect(typeof payload.parent === 'object' && payload.parent && !Array.isArray(payload.parent) ? payload.parent.child_a : null).toBe('nested changed');
    expect(Object.hasOwn(payload, 'removed_top_level')).toBe(false);
    expect(Object.hasOwn(payload, '$internal')).toBe(false);
    expect(typeof payload.parent === 'object' && payload.parent && !Array.isArray(payload.parent) ? Object.hasOwn(payload.parent, 'removed_nested_child') : true).toBe(false);
  });

  it('round-trips all current field value shapes when schema is unchanged', () => {
    const currentOnlyPayload: AnswerPayload = {
      text_1: 'hello',
      number_1: 42,
      single_1: 'a',
      multi_1: ['x', 'y'],
      date_1: '2026-05-27',
      file_1: 's3://bucket/file.png',
      parent: { child_a: 'nested-a', child_b: 7 },
    };
    const form = createForm({ initialValues: answerPayloadToFormilyValues(currentOnlyPayload) });
    expect(formilyValuesToAnswerPayload(form.values, fields)).toEqual(currentOnlyPayload);
  });

  it('handles empty payload and empty schema edge cases', () => {
    expect(answerPayloadToFormilyValues({})).toEqual({});
    expect(formilyValuesToAnswerPayload({ stray: 'value' }, [])).toEqual({});
    expect(schemaToFormilyISchema([])).toEqual({ type: 'object', properties: {} });
  });

  it('emits JSON-safe plain AnswerPayload without reactive leakage', () => {
    const form = createForm({ initialValues: answerPayloadToFormilyValues(historicalPayload) });
    const payload = formilyValuesToAnswerPayload(form.values, fields);
    expect(JSON.parse(JSON.stringify(payload))).toEqual(payload);
  });
});

describe('Formily validation projection', () => {
  it('projects required fields to Formily validation errors', async () => {
    const errors = await validateFormilyField(field({ stableId: 'required_text', type: 'text', validation: { required: true } }), '');
    expect(errors.some((error) => error.includes('此字段必填'))).toBe(true);
  });

  it('projects minLength to Formily validation errors', async () => {
    const errors = await validateFormilyField(field({ stableId: 'short_text', type: 'text', validation: { minLength: 2 } }), 'a');
    expect(errors.some((error) => error.includes('最少 2 字'))).toBe(true);
  });

  it('projects maxLength to Formily validation errors', async () => {
    const errors = await validateFormilyField(field({ stableId: 'long_text', type: 'text', validation: { maxLength: 12 } }), 'abcdefghijklm');
    expect(errors.some((error) => error.includes('最多 12 字'))).toBe(true);
  });

  it('projects pattern to Formily validation errors', async () => {
    const errors = await validateFormilyField(field({ stableId: 'pattern_text', type: 'text', validation: { pattern: '^[a-z ]+$' } }), 'ABC123');
    expect(errors.some((error) => error.includes('格式不正确'))).toBe(true);
  });

  it('keeps Formily passing values within payloadValidation authority', async () => {
    const textFields = [field({ stableId: 'name', type: 'text', validation: { required: true, minLength: 2, maxLength: 12, pattern: '^[a-z ]+$' } })];
    const valid = { name: 'hello world' } satisfies AnswerPayload;
    const invalid = { name: '' } satisfies AnswerPayload;

    expect(await validateFormilyField(textFields[0], valid.name)).toHaveLength(0);
    expect(validatePayload(textFields, valid)).toHaveLength(0);
    expect((await validateFormilyField(textFields[0], invalid.name)).length).toBeGreaterThan(0);
    expect(validatePayload(textFields, invalid).length).toBeGreaterThan(0);

    const schema = schemaToFormilyISchema(textFields);
    expect((schema.properties as Record<string, any>).name?.['x-validator']).toBeTruthy();
  });

  it('projects number min and max to Formily validation errors', async () => {
    const fieldSpec = field({ stableId: 'score', type: 'number', validation: { min: 0, max: 100 } });
    expect((await validateFormilyField(fieldSpec, -5)).some((error) => error.includes('不能小于 0'))).toBe(true);
    expect((await validateFormilyField(fieldSpec, 200)).some((error) => error.includes('不能大于 100'))).toBe(true);

    const form = createForm({ initialValues: { score: 42 } });
    const payload = formilyValuesToAnswerPayload(form.values, [fieldSpec]);
    expect(validatePayload([fieldSpec], payload)).toHaveLength(0);
  });
});
