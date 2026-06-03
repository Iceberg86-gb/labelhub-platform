import type { ReactNode } from 'react';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import { SchemaFormilyPreviewPanel } from '../labeling/formily/preview/SchemaFormilyPreviewPanel';
import { renderClient } from '../labeling/formily/__tests__/renderClient';

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

  return {
    Select,
    Tag({ children }: { children?: ReactNode }) {
      return <span>{children}</span>;
    },
    Typography: {
      Text({ children }: { children?: ReactNode }) {
        return <span>{children}</span>;
      },
    },
  };
});

function field(stableId: string, label: string, type: SchemaField['type'], patch: Partial<SchemaField> = {}): SchemaField {
  return {
    stableId,
    label,
    type,
    ...patch,
  };
}

function selectField(stableId: string, label: string, patch: Partial<SchemaField> = {}): SchemaField {
  return field(stableId, label, 'single_select', {
    options: [
      { label: '1', value: '1' },
      { label: '2', value: '2' },
      { label: '3', value: '3' },
      { label: '4', value: '4' },
      { label: '5', value: '5' },
    ],
    validation: { required: true },
    ...patch,
  });
}

const relevanceField = selectField('relevance_score', '相关性');
const accuracyField = selectField('accuracy_score', '准确性');
const formatField = selectField('format_score', '表达与格式');
const formatFieldVisibleWhenRelevanceIsFive = selectField('format_score', '表达与格式', {
  visibleWhen: { field: 'relevance_score', op: 'eq', value: '5' },
});

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

async function selectPreviewValue(container: HTMLElement, stableId: string, value: string) {
  const select = selectFor(container, stableId);
  await act(async () => {
    select.value = value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
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

describe('schema designer preview linkage integration', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('hides and shows an atomic visibleWhen target when the preview driver field changes', async () => {
    const view = renderClient(
      <SchemaFormilyPreviewPanel schemaFields={[relevanceField, formatFieldVisibleWhenRelevanceIsFive, accuracyField]} />,
    );

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'format_score')).not.toBeNull();
    expect(selectFor(view.container, 'format_score').value).toBe('');

    view.unmount();
  });

  it('keeps dirty preview values coherent when an atomic visibleWhen is applied after user input exists', async () => {
    const view = renderClient(<SchemaFormilyPreviewPanel schemaFields={[relevanceField, formatField, accuracyField]} />);

    await selectPreviewValue(view.container, 'relevance_score', '1');
    await selectPreviewValue(view.container, 'format_score', '2');
    await selectPreviewValue(view.container, 'accuracy_score', '3');

    view.rerender(
      <SchemaFormilyPreviewPanel schemaFields={[relevanceField, formatFieldVisibleWhenRelevanceIsFive, accuracyField]} />,
    );
    await flushPreviewDebounce();

    expect(fieldContainer(view.container, 'format_score')).toBeNull();

    await selectPreviewValue(view.container, 'relevance_score', '5');

    expect(fieldContainer(view.container, 'format_score')).not.toBeNull();

    view.unmount();
  });
});
