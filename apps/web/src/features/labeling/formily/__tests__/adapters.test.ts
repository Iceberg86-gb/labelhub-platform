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
    content: overrides.content,
    sourcePath: overrides.sourcePath,
    aiPrompt: overrides.aiPrompt,
    acceptedFileTypes: overrides.acceptedFileTypes,
    maxFileSizeMb: overrides.maxFileSizeMb,
    validation: overrides.validation,
    options: overrides.options,
    children: overrides.children,
    tabs: overrides.tabs,
    visibleWhen: overrides.visibleWhen,
    requiredWhen: overrides.requiredWhen,
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
  field({ stableId: 'file_1', type: 'file_upload', acceptedFileTypes: ['image/png'], maxFileSizeMb: 5 }),
  field({ stableId: 'rich_1', type: 'rich_text' }),
  field({ stableId: 'json_1', type: 'json_editor' }),
  field({ stableId: 'llm_1', type: 'llm_interaction', aiPrompt: 'Summarize this field' }),
  field({ stableId: 'show_1', type: 'show_item', content: 'Display-only copy' }),
  field({
    stableId: 'tabs_1',
    type: 'tab_container',
    tabs: [
      {
        stableId: 'tab_a',
        label: 'Tab A',
        children: [field({ stableId: 'tab_text', type: 'text' })],
      },
    ],
  }),
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
  file_1: { objectKey: 'session-attachments/file.png', fileName: 'file.png', contentType: 'image/png', sizeBytes: 1024 },
  rich_1: '<p>rich</p>',
  json_1: { score: 0.95, tags: ['a'] },
  llm_1: { input: 'draft', output: { summary: 'suggestion' }, aiCallId: 9 },
  show_1: 'historical display-only value',
  tab_text: 'tab value',
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
      textarea: 'LabelHubTextareaField',
      number: 'LabelHubNumberField',
      single_select: 'LabelHubSelectField',
      multi_select: 'LabelHubSelectField',
      date: 'LabelHubDateField',
      file_upload: 'LabelHubFileUploadField',
      rich_text: 'LabelHubRichTextField',
      json_editor: 'LabelHubJsonField',
      llm_interaction: 'LabelHubLlmInteractionField',
      show_item: 'LabelHubShowItem',
      nested_object: 'LabelHubNestedObjectField',
      tab_container: 'LabelHubTabsContainer',
    });
  });

  it('maps stableId properties and recursive nested_object children', () => {
    const schema = schemaToFormilyISchema(fields);
    const properties = schema.properties as Record<string, any>;
    expect(schema.type).toBe('object');
    expect(properties.text_1?.['x-component']).toBe('LabelHubTextField');
    expect(properties.parent?.properties?.child_a?.['x-component']).toBe('LabelHubTextField');
    expect(properties.rich_1?.['x-component']).toBe('LabelHubRichTextField');
    expect(properties.json_1?.['x-component']).toBe('LabelHubJsonField');
    expect(properties.llm_1?.['x-component']).toBe('LabelHubLlmInteractionField');
    expect(properties.show_1?.['x-component']).toBe('LabelHubShowItem');
    expect(properties.show_1?.type).toBe('void');
    expect(properties.tabs_1?.['x-component']).toBe('LabelHubTabsContainer');
    expect(properties.tabs_1?.properties?.tab_a?.['x-component']).toBe('LabelHubTabPane');
    expect(properties.tabs_1?.properties?.tab_a?.properties?.tab_text?.['x-component']).toBe('LabelHubTextField');
    expect(properties.text_1?.title).toBe('Text');
    expect(properties.text_1?.description).toBe('Text help');
    expect(properties.text_1?.['x-component-props']?.placeholder).toBe('Enter text（至少2字符）');
    expect(schema.required).toEqual(['text_1']);
    expect(properties.single_1?.enum).toEqual([
      { label: 'Alpha', value: 'a' },
      { label: 'Beta', value: 'b' },
    ]);
  });

  it('maps textarea fields to string Formily schema and LabelHubTextareaField', () => {
    const schema = schemaToFormilyISchema([
      field({
        stableId: 'long_reason',
        type: 'textarea' as SchemaField['type'],
        label: 'Long reason',
        placeholder: '请输入多行说明',
        validation: { required: true, minLength: 10, maxLength: 500, pattern: '^[\\s\\S]+$' },
      }),
    ]);
    const properties = schema.properties as Record<string, any>;

    expect(properties.long_reason?.type).toBe('string');
    expect(properties.long_reason?.['x-component']).toBe('LabelHubTextareaField');
    expect(properties.long_reason?.['x-component-props']?.placeholder).toBe('请输入多行说明（至少10字符）');
    expect(schema.required).toEqual(['long_reason']);
  });

  it('maps visibleWhen and requiredWhen to Formily x-reactions', () => {
    const reactiveFields = [
      field({ stableId: 'driver', type: 'text' }),
      field({
        stableId: 'details',
        type: 'text',
        visibleWhen: { field: 'driver', op: 'eq', value: 'show' },
        requiredWhen: { field: 'driver', op: 'eq', value: 'show' },
      }),
    ];
    const schema = schemaToFormilyISchema(reactiveFields);
    const reaction = (schema.properties as Record<string, any>).details?.['x-reactions'];
    const formilyField = { form: { values: { driver: 'show', details: '' } }, visible: false, display: 'none', required: false };

    expect(reaction).toBeTypeOf('function');
    reaction(formilyField);
    expect(formilyField).toMatchObject({ visible: true, display: 'visible', required: true });

    formilyField.form.values.driver = 'hide';
    reaction(formilyField);
    expect(formilyField).toMatchObject({ visible: false, display: 'none', required: false });
  });

  it('adds the text minLength reminder to placeholders', () => {
    const schema = schemaToFormilyISchema([
      field({
        stableId: 'detailed_comment',
        type: 'text',
        placeholder: '说明评分依据、主要风险和建议',
        validation: { minLength: 5 },
      }),
    ]);
    const properties = schema.properties as Record<string, any>;

    expect(properties.detailed_comment?.['x-component-props']?.placeholder).toBe('说明评分依据、主要风险和建议（至少5字符）');
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
      file_1: { objectKey: 'session-attachments/file.png', fileName: 'file.png', contentType: 'image/png', sizeBytes: 1024 },
      rich_1: '<p>rich</p>',
      json_1: { score: 0.95, tags: ['a'] },
      llm_1: { input: 'draft', output: { summary: 'suggestion' }, aiCallId: 9 },
      tab_text: 'tab value',
      parent: { child_a: 'nested-a', child_b: 7 },
    };
    const form = createForm({ initialValues: answerPayloadToFormilyValues(currentOnlyPayload) });
    expect(formilyValuesToAnswerPayload(form.values, fields)).toEqual(currentOnlyPayload);
  });

  it('hydrates tab container values for Formily paths and emits tab child fields as flat payload', () => {
    const tabFields = [
      field({
        stableId: 'tabs_1',
        type: 'tab_container',
        tabs: [
          { stableId: 'tab_a', label: 'Tab A', children: [field({ stableId: 'tab_text', type: 'text' })] },
        ],
      }),
    ];
    const values = answerPayloadToFormilyValues({ tab_text: 'initial' }, tabFields);
    expect((values.tabs_1 as any).tab_a.tab_text).toBe('initial');
    (values.tabs_1 as any).tab_a.tab_text = 'changed';
    expect(formilyValuesToAnswerPayload(values, tabFields)).toEqual({ tab_text: 'changed' });
  });

  it('does not let an empty tab mirror overwrite a non-empty file_upload object', () => {
    const uploadedFile = {
      objectKey: 'session-attachments/20260607/task-44/session-55/abc-evidence.pdf',
      fileName: 'evidence.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1234,
    };
    const tabFields = [
      field({
        stableId: 'tabs_1',
        type: 'tab_container',
        tabs: [
          { stableId: 'tab_a', label: 'Tab A', children: [field({ stableId: 'evidence_file', type: 'file_upload' })] },
        ],
      }),
    ];

    expect(formilyValuesToAnswerPayload({
      evidence_file: uploadedFile,
      tabs_1: { tab_a: { evidence_file: {} } },
    }, tabFields)).toEqual({ evidence_file: uploadedFile });
  });

  it('omits display-only show_item fields from outbound answer payload', () => {
    const form = createForm({ initialValues: { show_1: 'display copy', text_1: 'hello' } });
    expect(formilyValuesToAnswerPayload(form.values, [
      field({ stableId: 'show_1', type: 'show_item', content: 'Display copy', sourcePath: 'question.prompt' }),
      field({ stableId: 'text_1', type: 'text' }),
    ])).toEqual({ text_1: 'hello' });
  });

  it('passes dataset item payload to show_item component props', () => {
    const itemPayload = { question: { prompt: 'Raw dataset prompt' } };
    const schema = schemaToFormilyISchema([
      field({ stableId: 'show_1', type: 'show_item', content: 'Fallback copy', sourcePath: 'question.prompt' }),
    ], { itemPayload });
    const properties = schema.properties as Record<string, any>;

    expect(properties.show_1?.['x-component']).toBe('LabelHubShowItem');
    expect(properties.show_1?.['x-component-props']?.field.sourcePath).toBe('question.prompt');
    expect(properties.show_1?.['x-component-props']?.itemPayload).toBe(itemPayload);
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

  it('projects named custom validators to Formily validation errors', async () => {
    const errors = await validateFormilyField(field({ stableId: 'url', type: 'text', validation: { customFunction: 'httpsUrl' } }), 'http://example.com');
    expect(errors.some((error) => error.includes('必须是 HTTPS URL'))).toBe(true);
  });

  it('projects textarea validation through the same Formily and payloadValidation path as text', async () => {
    const textareaField = field({
      stableId: 'long_reason',
      type: 'textarea' as SchemaField['type'],
      validation: { required: true, minLength: 10, maxLength: 40, pattern: '^reason:', customFunction: 'nonBlankTrimmed' },
    });
    const valid = { long_reason: 'reason: enough detail' } satisfies AnswerPayload;
    const invalid = { long_reason: 'short' } satisfies AnswerPayload;

    expect(await validateFormilyField(textareaField, valid.long_reason)).toHaveLength(0);
    expect(validatePayload([textareaField], valid)).toHaveLength(0);
    expect((await validateFormilyField(textareaField, invalid.long_reason)).length).toBeGreaterThan(0);
    expect(validatePayload([textareaField], invalid).map((error) => error.reason)).toEqual(['最少 10 字', '格式不正确']);
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
