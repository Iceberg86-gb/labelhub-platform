import type { ReactNode } from 'react';
import { useState } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import { renderClient } from '../../labeling/formily/__tests__/renderClient';
import { ShowItemFieldEditor } from './ShowItemFieldEditor';

vi.mock('@douyinfe/semi-ui', () => ({
  AutoComplete: ({
    'aria-label': ariaLabel,
    data,
    emptyContent,
    onChange,
    placeholder,
    renderItem,
    value,
  }: {
    'aria-label'?: string;
    data?: Array<{ value: string; label: ReactNode; typeLabel?: string }>;
    emptyContent?: ReactNode;
    onChange?: (value: string) => void;
    placeholder?: string;
    renderItem?: (option: { value: string; label: ReactNode; typeLabel?: string }) => ReactNode;
    value?: string;
  }) => (
    <div>
      <input
        aria-label={ariaLabel}
        placeholder={placeholder}
        value={value ?? ''}
        onChange={(event) => onChange?.(event.currentTarget.value)}
      />
      <div>
        {(data ?? []).map((item) => (
          <div key={item.value}>{renderItem ? renderItem(item) : item.label}</div>
        ))}
        {data?.length === 0 ? emptyContent : null}
      </div>
    </div>
  ),
  Input: ({
    onChange,
    value,
  }: {
    onChange?: (value: string) => void;
    value?: string;
  }) => <input value={value ?? ''} onChange={(event) => onChange?.(event.currentTarget.value)} />,
  TextArea: ({
    onChange,
    value,
  }: {
    onChange?: (value: string) => void;
    value?: string;
  }) => <textarea value={value ?? ''} onChange={(event) => onChange?.(event.currentTarget.value)} />,
  Typography: {
    Text({ children, type }: { children?: ReactNode; type?: string }) {
      return <span data-type={type}>{children}</span>;
    },
  },
}));

const showItemField: SchemaField = {
  stableId: 'prompt_display',
  label: '题目',
  type: 'show_item',
  content: '题面',
  sourcePath: '',
};

function setNativeValue(element: HTMLInputElement, value: string) {
  const descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value');
  descriptor?.set?.call(element, value);
}

describe('ShowItemFieldEditor sourcePath suggestions', () => {
  it('renders dataset sourcePath options while keeping free input fallback', () => {
    const changes: SchemaField[] = [];
    const view = renderClient(
      <ShowItemEditorHarness changes={changes} />,
    );

    expect(view.text()).toContain('question.text');
    expect(view.text()).toContain('string');
    expect(view.text()).toContain('items.0.title');

    const input = view.container.querySelector('[aria-label="源数据路径"]') as HTMLInputElement;
    act(() => {
      setNativeValue(input, 'custom.missing');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(changes.at(-1)?.sourcePath).toBe('custom.missing');
    expect(view.text()).toContain('未在数据集字段中找到,请确认拼写');
    view.unmount();
  });
});

function ShowItemEditorHarness({ changes }: { changes: SchemaField[] }) {
  const [field, setField] = useState(showItemField);
  return (
    <ShowItemFieldEditor
      field={field}
      availableFields={[]}
      errors={[]}
      errorsByField={new Map()}
      selectedStableId="prompt_display"
      onSelect={() => {}}
      onDelete={() => {}}
      onChange={(nextField) => {
        changes.push(nextField);
        setField(nextField);
      }}
      sourcePathOptions={[
        { value: 'question.text', typeLabel: 'string' },
        { value: 'items.0.title', typeLabel: 'string' },
      ]}
    />
  );
}
