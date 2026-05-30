import { describe, expect, it } from 'vitest';
import type { SchemaField } from '../../entities/schema/schemaTypes';
import {
  CANVAS_ROOT_DROP_ID,
  designerDropIdFromTarget,
  resolveDesignerDragEnd,
} from './designerDragModel';

describe('designer nested drag model', () => {
  it('resolves palette drops into nested object and tab children', () => {
    const fields = makeFields();

    expect(resolveDesignerDragEnd(fields, { activeId: 'palette:text', overId: 'nested:group' })).toMatchObject({
      kind: 'add',
      fieldType: 'text',
      parentStableId: 'group',
      index: 1,
    });
    expect(resolveDesignerDragEnd(fields, { activeId: 'palette:rich_text', overId: 'tab:tabs:tab-a' })).toMatchObject({
      kind: 'add',
      fieldType: 'rich_text',
      parentStableId: 'tab:tabs:tab-a',
      index: 1,
    });
  });

  it('keeps root palette drop and root reorder behavior', () => {
    const fields = makeFields();

    expect(resolveDesignerDragEnd(fields, { activeId: 'palette:number', overId: CANVAS_ROOT_DROP_ID })).toMatchObject({
      kind: 'add',
      fieldType: 'number',
      parentStableId: undefined,
      index: 4,
    });

    const result = resolveDesignerDragEnd(fields, { activeId: 'title', overId: 'group' });
    expect(result.kind).toBe('change');
    expect(result.kind === 'change' ? result.fields.map((field) => field.stableId) : []).toEqual(['group', 'title', 'other', 'tabs']);
  });

  it('moves fields between root, nested objects, and tab children without cloning stable ids', () => {
    const fields = makeFields();

    const topToNested = resolveDesignerDragEnd(fields, { activeId: 'title', overId: 'nested:group' });
    expect(topToNested.kind).toBe('change');
    expect(stableIdsAt(topToNested.kind === 'change' ? topToNested.fields : [], { kind: 'root' })).toEqual(['group', 'other', 'tabs']);
    expect(stableIdsAt(topToNested.kind === 'change' ? topToNested.fields : [], { kind: 'nested', stableId: 'group' })).toEqual(['child', 'title']);

    const nestedToRoot = resolveDesignerDragEnd(fields, { activeId: 'child', overId: 'title' });
    expect(nestedToRoot.kind).toBe('change');
    expect(stableIdsAt(nestedToRoot.kind === 'change' ? nestedToRoot.fields : [], { kind: 'root' })).toEqual(['child', 'title', 'group', 'other', 'tabs']);
    expect(stableIdsAt(nestedToRoot.kind === 'change' ? nestedToRoot.fields : [], { kind: 'nested', stableId: 'group' })).toEqual([]);

    const nestedToNested = resolveDesignerDragEnd(fields, { activeId: 'child', overId: 'nested:other' });
    expect(nestedToNested.kind).toBe('change');
    expect(stableIdsAt(nestedToNested.kind === 'change' ? nestedToNested.fields : [], { kind: 'nested', stableId: 'other' })).toEqual(['other-child', 'child']);
  });

  it('rejects self, descendant, and invalid container moves', () => {
    const fields = makeFields();

    expect(resolveDesignerDragEnd(fields, { activeId: 'group', overId: designerDropIdFromTarget({ kind: 'nested', stableId: 'group' }) })).toMatchObject({
      kind: 'noop',
    });
    expect(resolveDesignerDragEnd(fields, { activeId: 'group', overId: 'child' })).toMatchObject({
      kind: 'noop',
    });
    expect(resolveDesignerDragEnd(fields, { activeId: 'palette:nested_object', overId: 'nested:group' })).toMatchObject({
      kind: 'noop',
    });
    expect(resolveDesignerDragEnd(fields, { activeId: 'palette:tab_container', overId: 'tab:tabs:tab-a' })).toMatchObject({
      kind: 'noop',
    });
    expect(resolveDesignerDragEnd(fields, { activeId: 'tabs', overId: 'nested:group' })).toMatchObject({
      kind: 'noop',
    });
  });
});

function makeFields(): SchemaField[] {
  return [
    { stableId: 'title', label: 'Title', type: 'text' },
    {
      stableId: 'group',
      label: 'Group',
      type: 'nested_object',
      children: [{ stableId: 'child', label: 'Child', type: 'text' }],
    },
    {
      stableId: 'other',
      label: 'Other group',
      type: 'nested_object',
      children: [{ stableId: 'other-child', label: 'Other child', type: 'number' }],
    },
    {
      stableId: 'tabs',
      label: 'Tabs',
      type: 'tab_container',
      tabs: [{ stableId: 'tab-a', label: 'First tab', children: [{ stableId: 'tab-child', label: 'Tab child', type: 'text' }] }],
    },
  ];
}

function stableIdsAt(fields: SchemaField[], target: { kind: 'root' } | { kind: 'nested'; stableId: string }): string[] {
  if (target.kind === 'root') {
    return fields.map((field) => field.stableId);
  }
  return (fields.find((field) => field.stableId === target.stableId)?.children ?? []).map((field) => field.stableId);
}
