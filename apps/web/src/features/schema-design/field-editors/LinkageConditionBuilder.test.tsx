import type { ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { LinkageAtomicCondition, SchemaField } from '../../../entities/schema/schemaTypes';
import { validateSchemaForUI } from '../../../entities/schema/schemaValidation';
import { renderClient } from '../../labeling/formily/__tests__/renderClient';
import {
  buildAtomicLinkageCondition,
  flattenLinkageCandidateFields,
  isLinkageConditionGroup,
  LinkageConditionBuilder,
} from './LinkageConditionBuilder';

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({
    children,
    disabled,
    onClick,
  }: {
    children?: ReactNode;
    disabled?: boolean;
    onClick?: () => void;
  }) => (
    <button type="button" disabled={disabled} onClick={onClick}>
      {children}
    </button>
  ),
  Typography: {
    Text({ children, className }: { children?: ReactNode; className?: string }) {
      return <span className={className}>{children}</span>;
    },
  },
}));

function field(stableId: string, label: string, type: SchemaField['type'], patch: Partial<SchemaField> = {}): SchemaField {
  return { stableId, label, type, ...patch };
}

const schemaFields: SchemaField[] = [
  field('driver', '驱动字段', 'single_select', {
    options: [{ label: '显示', value: 'show' }, { label: '隐藏', value: 'hide' }],
  }),
  field('score', '评分', 'number'),
  field('details', '详情', 'text'),
  field('group', '嵌套组', 'nested_object', {
    children: [field('nested_driver', '嵌套驱动', 'text')],
  }),
  field('tabs', '多 Tab', 'tab_container', {
    tabs: [
      {
        stableId: 'tab-a',
        label: 'Tab A',
        children: [field('tab_score', 'Tab 分数', 'number')],
      },
    ],
  }),
];

function setNativeValue(element: HTMLInputElement | HTMLSelectElement, value: string) {
  const descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), 'value');
  descriptor?.set?.call(element, value);
}

describe('LinkageConditionBuilder', () => {
  it('flattens nested and tab fields while excluding the edited field itself', () => {
    expect(flattenLinkageCandidateFields(schemaFields, 'details').map((candidate) => candidate.stableId)).toEqual([
      'driver',
      'score',
      'group',
      'nested_driver',
      'tabs',
      'tab_score',
    ]);
  });

  it('builds atomic linkage conditions with op-specific value shapes that pass schema validation', () => {
    expect(buildAtomicLinkageCondition('driver', 'eq', 'show')).toEqual({
      field: 'driver',
      op: 'eq',
      value: 'show',
    });
    expect(buildAtomicLinkageCondition('driver', 'in', 'alpha, beta')).toEqual({
      field: 'driver',
      op: 'in',
      value: ['alpha', 'beta'],
    });
    expect(buildAtomicLinkageCondition('score', 'gt', '3')).toEqual({
      field: 'score',
      op: 'gt',
      value: 3,
    });
    expect(buildAtomicLinkageCondition('driver', 'empty', 'legacy')).toEqual({
      field: 'driver',
      op: 'empty',
    });

    const target = field('target', '目标字段', 'text', {
      visibleWhen: buildAtomicLinkageCondition('driver', 'in', 'alpha,beta'),
      requiredWhen: buildAtomicLinkageCondition('score', 'gte', '2'),
    });

    expect(validateSchemaForUI({ fields: [schemaFields[0], schemaFields[1], target] })).toEqual([]);
  });

  it('limits numeric operators to number field candidates and writes number values', () => {
    const changes: SchemaField[] = [];
    const target = field('details', '详情', 'text');
    const view = renderClient(
      <LinkageConditionBuilder
        field={target}
        availableFields={schemaFields}
        onChange={(nextField) => changes.push(nextField)}
      />,
    );

    const keySelect = view.container.querySelector('[aria-label="联动目标"]') as HTMLSelectElement;
    const fieldSelect = view.container.querySelector('[aria-label="visibleWhen 条件字段"]') as HTMLSelectElement;
    const opSelect = view.container.querySelector('[aria-label="visibleWhen 操作符"]') as HTMLSelectElement;

    act(() => {
      setNativeValue(keySelect, 'visibleWhen');
      keySelect.dispatchEvent(new Event('change', { bubbles: true }));
      setNativeValue(opSelect, 'gt');
      opSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(Array.from(fieldSelect.options).map((option) => option.value).filter(Boolean)).toEqual(['score', 'tab_score']);
    const nextFieldSelect = view.container.querySelector('[aria-label="visibleWhen 条件字段"]') as HTMLSelectElement;
    const valueInput = view.container.querySelector('[aria-label="visibleWhen 条件值"]') as HTMLInputElement;

    act(() => {
      setNativeValue(nextFieldSelect, 'score');
      nextFieldSelect.dispatchEvent(new Event('change', { bubbles: true }));
      setNativeValue(valueInput, '5');
      valueInput.dispatchEvent(new Event('input', { bubbles: true }));
      valueInput.dispatchEvent(new Event('change', { bubbles: true }));
    });

    const applyButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '应用条件');
    expect(applyButton).toBeTruthy();
    expect(applyButton?.disabled).toBe(false);
    act(() => {
      applyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(changes.at(-1)?.visibleWhen).toEqual({ field: 'score', op: 'gt', value: 5 });
    view.unmount();
  });

  it('edits group conditions visually and writes a one-layer mutually exclusive group', () => {
    const changes: SchemaField[] = [];
    const target = field('details', '详情', 'text', {
      visibleWhen: { allOf: [{ field: 'driver', op: 'eq', value: 'show' }] },
    });
    const view = renderClient(
      <LinkageConditionBuilder
        field={target}
        availableFields={schemaFields}
        onChange={(nextField) => changes.push(nextField)}
      />,
    );

    expect(isLinkageConditionGroup(target.visibleWhen)).toBe(true);
    expect(view.text()).not.toContain('此条件为高级分组,请使用下方 JSON 编辑');
    expect(view.text()).toContain('条件组逻辑');

    const operatorSelect = view.container.querySelector('[aria-label="条件组逻辑"]') as HTMLSelectElement;
    const addButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '添加条件');
    expect(operatorSelect.value).toBe('allOf');
    expect(addButton).toBeTruthy();

    act(() => {
      setNativeValue(operatorSelect, 'anyOf');
      operatorSelect.dispatchEvent(new Event('change', { bubbles: true }));
      addButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    const row2FieldSelect = view.container.querySelector('[aria-label="visibleWhen 第2条条件字段"]') as HTMLSelectElement;
    const row2OpSelect = view.container.querySelector('[aria-label="visibleWhen 第2条操作符"]') as HTMLSelectElement;
    const row2ValueInput = view.container.querySelector('[aria-label="visibleWhen 第2条条件值"]') as HTMLInputElement;

    act(() => {
      setNativeValue(row2OpSelect, 'gt');
      row2OpSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });
    expect(Array.from(row2FieldSelect.options).map((option) => option.value).filter(Boolean)).toEqual(['score', 'tab_score']);

    act(() => {
      setNativeValue(row2FieldSelect, 'score');
      row2FieldSelect.dispatchEvent(new Event('change', { bubbles: true }));
      setNativeValue(row2ValueInput, '5');
      row2ValueInput.dispatchEvent(new Event('input', { bubbles: true }));
      row2ValueInput.dispatchEvent(new Event('change', { bubbles: true }));
    });

    const applyButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '应用条件');
    act(() => {
      applyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(changes.at(-1)?.visibleWhen).toEqual({
      anyOf: [
        { field: 'driver', op: 'eq', value: 'show' },
        { field: 'score', op: 'gt', value: 5 },
      ],
    });
    expect('allOf' in (changes.at(-1)?.visibleWhen ?? {})).toBe(false);
    expect(validateSchemaForUI({ fields: [schemaFields[0], schemaFields[1], changes.at(-1) as SchemaField] })).toEqual([]);
    view.unmount();
  });

  it('keeps group drafts local until apply and preserves op-specific value rules', () => {
    const changes: SchemaField[] = [];
    const target = field('details', '详情', 'text');
    const view = renderClient(
      <LinkageConditionBuilder
        field={target}
        availableFields={schemaFields}
        onChange={(nextField) => changes.push(nextField)}
      />,
    );

    const modeSelect = view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement;
    act(() => {
      setNativeValue(modeSelect, 'group');
      modeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });

    const deleteButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '删除');
    expect(deleteButton?.disabled).toBe(true);

    const row1FieldSelect = view.container.querySelector('[aria-label="visibleWhen 第1条条件字段"]') as HTMLSelectElement;
    const row1OpSelect = view.container.querySelector('[aria-label="visibleWhen 第1条操作符"]') as HTMLSelectElement;
    act(() => {
      setNativeValue(row1OpSelect, 'empty');
      row1OpSelect.dispatchEvent(new Event('change', { bubbles: true }));
      setNativeValue(row1FieldSelect, 'driver');
      row1FieldSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(view.container.querySelector('[aria-label="visibleWhen 第1条条件值"]')).toBeNull();
    expect(changes).toEqual([]);

    const applyButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '应用条件');
    act(() => {
      applyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(changes.at(-1)?.visibleWhen).toEqual({ allOf: [{ field: 'driver', op: 'empty' }] });
    expect(validateSchemaForUI({ fields: [schemaFields[0], changes.at(-1) as SchemaField] })).toEqual([]);
    view.unmount();
  });

  it('upgrades a single atomic draft into a group when adding another condition', () => {
    const target = field('details', '详情', 'text', {
      visibleWhen: { field: 'driver', op: 'eq', value: 'show' },
    });
    const view = renderClient(
      <LinkageConditionBuilder field={target} availableFields={schemaFields} onChange={vi.fn()} />,
    );

    const addButton = Array.from(view.container.querySelectorAll('button')).find((button) => button.textContent === '添加条件');
    act(() => {
      addButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect((view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement).value).toBe('group');
    expect((view.container.querySelector('[aria-label="条件组逻辑"]') as HTMLSelectElement).value).toBe('allOf');
    expect((view.container.querySelector('[aria-label="visibleWhen 第1条条件字段"]') as HTMLSelectElement).value).toBe('driver');
    expect((view.container.querySelector('[aria-label="visibleWhen 第1条操作符"]') as HTMLSelectElement).value).toBe('eq');
    expect((view.container.querySelector('[aria-label="visibleWhen 第1条条件值"]') as HTMLInputElement).value).toBe('show');
    expect((view.container.querySelector('[aria-label="visibleWhen 第2条条件字段"]') as HTMLSelectElement).value).toBe('');
    view.unmount();
  });

  it('requires confirmation before reducing multi-condition groups to a single atomic condition', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const onChange = vi.fn();
    const target = field('details', '详情', 'text', {
      visibleWhen: {
        anyOf: [
          { field: 'driver', op: 'eq', value: 'show' },
          { field: 'score', op: 'gt', value: 3 },
        ],
      },
    });
    const view = renderClient(
      <LinkageConditionBuilder field={target} availableFields={schemaFields} onChange={onChange} />,
    );

    const modeSelect = view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement;
    act(() => {
      setNativeValue(modeSelect, 'atomic');
      modeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(confirmSpy).toHaveBeenCalledWith(
      '将从"任一满足"条件组切回单条件,并只保留第一条条件;其他可触发显示/必填的条件会被移除。',
    );
    expect(onChange).not.toHaveBeenCalled();
    expect((view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement).value).toBe('group');

    confirmSpy.mockReturnValue(true);
    act(() => {
      setNativeValue(view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement, 'atomic');
      view.container.querySelector('[aria-label="联动模式"]')?.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(onChange).toHaveBeenCalledWith({
      ...target,
      visibleWhen: { field: 'driver', op: 'eq', value: 'show' },
    });
    view.unmount();
    confirmSpy.mockRestore();
  });

  it('uses allOf-specific confirmation wording when reducing allOf groups', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const target = field('details', '详情', 'text', {
      visibleWhen: {
        allOf: [
          { field: 'driver', op: 'eq', value: 'show' },
          { field: 'score', op: 'gt', value: 3 },
        ],
      },
    });
    const view = renderClient(
      <LinkageConditionBuilder field={target} availableFields={schemaFields} onChange={vi.fn()} />,
    );

    const modeSelect = view.container.querySelector('[aria-label="联动模式"]') as HTMLSelectElement;
    act(() => {
      setNativeValue(modeSelect, 'atomic');
      modeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(confirmSpy).toHaveBeenCalledWith('将从"全部满足"条件组切回单条件,并只保留第一条条件。');
    view.unmount();
    confirmSpy.mockRestore();
  });
});
