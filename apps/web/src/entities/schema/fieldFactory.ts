import type { SchemaField, SchemaFieldType } from './schemaTypes';
import { generateId } from './generateId';

type SchemaTab = NonNullable<SchemaField['tabs']>[number];

export function generateStableId(): string {
  return generateId();
}

export function createField(type: SchemaFieldType): SchemaField {
  const base = {
    stableId: generateStableId(),
    label: '',
    type,
    validation: { required: false },
  };

  switch (type) {
    case 'text':
    case 'textarea':
    case 'rich_text':
    case 'llm_interaction':
      return { ...base, placeholder: undefined, help: undefined };
    case 'json_editor':
      return { ...base, help: undefined, placeholder: '{ }' };
    case 'show_item':
      return { ...base, content: '展示内容', sourcePath: undefined, validation: { required: false } };
    case 'number':
    case 'date':
    case 'file_upload':
      return { ...base, help: undefined };
    case 'single_select':
    case 'multi_select':
      return {
        ...base,
        help: undefined,
        options: [{ label: '选项 1', value: 'option_1' }],
      };
    case 'nested_object':
      return { ...base, children: [] };
    case 'tab_container':
      return { ...base, tabs: [createTab('Tab 1')] };
    default: {
      const _exhaustive: never = type;
      throw new Error(`Unknown field type: ${_exhaustive}`);
    }
  }
}

export function createTab(label: string): SchemaTab {
  return {
    stableId: generateStableId(),
    label,
    children: [],
  };
}

export function isContainerField(field: SchemaField): boolean {
  return field.type === 'nested_object' || field.type === 'tab_container';
}

export function duplicateFieldWithFreshStableIds(field: SchemaField): SchemaField {
  const copy = deepCloneField(field);
  reassignFieldStableIds(copy);
  copy.label = `${field.label || '未命名字段'} (副本)`;
  return copy;
}

export function insertFieldAfterStableId(fields: SchemaField[], stableId: string, fieldToInsert: SchemaField): SchemaField[] {
  const result = insertIntoFieldListAfterStableId(fields, stableId, fieldToInsert);
  return result.changed ? result.fields : fields;
}

export function findFieldByStableId(fields: SchemaField[], stableId: string | null): SchemaField | null {
  if (!stableId) return null;

  for (const field of fields) {
    if (field.stableId === stableId) {
      return field;
    }

    const found = findFieldByStableId(fieldChildFields(field), stableId);
    if (found) {
      return found;
    }
  }

  return null;
}

export function containsFieldStableId(fields: SchemaField[], stableId: string | null): boolean {
  return Boolean(findFieldByStableId(fields, stableId));
}

export function updateFieldByStableId(
  fields: SchemaField[],
  stableId: string,
  updater: (field: SchemaField) => SchemaField,
): SchemaField[] {
  let changed = false;
  const next = fields.map((field) => {
    if (field.stableId === stableId) {
      changed = true;
      return updater(field);
    }

    if (field.type === 'nested_object' && field.children?.length) {
      const nextChildren = updateFieldByStableId(field.children, stableId, updater);
      if (nextChildren !== field.children) {
        changed = true;
        return { ...field, children: nextChildren };
      }
    }

    if (field.type === 'tab_container' && field.tabs?.length) {
      const nextTabs = field.tabs.map((tab) => {
        const currentChildren = tab.children ?? [];
        const nextChildren = updateFieldByStableId(currentChildren, stableId, updater);
        if (nextChildren !== currentChildren) {
          changed = true;
          return { ...tab, children: nextChildren };
        }
        return tab;
      });
      if (changed) {
        return { ...field, tabs: nextTabs };
      }
    }

    return field;
  });

  return changed ? next : fields;
}

export function removeFieldByStableId(fields: SchemaField[], stableId: string): SchemaField[] {
  let changed = false;
  const next: SchemaField[] = [];

  fields.forEach((field) => {
    if (field.stableId === stableId) {
      changed = true;
      return;
    }

    if (field.type === 'nested_object' && field.children?.length) {
      const nextChildren = removeFieldByStableId(field.children, stableId);
      if (nextChildren !== field.children) {
        changed = true;
        next.push({ ...field, children: nextChildren });
        return;
      }
    }

    if (field.type === 'tab_container' && field.tabs?.length) {
      const nextTabs = field.tabs.map((tab) => {
        const currentChildren = tab.children ?? [];
        const nextChildren = removeFieldByStableId(currentChildren, stableId);
        if (nextChildren !== currentChildren) {
          changed = true;
          return { ...tab, children: nextChildren };
        }
        return tab;
      });
      if (changed) {
        next.push({ ...field, tabs: nextTabs });
        return;
      }
    }

    next.push(field);
  });

  return changed ? next : fields;
}

function fieldChildFields(field: SchemaField): SchemaField[] {
  if (field.type === 'nested_object') {
    return field.children ?? [];
  }
  if (field.type === 'tab_container') {
    return field.tabs?.flatMap((tab) => tab.children ?? []) ?? [];
  }
  return [];
}

function deepCloneField(field: SchemaField): SchemaField {
  return JSON.parse(JSON.stringify(field)) as SchemaField;
}

function reassignFieldStableIds(field: SchemaField) {
  field.stableId = generateStableId();

  if (field.type === 'nested_object') {
    field.children?.forEach(reassignFieldStableIds);
  }

  if (field.type === 'tab_container') {
    field.tabs?.forEach((tab) => {
      tab.stableId = generateStableId();
      tab.children?.forEach(reassignFieldStableIds);
    });
  }
}

function insertIntoFieldListAfterStableId(
  fields: SchemaField[],
  stableId: string,
  fieldToInsert: SchemaField,
): { fields: SchemaField[]; changed: boolean } {
  let changed = false;
  const next: SchemaField[] = [];

  fields.forEach((field) => {
    next.push(field);
    if (field.stableId === stableId) {
      next.push(fieldToInsert);
      changed = true;
      return;
    }

    if (field.type === 'nested_object' && field.children?.length) {
      const childResult = insertIntoFieldListAfterStableId(field.children, stableId, fieldToInsert);
      if (childResult.changed) {
        next[next.length - 1] = { ...field, children: childResult.fields };
        changed = true;
      }
    }

    if (field.type === 'tab_container' && field.tabs?.length) {
      let tabsChanged = false;
      const nextTabs = field.tabs.map((tab) => {
        const childResult = insertIntoFieldListAfterStableId(tab.children ?? [], stableId, fieldToInsert);
        if (!childResult.changed) return tab;
        tabsChanged = true;
        changed = true;
        return { ...tab, children: childResult.fields };
      });
      if (tabsChanged) {
        next[next.length - 1] = { ...field, tabs: nextTabs };
      }
    }
  });

  return { fields: next, changed };
}
