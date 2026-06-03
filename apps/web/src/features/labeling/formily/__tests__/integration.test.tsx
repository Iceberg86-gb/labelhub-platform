import { describe, expect, it } from 'vitest';
import labelerSessionSource from '../../../../pages/labeler/LabelerSessionPage.tsx?raw';
import labelerSubmissionSource from '../../../../pages/labeler/LabelerSubmissionPage.tsx?raw';
import ownerSubmissionSource from '../../../../pages/owner/OwnerSubmissionPage.tsx?raw';
import reviewerSubmissionSource from '../../../../pages/reviewer/ReviewerSubmissionPage.tsx?raw';
import type { FieldFinding } from '../../../../entities/ai/aiTypes';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { createEmptyPreviewPayload, SchemaFormilyPreviewPanel } from '../preview/SchemaFormilyPreviewPanel';
import { applyExternalErrorsToForm, createSchemaFormilyForm, SchemaFormilyRenderer } from '../SchemaFormilyRenderer';
import { renderClient } from './renderClient';

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

const consumerSources = [
  labelerSessionSource,
  labelerSubmissionSource,
  ownerSubmissionSource,
  reviewerSubmissionSource,
];

const basicFields: SchemaField[] = [
  field({ stableId: 'field_X', type: 'text', label: '证据字段' }),
  field({ stableId: 'score', type: 'number', label: '分数' }),
];

describe('M7-P2 Formily consumer integration', () => {
  it('switches all legacy page consumers to SchemaFormilyRenderer', () => {
    for (const source of consumerSources) {
      expect(source).toContain('SchemaFormilyRenderer');
      expect(source).not.toContain("features/labeling/SchemaRenderer");
      expect(source).not.toContain('<SchemaRenderer');
    }
  });

  it('passes dataset item payload to historical and review renderers for show_item fields', () => {
    for (const source of [labelerSubmissionSource, ownerSubmissionSource, reviewerSubmissionSource]) {
      expect(source).toContain('itemPayload={renderSchema.datasetItem?.itemPayload}');
    }
    expect(labelerSessionSource).toContain('itemPayload={datasetItemContext.payload}');
  });

  it('supports LabelerSession editable value persistence', () => {
    let nextPayload: AnswerPayload | undefined;
    const form = createSchemaFormilyForm({
      schemaFields: basicFields,
      value: { field_X: 'before', score: 1 },
      readOnly: false,
      onChange: (next) => {
        nextPayload = next;
      },
    });

    form.setValuesIn('field_X', 'after');

    expect(nextPayload).toEqual({ field_X: 'after', score: 1 });
  });

  it('supports LabelerSubmitted read-only rendering', () => {
    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={basicFields} value={{ field_X: 'submitted' }} readOnly onChange={() => {}} />,
    );

    expect(view.text()).toContain('证据字段');
    expect(view.text()).toContain('submitted');
    view.unmount();
  });

  it('keeps owner AI review stableId targets compatible with the Formily render path', () => {
    const finding: FieldFinding = {
      fieldPath: 'field_X',
      label: '证据字段',
      severity: 'warning',
      finding: 'AI finding remains targeted to field_X',
      confidence: '0.91',
    };

    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={basicFields} value={{ field_X: 'owner value' }} readOnly onChange={() => {}} />,
    );

    expect(view.text()).toContain('证据字段');
    expect(finding.fieldPath).toBe('field_X');
    expect(finding.label).toBe('证据字段');
    view.unmount();
  });

  it('supports reviewer read-only external field errors', () => {
    const form = createSchemaFormilyForm({
      schemaFields: basicFields,
      value: { field_X: 'review value' },
      readOnly: true,
      onChange: () => {},
    });

    form.createField({ name: 'field_X', basePath: '', title: '证据字段' });
    applyExternalErrorsToForm(form, basicFields, new Map([['field_X', ['需要复查']]]));

    let errors: unknown[] = [];
    form.getFieldState('field_X', (state) => {
      errors = state.selfErrors ?? [];
    });
    expect(errors).toEqual(['需要复查']);
  });

  it('keeps designer preview one-way and reactive to field additions', () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[field({ stableId: 'title', type: 'text', label: '标题' })]} />);

    expect(view.text()).toContain('标题');
    expect(view.text()).not.toContain('分数');

    view.rerender(
      <SchemaFormilyPreviewPanel
        schemaFields={[
          field({ stableId: 'title', type: 'text', label: '标题' }),
          field({ stableId: 'score', type: 'number', label: '分数' }),
        ]}
      />,
    );

    expect(view.text()).toContain('分数');
    expect(createEmptyPreviewPayload()).toEqual({});
    view.unmount();
  });
});
