import type { ReactNode } from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { SchemaFormilyPreviewPanel } from '../preview/SchemaFormilyPreviewPanel';
import { renderClient } from './renderClient';

vi.mock('@douyinfe/semi-ui', () => {
  function Select({
    children,
    multiple,
    onChange,
    placeholder,
    value,
  }: {
    children?: ReactNode;
    multiple?: boolean;
    onChange?: (value: string | string[]) => void;
    placeholder?: string;
    value?: string | string[];
  }) {
    return (
      <select
        multiple={multiple}
        value={value ?? (multiple ? [] : '')}
        onChange={(event) =>
          onChange?.(
            multiple
              ? Array.from(event.currentTarget.selectedOptions).map((option) => option.value)
              : event.currentTarget.value,
          )
        }
      >
        {!multiple ? <option value="">{placeholder ?? '请选择'}</option> : null}
        {children}
      </select>
    );
  }

  Select.Option = function Option({ children, value }: { children?: ReactNode; value: string }) {
    return <option value={value}>{children}</option>;
  };

  function TextArea({
    onChange,
    placeholder,
    value,
  }: {
    onChange?: (value: string) => void;
    placeholder?: string;
    value?: string;
  }) {
    return (
      <textarea
        placeholder={placeholder}
        value={value ?? ''}
        onChange={(event) => onChange?.(event.currentTarget.value)}
      />
    );
  }

  return {
    TextArea,
    Select,
    Tag({ children }: { children?: ReactNode }) {
      return <span>{children}</span>;
    },
    Toast: {
      warning: vi.fn(),
    },
    Typography: {
      Text({ children }: { children?: ReactNode }) {
        return <span>{children}</span>;
      },
    },
  };
});

function selectField(stableId: string, label: string, patch: Partial<SchemaField> = {}): SchemaField {
  return {
    stableId,
    label,
    type: 'single_select',
    options: [
      { label: '1', value: '1' },
      { label: '2', value: '2' },
      { label: '3', value: '3' },
      { label: '4', value: '4' },
      { label: '5', value: '5' },
    ],
    ...patch,
  };
}

const relevanceField = selectField('relevance_score', '相关性');
const accuracyField = selectField('accuracy_score', '准确性');
const formatField = selectField('format_score', '表达与格式');
const allOfFormatField = selectField('format_score', '表达与格式', {
  visibleWhen: {
    allOf: [
      { field: 'relevance_score', op: 'eq', value: '5' },
      { field: 'accuracy_score', op: 'eq', value: '5' },
    ],
  },
});
const anyOfFormatField = selectField('format_score', '表达与格式', {
  visibleWhen: {
    anyOf: [
      { field: 'relevance_score', op: 'eq', value: '5' },
      { field: 'accuracy_score', op: 'eq', value: '5' },
    ],
  },
});
const atomicFormatField = selectField('format_score', '表达与格式', {
  visibleWhen: { field: 'relevance_score', op: 'eq', value: '5' },
});
const formatRequiredWhenRelevanceIsFive = selectField('format_score', '表达与格式', {
  requiredWhen: { field: 'relevance_score', op: 'eq', value: '5' },
});
const fixedAnswerField: SchemaField = {
  stableId: 'fixed_answer',
  label: '修订建议',
  type: 'json_editor',
};
const fixedAnswerAllOfField: SchemaField = {
  ...fixedAnswerField,
  visibleWhen: {
    allOf: [
      { field: 'relevance_score', op: 'eq', value: '5' },
      { field: 'accuracy_score', op: 'eq', value: '5' },
    ],
  },
};

function fieldContainer(container: HTMLElement, stableId: string): HTMLElement | null {
  return container.querySelector(`[data-labeling-field-id="${stableId}"]`);
}

function selectFor(container: HTMLElement, stableId: string): HTMLSelectElement {
  const select = fieldContainer(container, stableId)?.querySelector('select');
  if (!select) {
    throw new Error(`Missing select for ${stableId}`);
  }
  return select as HTMLSelectElement;
}

function textareaFor(container: HTMLElement, stableId: string): HTMLTextAreaElement {
  const textarea = fieldContainer(container, stableId)?.querySelector('textarea');
  if (!textarea) {
    throw new Error(`Missing textarea for ${stableId}`);
  }
  return textarea as HTMLTextAreaElement;
}

async function selectPreviewValue(container: HTMLElement, stableId: string, value: string) {
  const select = selectFor(container, stableId);
  await act(async () => {
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flushPreviewDebounce();
}

async function typePreviewJson(container: HTMLElement, stableId: string, value: string) {
  const textarea = textareaFor(container, stableId);
  await act(async () => {
    textarea.value = value;
    textarea.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await flushPreviewDebounce();
}

async function flushPreviewDebounce() {
  await act(async () => {
    vi.runOnlyPendingTimers();
  });
  await act(async () => {
    await Promise.resolve();
  });
}

describe('SchemaFormilyPreviewPanel linkage visibility', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('hides an allOf group target until every driver field is satisfied', async () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[relevanceField, accuracyField, allOfFormatField]} />);

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'accuracy_score', '5');

    expect(fieldContainer(view.container, 'format_score')).not.toBeNull();

    view.unmount();
  });

  it('shows an anyOf group target when either driver field is satisfied', async () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[relevanceField, accuracyField, anyOfFormatField]} />);

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'accuracy_score', '5');

    expect(fieldContainer(view.container, 'format_score')).not.toBeNull();

    view.unmount();
  });

  it('keeps a json_editor allOf target reactive when a condition is applied after preview input exists', async () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[relevanceField, accuracyField, fixedAnswerField]} />);

    await selectPreviewValue(view.container, 'relevance_score', '1');
    await selectPreviewValue(view.container, 'accuracy_score', '5');
    await typePreviewJson(view.container, 'fixed_answer', '{"note":"draft"}');

    view.rerender(
      <SchemaFormilyPreviewPanel schemaFields={[relevanceField, accuracyField, fixedAnswerAllOfField]} />,
    );
    await flushPreviewDebounce();

    expect(fieldContainer(view.container, 'fixed_answer')).toBeNull();

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'fixed_answer')).not.toBeNull();

    view.unmount();
  });

  it('keeps atomic visibleWhen preview behavior reactive', async () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[relevanceField, atomicFormatField, accuracyField]} />);

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'format_score')).not.toBeNull();

    view.unmount();
  });

  it('keeps requiredWhen reactions active after visible fields are prefiltered', async () => {
    const view = renderClient(
      <SchemaFormilyPreviewPanel schemaFields={[relevanceField, formatRequiredWhenRelevanceIsFive, accuracyField]} />,
    );

    expect(fieldContainer(view.container, 'format_score')?.textContent).not.toContain('必填');

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'format_score')?.textContent).toContain('必填');

    view.unmount();
  });
});
