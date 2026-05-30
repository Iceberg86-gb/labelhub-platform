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

  it('renders show_item from dataset item sourcePath without adding it to answers', () => {
    const sourceField = {
      stableId: 'source_prompt',
      label: 'Prompt',
      type: 'show_item',
      content: 'Fallback prompt',
      sourcePath: 'question.prompt',
    } satisfies SchemaField;
    const answerField = { stableId: 'answer', label: 'Answer', type: 'text' } as SchemaField;
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[sourceField, answerField]}
        value={{ source_prompt: 'must not submit', answer: 'draft' }}
        readOnly={false}
        onChange={() => {}}
        itemPayload={{ question: { prompt: 'Raw dataset prompt' } }}
      />,
    );

    expect(view.text()).toContain('Raw dataset prompt');
    expect(view.text()).not.toContain('Fallback prompt');
    expect(formilyValuesToAnswerPayload({ source_prompt: 'must not submit', answer: 'draft' }, [sourceField, answerField])).toEqual({
      answer: 'draft',
    });
    view.unmount();
  });

  it('falls back to configured show_item content when sourcePath is missing', () => {
    const sourceField = {
      stableId: 'source_prompt',
      label: 'Prompt',
      type: 'show_item',
      content: 'Fallback prompt',
      sourcePath: 'question.missing',
    } satisfies SchemaField;
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[sourceField]}
        value={{}}
        readOnly={false}
        onChange={() => {}}
        itemPayload={{ question: { prompt: 'Raw dataset prompt' } }}
      />,
    );

    expect(view.text()).toContain('Fallback prompt');
    expect(view.text()).not.toContain('Raw dataset prompt');
    view.unmount();
  });
});
