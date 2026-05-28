import { createForm } from '@formily/core';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { answerPayloadToFormilyValues } from '../adapters/answerPayloadToFormilyValues';
import { LABEL_HUB_COMPONENTS } from '../adapters/componentRegistry';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from '../adapters/schemaToFormilyISchema';

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

let assertions = 0;
function equal(actual: unknown, expected: unknown, message?: string) {
  if (actual !== expected) {
    throw new Error(message ?? `Expected ${String(actual)} to equal ${String(expected)}`);
  }
}

function deepEqual(actual: unknown, expected: unknown, message?: string) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(message ?? `Expected ${JSON.stringify(actual)} to deep-equal ${JSON.stringify(expected)}`);
  }
}

function notEqual(actual: unknown, expected: unknown, message?: string) {
  if (actual === expected) {
    throw new Error(message ?? 'Expected values to be different references');
  }
}

function check(name: string, assertion: () => void) {
  assertion();
  assertions += 1;
  console.log(`ok ${assertions} - ${name}`);
}

check('component registry maps all current schema field types to LabelHub x-component names', () => {
  equal(LABEL_HUB_COMPONENTS.text, 'LabelHubTextField');
  equal(LABEL_HUB_COMPONENTS.number, 'LabelHubNumberField');
  equal(LABEL_HUB_COMPONENTS.single_select, 'LabelHubSelectField');
  equal(LABEL_HUB_COMPONENTS.multi_select, 'LabelHubSelectField');
  equal(LABEL_HUB_COMPONENTS.date, 'LabelHubDateField');
  equal(LABEL_HUB_COMPONENTS.file_upload, 'LabelHubFileUploadField');
  equal(LABEL_HUB_COMPONENTS.nested_object, 'LabelHubNestedObjectField');
});

check('schemaToFormilyISchema maps stableId properties and recursive nested_object children', () => {
  const schema = schemaToFormilyISchema(fields);
  const properties = schema.properties as Record<string, any>;
  equal(schema.type, 'object');
  equal(properties.text_1?.['x-component'], 'LabelHubTextField');
  equal(properties.number_1?.['x-component'], 'LabelHubNumberField');
  equal(properties.single_1?.['x-component'], 'LabelHubSelectField');
  equal(properties.multi_1?.['x-component'], 'LabelHubSelectField');
  equal(properties.date_1?.['x-component'], 'LabelHubDateField');
  equal(properties.file_1?.['x-component'], 'LabelHubFileUploadField');
  equal(properties.parent?.type, 'object');
  equal(properties.parent?.properties?.child_a?.['x-component'], 'LabelHubTextField');
  equal(properties.text_1?.title, 'Text');
  equal(properties.text_1?.description, 'Text help');
  equal(properties.text_1?.['x-component-props']?.placeholder, 'Enter text');
  deepEqual(schema.required, ['text_1']);
  deepEqual(properties.single_1?.enum, [
    { label: 'Alpha', value: 'a' },
    { label: 'Beta', value: 'b' },
  ]);
});

check('answerPayloadToFormilyValues preserves historical payload keys on outbound load', () => {
  const values = answerPayloadToFormilyValues(historicalPayload);
  deepEqual(values, historicalPayload);
  notEqual(values, historicalPayload);
  notEqual(values.parent, historicalPayload.parent);
  equal(values.removed_top_level, 'historical top-level value');
});

check('Formily dot path updates nested stableId values and inbound save filters to current schema', () => {
  const form = createForm({ initialValues: answerPayloadToFormilyValues(historicalPayload) });
  form.setValuesIn('parent.child_a', 'nested changed');
  form.setValuesIn('$internal', 'drop me');
  form.setValuesIn('_void_hidden', 'drop me too');
  const payload = formilyValuesToAnswerPayload(form.values, fields);
  equal(payload.text_1, 'hello');
  equal(payload.parent && typeof payload.parent === 'object' && !Array.isArray(payload.parent) ? payload.parent.child_a : null, 'nested changed');
  equal(payload.parent && typeof payload.parent === 'object' && !Array.isArray(payload.parent) ? payload.parent.child_b : null, 7);
  equal(Object.hasOwn(payload, 'removed_top_level'), false);
  equal(Object.hasOwn(payload, '$internal'), false);
  equal(payload.parent && typeof payload.parent === 'object' && !Array.isArray(payload.parent) ? Object.hasOwn(payload.parent, 'removed_nested_child') : true, false);
});

check('round-trip identity holds for all current field value shapes when schema is unchanged', () => {
  const currentOnlyPayload: AnswerPayload = {
    text_1: 'hello',
    number_1: 42,
    single_1: 'a',
    multi_1: ['x', 'y'],
    date_1: '2026-05-27',
    file_1: 's3://bucket/file.png',
    parent: {
      child_a: 'nested-a',
      child_b: 7,
    },
  };
  const form = createForm({ initialValues: answerPayloadToFormilyValues(currentOnlyPayload) });
  const payload = formilyValuesToAnswerPayload(form.values, fields);
  deepEqual(payload, currentOnlyPayload);
});

check('empty AnswerPayload and empty SchemaField[] edge cases produce plain empty objects', () => {
  deepEqual(answerPayloadToFormilyValues({}), {});
  deepEqual(formilyValuesToAnswerPayload({ stray: 'value' }, []), {});
  deepEqual(schemaToFormilyISchema([]), { type: 'object', properties: {} });
});

check('snapshot extraction emits JSON-safe plain AnswerPayload without reactive leakage', () => {
  const form = createForm({ initialValues: answerPayloadToFormilyValues(historicalPayload) });
  const payload = formilyValuesToAnswerPayload(form.values, fields);
  const json = JSON.stringify(payload);
  deepEqual(JSON.parse(json), payload);
});

console.log(`C2 adapter assertions passed: ${assertions}`);
