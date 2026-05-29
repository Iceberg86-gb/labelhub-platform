import { createForm } from '@formily/core';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { schemaToFormilyISchema } from '../adapters/schemaToFormilyISchema';
import { createSchemaFormilyForm, SchemaFormilyRenderer } from '../SchemaFormilyRenderer';
import { renderClient } from './renderClient';

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

describe('SchemaFormilyRenderer', () => {
  it('mounts without throwing for a 7-type schema', () => {
    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={schemaFields} value={payload} readOnly={false} onChange={() => {}} />,
    );
    expect(view.text()).toContain('Title');
    expect(view.text()).toContain('Meta');
    view.unmount();
  });

  it('emits sanitized AnswerPayload on Formily field update', () => {
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
    expect(lastPayload?.meta).toEqual({ note: 'changed' });
    expect(formilyValuesToAnswerPayload(form.values, schemaFields)).toEqual(lastPayload);
  });

  it('creates a readPretty Formily form in readOnly mode', () => {
    const form = createSchemaFormilyForm({ schemaFields, value: payload, readOnly: true, onChange: () => {} });
    expect(form.readPretty).toBe(true);
  });

  it('exposes the internal Formily form through onFormReady', () => {
    const onFormReady = vi.fn();
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={schemaFields}
        value={payload}
        readOnly={false}
        onChange={() => {}}
        onFormReady={onFormReady}
      />,
    );

    const form = onFormReady.mock.calls.at(-1)?.[0];
    expect(form?.values.title).toBe('Existing');
    expect(form?.values.meta).toEqual({ note: 'nested' });
    view.unmount();
  });

  it('supports external errors through Formily field state', () => {
    const form = createForm({ initialValues: payload });
    form.createField({ name: 'title', basePath: '', title: 'Title' });
    form.setFieldState('title', (state) => {
      state.selfErrors = ['Title is required'];
    });
    let errors: unknown[] = [];
    form.getFieldState('title', (state) => {
      errors = state.selfErrors ?? [];
    });
    expect(errors).toEqual(['Title is required']);
  });

  it('marks multi_select with multiple mode for LabelHubSelectField', () => {
    const schema = schemaToFormilyISchema(schemaFields);
    const properties = schema.properties as Record<string, any>;
    expect(properties.tags['x-component']).toBe('LabelHubSelectField');
    expect(properties.tags['x-component-props'].mode).toBe('multiple');
    expect(properties.status['x-component-props'].mode).toBeUndefined();
  });
});
