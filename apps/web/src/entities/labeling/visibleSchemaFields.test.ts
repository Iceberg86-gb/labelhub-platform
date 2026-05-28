import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../schema/schemaTypes';
import { createVisibleSchemaFieldsSelector, filterVisibleSchemaFields } from './visibleSchemaFields';

function field(overrides: Partial<SchemaField> & Pick<SchemaField, 'stableId' | 'type'>): SchemaField {
  return {
    stableId: overrides.stableId,
    type: overrides.type,
    label: overrides.label ?? overrides.stableId,
    visibleWhen: overrides.visibleWhen,
    children: overrides.children,
  };
}

describe('visible schema field filtering', () => {
  it('omits hidden fields from the shared renderer and submit modal field list', () => {
    const fields = [
      field({ stableId: 'type', type: 'text' }),
      field({
        stableId: 'conditional',
        type: 'text',
        visibleWhen: { field: 'type', op: 'eq', value: 'show' },
      }),
    ];

    expect(filterVisibleSchemaFields(fields, { type: 'hide', conditional: 'kept in payload' }).map((item) => item.stableId)).toEqual(['type']);
  });

  it('recursively omits hidden nested children without mutating payload values', () => {
    const child = field({
      stableId: 'child',
      type: 'text',
      visibleWhen: { field: 'type', op: 'eq', value: 'show' },
    });
    const parent = field({ stableId: 'parent', type: 'nested_object', children: [child] });
    const fields = [field({ stableId: 'type', type: 'text' }), parent];
    const payload = { type: 'hide', parent: { child: 'hidden value' } };

    const visible = filterVisibleSchemaFields(fields, payload);

    expect(visible[1]?.stableId).toBe('parent');
    expect(visible[1]?.children).toEqual([]);
    expect(payload.parent.child).toBe('hidden value');
  });

  it('returns the original schema array when every field remains visible', () => {
    const fields = [field({ stableId: 'always', type: 'text' })];

    expect(filterVisibleSchemaFields(fields, { always: 'value' })).toBe(fields);
  });

  it('keeps a stable filtered reference when the visible stableId shape does not change', () => {
    const selector = createVisibleSchemaFieldsSelector();
    const fields = [
      field({ stableId: 'type', type: 'text' }),
      field({
        stableId: 'conditional',
        type: 'text',
        visibleWhen: { field: 'type', op: 'eq', value: 'show' },
      }),
    ];

    const first = selector(fields, { type: 'hide', conditional: 'first' });
    const second = selector(fields, { type: 'hide', conditional: 'second' });
    const third = selector(fields, { type: 'show', conditional: 'third' });

    expect(first).toBe(second);
    expect(third).not.toBe(first);
    expect(third.map((item) => item.stableId)).toEqual(['type', 'conditional']);
  });
});
