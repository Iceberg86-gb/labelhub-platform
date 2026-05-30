import type { SchemaField, SchemaFieldType } from '../../entities/schema/schemaTypes';
import { SCHEMA_FIELD_TYPES } from '../../entities/schema/schemaTypes';

export const CANVAS_ROOT_DROP_ID = 'canvas:root';
export const LEGACY_CANVAS_DROP_ID = 'schema-designer-canvas-dropzone';
export const PALETTE_PREFIX = 'palette:';

export type DesignerDropTarget =
  | { kind: 'root' }
  | { kind: 'nested'; stableId: string }
  | { kind: 'tab'; containerStableId: string; tabStableId: string };

type FieldLocation = {
  field: SchemaField;
  index: number;
  parent: DesignerDropTarget;
  ancestorStableIds: string[];
};

type DropLocation = {
  target: DesignerDropTarget;
  index: number;
  ancestorStableIds: string[];
};

export type DesignerDragResolution =
  | {
    kind: 'add';
    fieldType: SchemaFieldType;
    parentStableId?: string;
    index: number;
  }
  | {
    kind: 'change';
    fields: SchemaField[];
  }
  | {
    kind: 'noop';
    reason: string;
  };

export function designerDropIdFromTarget(target: DesignerDropTarget): string {
  if (target.kind === 'root') {
    return CANVAS_ROOT_DROP_ID;
  }
  if (target.kind === 'nested') {
    return `nested:${target.stableId}`;
  }
  return `tab:${target.containerStableId}:${target.tabStableId}`;
}

export function designerTargetFromParentStableId(parentStableId?: string | null): DesignerDropTarget {
  if (!parentStableId) {
    return { kind: 'root' };
  }

  const parsed = parseDesignerDropId(parentStableId);
  if (parsed?.kind === 'tab') {
    return parsed;
  }
  if (parsed?.kind === 'nested') {
    return parsed;
  }

  return { kind: 'nested', stableId: parentStableId };
}

export function parentStableIdFromDesignerTarget(target: DesignerDropTarget): string | undefined {
  if (target.kind === 'root') {
    return undefined;
  }
  if (target.kind === 'nested') {
    return target.stableId;
  }
  return designerDropIdFromTarget(target);
}

export function parseDesignerDropId(id: string | null | undefined): DesignerDropTarget | null {
  if (!id) {
    return null;
  }
  if (id === CANVAS_ROOT_DROP_ID || id === LEGACY_CANVAS_DROP_ID) {
    return { kind: 'root' };
  }
  if (id.startsWith('nested:')) {
    const stableId = id.slice('nested:'.length);
    return stableId ? { kind: 'nested', stableId } : null;
  }
  if (id.startsWith('tab:')) {
    const [, containerStableId, tabStableId] = id.split(':');
    return containerStableId && tabStableId ? { kind: 'tab', containerStableId, tabStableId } : null;
  }
  return null;
}

export function paletteTypeFromDesignerId(id: string | null | undefined): SchemaFieldType | null {
  if (!id?.startsWith(PALETTE_PREFIX)) {
    return null;
  }
  const type = id.slice(PALETTE_PREFIX.length);
  return SCHEMA_FIELD_TYPES.includes(type as SchemaFieldType) ? type as SchemaFieldType : null;
}

export function canPlaceFieldTypeInDesignerTarget(type: SchemaFieldType, target: DesignerDropTarget): boolean {
  if (target.kind === 'root') {
    return true;
  }
  if (target.kind === 'nested') {
    return type !== 'nested_object' && type !== 'tab_container';
  }
  return type !== 'tab_container';
}

export function insertFieldIntoDesignerTarget(
  fields: SchemaField[],
  target: DesignerDropTarget,
  field: SchemaField,
  index?: number,
): SchemaField[] | null {
  if (!canPlaceFieldTypeInDesignerTarget(field.type, target)) {
    return null;
  }
  return updateDesignerTargetFields(fields, target, (children) => insertAt(children, field, index));
}

export function resolveDesignerDragEnd(
  fields: SchemaField[],
  { activeId, overId }: { activeId: string; overId: string | null | undefined },
): DesignerDragResolution {
  const paletteType = paletteTypeFromDesignerId(activeId);
  if (paletteType) {
    const target = resolveDropLocation(fields, overId ?? CANVAS_ROOT_DROP_ID);
    if (!target) {
      return { kind: 'noop', reason: 'unknown palette target' };
    }
    if (!canPlaceFieldTypeInDesignerTarget(paletteType, target.target)) {
      return { kind: 'noop', reason: 'field type is not allowed in target' };
    }
    return {
      kind: 'add',
      fieldType: paletteType,
      parentStableId: parentStableIdFromDesignerTarget(target.target),
      index: target.index,
    };
  }

  if (!overId || activeId === overId) {
    return { kind: 'noop', reason: 'missing or unchanged target' };
  }

  const source = findFieldLocation(fields, activeId);
  const target = resolveDropLocation(fields, overId);
  if (!source || !target) {
    return { kind: 'noop', reason: 'unknown field or target' };
  }
  if (target.ancestorStableIds.includes(activeId)) {
    return { kind: 'noop', reason: 'cannot move a field into itself or its descendants' };
  }
  if (!canPlaceFieldTypeInDesignerTarget(source.field.type, target.target)) {
    return { kind: 'noop', reason: 'field type is not allowed in target' };
  }

  if (sameDesignerTarget(source.parent, target.target)) {
    const current = fieldsAtDesignerTarget(fields, source.parent);
    if (!current) {
      return { kind: 'noop', reason: 'source container not found' };
    }
    const nextContainerFields = moveItem(current, source.index, target.index);
    const nextFields = updateDesignerTargetFields(fields, source.parent, () => nextContainerFields);
    return nextFields ? { kind: 'change', fields: nextFields } : { kind: 'noop', reason: 'source container not changed' };
  }

  const withoutSource = removeFromDesignerTarget(fields, source.parent, activeId);
  if (!withoutSource) {
    return { kind: 'noop', reason: 'source removal failed' };
  }
  const nextFields = insertFieldIntoDesignerTarget(withoutSource, target.target, source.field, target.index);
  return nextFields ? { kind: 'change', fields: nextFields } : { kind: 'noop', reason: 'target insertion failed' };
}

function resolveDropLocation(fields: SchemaField[], overId: string | null | undefined): DropLocation | null {
  const parsed = parseDesignerDropId(overId);
  if (parsed) {
    if (parsed.kind === 'root') {
      return { target: parsed, index: fields.length, ancestorStableIds: [] };
    }
    if (parsed.kind === 'nested') {
      const owner = findFieldLocation(fields, parsed.stableId);
      if (!owner || owner.field.type !== 'nested_object') {
        return null;
      }
      return {
        target: parsed,
        index: (owner.field.children ?? []).length,
        ancestorStableIds: [...owner.ancestorStableIds, owner.field.stableId],
      };
    }
    const owner = findFieldLocation(fields, parsed.containerStableId);
    if (!owner || owner.field.type !== 'tab_container') {
      return null;
    }
    const tab = owner.field.tabs?.find((candidate) => candidate.stableId === parsed.tabStableId);
    if (!tab) {
      return null;
    }
    return {
      target: parsed,
      index: (tab.children ?? []).length,
      ancestorStableIds: [...owner.ancestorStableIds, owner.field.stableId],
    };
  }

  const fieldLocation = findFieldLocation(fields, overId ?? null);
  return fieldLocation
    ? {
      target: fieldLocation.parent,
      index: fieldLocation.index,
      ancestorStableIds: fieldLocation.ancestorStableIds,
    }
    : null;
}

function findFieldLocation(fields: SchemaField[], stableId: string | null): FieldLocation | null {
  if (!stableId) {
    return null;
  }
  return findFieldLocationIn(fields, stableId, { kind: 'root' }, []);
}

function findFieldLocationIn(
  fields: SchemaField[],
  stableId: string,
  parent: DesignerDropTarget,
  ancestorStableIds: string[],
): FieldLocation | null {
  for (let index = 0; index < fields.length; index += 1) {
    const field = fields[index];
    if (field.stableId === stableId) {
      return { field, index, parent, ancestorStableIds };
    }

    if (field.type === 'nested_object') {
      const found = findFieldLocationIn(field.children ?? [], stableId, { kind: 'nested', stableId: field.stableId }, [
        ...ancestorStableIds,
        field.stableId,
      ]);
      if (found) {
        return found;
      }
    }

    if (field.type === 'tab_container') {
      for (const tab of field.tabs ?? []) {
        const found = findFieldLocationIn(
          tab.children ?? [],
          stableId,
          { kind: 'tab', containerStableId: field.stableId, tabStableId: tab.stableId },
          [...ancestorStableIds, field.stableId],
        );
        if (found) {
          return found;
        }
      }
    }
  }
  return null;
}

function fieldsAtDesignerTarget(fields: SchemaField[], target: DesignerDropTarget): SchemaField[] | null {
  if (target.kind === 'root') {
    return fields;
  }

  const owner = findFieldLocation(fields, target.kind === 'nested' ? target.stableId : target.containerStableId);
  if (!owner) {
    return null;
  }
  if (target.kind === 'nested') {
    return owner.field.type === 'nested_object' ? owner.field.children ?? [] : null;
  }
  if (owner.field.type !== 'tab_container') {
    return null;
  }
  return owner.field.tabs?.find((tab) => tab.stableId === target.tabStableId)?.children ?? null;
}

function updateDesignerTargetFields(
  fields: SchemaField[],
  target: DesignerDropTarget,
  updater: (fields: SchemaField[]) => SchemaField[],
): SchemaField[] | null {
  if (target.kind === 'root') {
    return updater(fields);
  }

  let changed = false;
  const nextFields = fields.map((field) => {
    if (target.kind === 'nested' && field.stableId === target.stableId && field.type === 'nested_object') {
      changed = true;
      return { ...field, children: updater(field.children ?? []) };
    }

    if (target.kind === 'tab' && field.stableId === target.containerStableId && field.type === 'tab_container') {
      let tabChanged = false;
      const nextTabs = (field.tabs ?? []).map((tab) => {
        if (tab.stableId !== target.tabStableId) {
          return tab;
        }
        changed = true;
        tabChanged = true;
        return { ...tab, children: updater(tab.children ?? []) };
      });
      return tabChanged ? { ...field, tabs: nextTabs } : field;
    }

    let nextField = field;
    if (field.children?.length) {
      const nextChildren = updateDesignerTargetFields(field.children, target, updater);
      if (nextChildren) {
        changed = true;
        nextField = { ...nextField, children: nextChildren };
      }
    }

    if (field.tabs?.length) {
      let tabChanged = false;
      const nextTabs = field.tabs.map((tab) => {
        const nextChildren = updateDesignerTargetFields(tab.children ?? [], target, updater);
        if (!nextChildren) {
          return tab;
        }
        changed = true;
        tabChanged = true;
        return { ...tab, children: nextChildren };
      });
      if (tabChanged) {
        nextField = { ...nextField, tabs: nextTabs };
      }
    }

    return nextField;
  });

  return changed ? nextFields : null;
}

function removeFromDesignerTarget(fields: SchemaField[], target: DesignerDropTarget, stableId: string): SchemaField[] | null {
  return updateDesignerTargetFields(fields, target, (children) => children.filter((field) => field.stableId !== stableId));
}

function sameDesignerTarget(left: DesignerDropTarget, right: DesignerDropTarget): boolean {
  if (left.kind !== right.kind) {
    return false;
  }
  if (left.kind === 'root' && right.kind === 'root') {
    return true;
  }
  if (left.kind === 'nested' && right.kind === 'nested') {
    return left.stableId === right.stableId;
  }
  return left.kind === 'tab'
    && right.kind === 'tab'
    && left.containerStableId === right.containerStableId
    && left.tabStableId === right.tabStableId;
}

function insertAt(fields: SchemaField[], field: SchemaField, index?: number): SchemaField[] {
  const insertIndex = index == null ? fields.length : Math.max(0, Math.min(index, fields.length));
  return [...fields.slice(0, insertIndex), field, ...fields.slice(insertIndex)];
}

function moveItem<T>(items: T[], from: number, to: number): T[] {
  const boundedTo = Math.max(0, Math.min(to, items.length));
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(boundedTo, 0, item);
  return next;
}
