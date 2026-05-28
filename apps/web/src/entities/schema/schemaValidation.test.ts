import { describe, expect, it } from 'vitest';
import type { LinkageAtomicCondition, LinkageConditionGroup, SchemaDocument, SchemaField, SchemaFieldType } from './schemaTypes';
import { validateSchemaForUI } from './schemaValidation';

function field(stableId: string, type: SchemaFieldType, overrides: Partial<SchemaField> = {}): SchemaField {
  const base: SchemaField = {
    stableId,
    label: `Label ${stableId}`,
    type,
  };

  if (type === 'single_select' || type === 'multi_select') {
    base.options = [{ label: 'Option', value: 'option' }];
  }
  if (type === 'nested_object') {
    base.children = [field(`${stableId}-child`, 'text')];
  }

  return { ...base, ...overrides };
}

function document(fields: SchemaField[]): SchemaDocument {
  return { fields };
}

function atomic(fieldId: string, op: LinkageAtomicCondition['op'], value?: unknown): LinkageAtomicCondition {
  return { field: fieldId, op, value };
}

function anyOf(conditions: LinkageAtomicCondition[]): LinkageConditionGroup {
  return { anyOf: conditions };
}

function expectSingleError(schema: SchemaDocument, fieldPath: string, reason: string) {
  expect(validateSchemaForUI(schema)).toEqual([{ fieldPath, stableId: expect.any(String), reason }]);
}

describe('validateSchemaForUI linkage DSL validation', () => {
  it('accepts valid visibleWhen and requiredWhen conditions', () => {
    expect(
      validateSchemaForUI(
        document([
          field('type', 'single_select'),
          field('details', 'text', {
            visibleWhen: anyOf([atomic('type', 'eq', 'other')]),
            requiredWhen: atomic('type', 'in', ['other', 'manual']),
          }),
        ]),
      ),
    ).toEqual([]);
  });

  it('rejects linkage reference to missing field', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: atomic('missing', 'eq', 'x') })]),
      'fields[1].visibleWhen.field',
      '联动条件引用的字段不存在',
    );
  });

  it('rejects linkage self-reference', () => {
    expectSingleError(
      document([field('details', 'text', { requiredWhen: atomic('details', 'notEmpty') })]),
      'fields[0].requiredWhen.field',
      '联动条件不能引用自身',
    );
  });

  it('rejects linkage dependency cycles', () => {
    expectSingleError(
      document([
        field('a', 'text', { visibleWhen: atomic('b', 'eq', 'yes') }),
        field('b', 'text', { requiredWhen: atomic('a', 'eq', 'yes') }),
      ]),
      'fields[0].visibleWhen',
      '联动条件存在循环依赖',
    );
  });

  it('rejects empty linkage group', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: anyOf([]) })]),
      'fields[1].visibleWhen.anyOf',
      '联动条件分组至少需要一个条件',
    );
  });

  it('rejects linkage group with both allOf and anyOf', () => {
    expectSingleError(
      document([
        field('type', 'text'),
        field('details', 'text', {
          visibleWhen: {
            allOf: [atomic('type', 'eq', 'a')],
            anyOf: [atomic('type', 'eq', 'b')],
          },
        }),
      ]),
      'fields[1].visibleWhen',
      '联动条件分组必须且只能设置 allOf 或 anyOf',
    );
  });

  it('rejects atomic condition without field or op', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: { field: '', op: undefined as never, value: 'x' } })]),
      'fields[1].visibleWhen',
      '联动条件必须包含 field 和 op',
    );
  });

  it('rejects empty operator with value', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: atomic('type', 'empty', 'x') })]),
      'fields[1].visibleWhen.value',
      'empty/notEmpty 不应设置 value',
    );
  });

  it('rejects scalar operator without scalar value', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: atomic('type', 'eq', ['x']) })]),
      'fields[1].visibleWhen.value',
      '联动操作符需要标量 value',
    );
  });

  it('rejects membership operator without array value', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: atomic('type', 'in', 'x') })]),
      'fields[1].visibleWhen.value',
      '联动操作符需要数组 value',
    );
  });

  it('rejects numeric comparison against non-number fields', () => {
    expectSingleError(
      document([field('type', 'text'), field('details', 'text', { visibleWhen: atomic('type', 'gt', 5) })]),
      'fields[1].visibleWhen.field',
      '数值比较只能引用数字字段',
    );
  });
});
