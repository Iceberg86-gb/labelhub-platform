import { describe, expect, it } from 'vitest';
import type { LinkageAtomicCondition, LinkageConditionGroup, LinkageConditionOp } from '../schema/schemaTypes';
import { evaluateLinkageCondition } from './linkageEvaluator';

function atomic(field: string, op: LinkageConditionOp, value?: unknown): LinkageAtomicCondition {
  return value === undefined ? { field, op } : { field, op, value };
}

function allOf(...conditions: LinkageAtomicCondition[]): LinkageConditionGroup {
  return { allOf: conditions };
}

function anyOf(...conditions: LinkageAtomicCondition[]): LinkageConditionGroup {
  return { anyOf: conditions };
}

describe('frontend linkage evaluator', () => {
  it('evaluates scalar equality without cross-type coercion', () => {
    const values = new Map<string, unknown>([
      ['textNumber', '1'],
      ['number', 1],
      ['bool', true],
    ]);

    expect(evaluateLinkageCondition(atomic('textNumber', 'eq', '1'), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('number', 'eq', 1.0), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('textNumber', 'eq', 1), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('bool', 'neq', false), values)).toBe(true);
  });

  it('treats empty driver values as false for non-empty operators', () => {
    const values = { blank: '', emptyList: [] };

    expect(evaluateLinkageCondition(atomic('missing', 'eq', 'x'), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('missing', 'neq', 'x'), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('blank', 'in', ['x']), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('emptyList', 'notIn', ['x']), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('missing', 'gt', 1), values)).toBe(false);
  });

  it('reuses P3a empty semantics', () => {
    const values = {
      blank: '',
      spaces: '   ',
      emptyList: [],
      emptyMap: {},
      text: 'x',
    };

    expect(evaluateLinkageCondition(atomic('missing', 'empty'), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('blank', 'empty'), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('emptyList', 'empty'), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('emptyMap', 'empty'), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('spaces', 'empty'), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('text', 'notEmpty'), values)).toBe(true);
  });

  it('evaluates membership for scalars and arrays by intersection', () => {
    const values = {
      single: 'manual',
      multi: ['alpha', 'beta'],
    };

    expect(evaluateLinkageCondition(atomic('single', 'in', ['manual', 'other']), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('single', 'notIn', ['other']), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('multi', 'in', ['beta', 'gamma']), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('multi', 'notIn', ['gamma']), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('multi', 'notIn', ['alpha']), values)).toBe(false);
  });

  it('evaluates finite numeric comparisons and rejects non-numeric values', () => {
    const values = {
      count: 10,
      decimal: 1.5,
      text: '10',
    };

    expect(evaluateLinkageCondition(atomic('count', 'gt', 5), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('count', 'gte', 10.0), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('decimal', 'lt', 2), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('decimal', 'lte', 1.5), values)).toBe(true);
    expect(evaluateLinkageCondition(atomic('text', 'gt', 5), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('count', 'gt', '5'), values)).toBe(false);
    expect(evaluateLinkageCondition(atomic('count', 'lt', Number.POSITIVE_INFINITY), values)).toBe(false);
  });

  it('evaluates one-level allOf and anyOf groups', () => {
    const values = { type: 'other', score: 7 };

    expect(evaluateLinkageCondition(allOf(atomic('type', 'eq', 'other'), atomic('score', 'gte', 5)), values)).toBe(true);
    expect(evaluateLinkageCondition(allOf(atomic('type', 'eq', 'other'), atomic('score', 'lt', 5)), values)).toBe(false);
    expect(evaluateLinkageCondition(anyOf(atomic('type', 'eq', 'manual'), atomic('score', 'gte', 5)), values)).toBe(true);
  });

  it('returns false for null or malformed conditions', () => {
    expect(evaluateLinkageCondition(null, { field: 'value' })).toBe(false);
    expect(evaluateLinkageCondition({} as LinkageConditionGroup, { field: 'value' })).toBe(false);
    expect(evaluateLinkageCondition({ field: 'field' } as LinkageAtomicCondition, { field: 'value' })).toBe(false);
    expect(evaluateLinkageCondition(atomic('field', 'neq'), { field: 'value' })).toBe(false);
    expect(evaluateLinkageCondition(atomic('field', 'notIn', 'not-array'), { field: 'value' })).toBe(false);
    expect(evaluateLinkageCondition(atomic('object', 'neq', 'value'), { object: { nested: 'value' } })).toBe(false);
  });
});
