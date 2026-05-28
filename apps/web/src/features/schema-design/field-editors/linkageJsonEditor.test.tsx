import { describe, expect, it, vi } from 'vitest';
import { renderToString } from 'react-dom/server';
import type { ReactNode } from 'react';
import type { SchemaField } from '../../../entities/schema/schemaTypes';
import { FieldEditor } from './FieldEditor';
import {
  applyLinkageConditionPatch,
  formatLinkageConditionForEditor,
  parseLinkageConditionInput,
} from './LinkageJsonEditor';

vi.mock('@douyinfe/semi-ui', () => ({
  Input({ value, onChange }: { value?: string; onChange?: (value: string) => void }) {
    return <input value={value ?? ''} onChange={(event) => onChange?.(event.currentTarget.value)} readOnly />;
  },
  InputNumber({ value, onChange }: { value?: number; onChange?: (value: number) => void }) {
    return <input type="number" value={value ?? ''} onChange={(event) => onChange?.(Number(event.currentTarget.value))} readOnly />;
  },
  Switch({ checked, onChange }: { checked?: boolean; onChange?: (checked: boolean) => void }) {
    return <input type="checkbox" checked={Boolean(checked)} onChange={(event) => onChange?.(event.currentTarget.checked)} readOnly />;
  },
  Typography: {
    Text({ children }: { children?: ReactNode }) {
      return <span>{children}</span>;
    },
  },
}));

function textField(overrides: Partial<SchemaField> = {}): SchemaField {
  return {
    stableId: 'details',
    type: 'text',
    label: '详情',
    ...overrides,
  };
}

describe('LinkageJsonEditor', () => {
  it('parses valid JSON condition input', () => {
    const result = parseLinkageConditionInput('{"field":"type","op":"eq","value":"other"}');

    expect(result).toEqual({
      ok: true,
      value: { field: 'type', op: 'eq', value: 'other' },
    });
  });

  it('rejects invalid JSON without producing a patch value', () => {
    const result = parseLinkageConditionInput('{"field":');

    expect(result).toEqual({ ok: false, reason: 'JSON 格式错误' });
  });

  it('formats missing conditions as an empty editor value', () => {
    expect(formatLinkageConditionForEditor(undefined)).toBe('');
  });

  it('writes visibleWhen from valid JSON', () => {
    const result = applyLinkageConditionPatch(textField(), 'visibleWhen', '{"field":"type","op":"eq","value":"show"}');

    expect(result).toEqual({
      ok: true,
      field: {
        stableId: 'details',
        type: 'text',
        label: '详情',
        visibleWhen: { field: 'type', op: 'eq', value: 'show' },
      },
    });
  });

  it('writes requiredWhen from valid JSON', () => {
    const result = applyLinkageConditionPatch(textField(), 'requiredWhen', '{"field":"type","op":"eq","value":"other"}');

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.field.requiredWhen).toEqual({ field: 'type', op: 'eq', value: 'other' });
    }
  });

  it('clears linkage condition when input is blank', () => {
    const field = textField({
      visibleWhen: { field: 'type', op: 'eq', value: 'show' },
    });

    const result = applyLinkageConditionPatch(field, 'visibleWhen', '   ');

    expect(result).toEqual({
      ok: true,
      field: {
        stableId: 'details',
        type: 'text',
        label: '详情',
        visibleWhen: undefined,
      },
    });
  });

  it('renders the advanced linkage JSON section from the shared FieldEditor wrapper', () => {
    const html = renderToString(
      <FieldEditor
        field={textField()}
        onChange={() => undefined}
        errors={[]}
        errorsByField={new Map()}
        selectedStableId={null}
        onSelect={() => undefined}
        onDelete={() => undefined}
      />,
    );

    expect(html).toContain('高级联动 JSON');
    expect(html).toContain('visibleWhen');
    expect(html).toContain('requiredWhen');
  });
});
