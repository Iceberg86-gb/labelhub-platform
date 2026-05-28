import { createForm } from '@formily/core';
import { renderToString } from 'react-dom/server';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from '../adapters/schemaToFormilyISchema';
import { createSchemaFormilyForm, SchemaFormilyRenderer } from '../SchemaFormilyRenderer';

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

let assertions = 0;
function check(name: string, assertion: () => void) {
  assertion();
  assertions += 1;
  console.log(`ok ${assertions} - ${name}`);
}

const schemaFields: SchemaField[] = [
  { stableId: 'title', label: 'Title', type: 'text', validation: { required: true } },
  { stableId: 'score', label: 'Score', type: 'number' },
  {
    stableId: 'status',
    label: 'Status',
    type: 'single_select',
    options: [
      { label: 'Open', value: 'open' },
      { label: 'Closed', value: 'closed' },
    ],
  },
  {
    stableId: 'tags',
    label: 'Tags',
    type: 'multi_select',
    options: [
      { label: 'A', value: 'a' },
      { label: 'B', value: 'b' },
    ],
  },
  { stableId: 'due', label: 'Due', type: 'date' },
  { stableId: 'file', label: 'File', type: 'file_upload' },
  {
    stableId: 'meta',
    label: 'Meta',
    type: 'nested_object',
    children: [{ stableId: 'note', label: 'Note', type: 'text' }],
  },
];

const payload: AnswerPayload = {
  title: 'Existing',
  score: 2,
  status: 'open',
  tags: ['a'],
  due: '2026-05-27',
  file: 's3://bucket/file.txt',
  meta: { note: 'nested' },
};

check('SchemaFormilyRenderer mounts without throwing for a 7-type schema', () => {
  const html = renderToString(
    <SchemaFormilyRenderer schemaFields={schemaFields} value={payload} readOnly={false} onChange={() => {}} />,
  );
  equal(html.includes('Title'), true);
  equal(html.includes('Meta'), true);
});

check('createSchemaFormilyForm emits sanitized AnswerPayload on Formily field update', () => {
  let lastPayload: AnswerPayload | undefined;
  const form = createSchemaFormilyForm({
    schemaFields,
    value: payload,
    readOnly: false,
    onChange: (next) => {
      lastPayload = next;
    },
  });
  form.setValuesIn('meta.note', 'changed');
  deepEqual(lastPayload?.meta, { note: 'changed' });
  deepEqual(formilyValuesToAnswerPayload(form.values, schemaFields), lastPayload);
});

check('readOnly mode creates a readPretty Formily form', () => {
  const form = createSchemaFormilyForm({ schemaFields, value: payload, readOnly: true, onChange: () => {} });
  equal(form.readPretty, true);
});

check('external errors can be injected into Formily field state', () => {
  const form = createForm({ initialValues: payload });
  form.createField({ name: 'title', basePath: '', title: 'Title' });
  form.setFieldState('title', (state) => {
    state.selfErrors = ['Title is required'];
  });
  let errors: unknown[] = [];
  form.getFieldState('title', (state) => {
    errors = state.selfErrors ?? [];
  });
  deepEqual(errors, ['Title is required']);
});

check('schema mapper marks multi_select with multiple mode for LabelHubSelectField', () => {
  const schema = schemaToFormilyISchema(schemaFields);
  const properties = schema.properties as Record<string, any>;
  equal(properties.tags['x-component'], 'LabelHubSelectField');
  equal(properties.tags['x-component-props'].mode, 'multiple');
  equal(properties.status['x-component-props'].mode, undefined);
});

console.log(`C3 renderer assertions passed: ${assertions}`);
