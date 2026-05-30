import type { SchemaField, SchemaFieldType } from './schemaTypes';

type SchemaTab = NonNullable<SchemaField['tabs']>[number];

export function generateStableId(): string {
  return crypto.randomUUID();
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
