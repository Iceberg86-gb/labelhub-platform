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

  it('does not parse or overwrite advanced group conditions', () => {
    const onChange = vi.fn();
    const target = field('details', '详情', 'text', {
      visibleWhen: { allOf: [{ field: 'driver', op: 'eq', value: 'show' }] },
    });
    const view = renderClient(
      <LinkageConditionBuilder field={target} availableFields={schemaFields} onChange={onChange} />,
    );

    expect(isLinkageConditionGroup(target.visibleWhen)).toBe(true);
    expect(view.text()).toContain('此条件为高级分组,请使用下方 JSON 编辑');

    const fieldSelect = view.container.querySelector('[aria-label="visibleWhen 条件字段"]') as HTMLSelectElement;
    expect(fieldSelect.disabled).toBe(true);
    expect(onChange).not.toHaveBeenCalled();
    view.unmount();
  });
});
