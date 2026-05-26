import type { SchemaField, SchemaFieldType } from './schemaTypes';

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
      return { ...base, placeholder: undefined, help: undefined };
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
    default: {
      const _exhaustive: never = type;
      throw new Error(`Unknown field type: ${_exhaustive}`);
    }
  }
}

export function findFieldByStableId(fields: SchemaField[], stableId: string | null): SchemaField | null {
  if (!stableId) return null;

  for (const field of fields) {
    if (field.stableId === stableId) {
      return field;
    }

    const found = field.children ? findFieldByStableId(field.children, stableId) : null;
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

    if (field.children?.length) {
      const nextChildren = updateFieldByStableId(field.children, stableId, updater);
      if (nextChildren !== field.children) {
        changed = true;
        return { ...field, children: nextChildren };
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

    if (field.children?.length) {
      const nextChildren = removeFieldByStableId(field.children, stableId);
      if (nextChildren !== field.children) {
        changed = true;
        next.push({ ...field, children: nextChildren });
        return;
      }
    }

    next.push(field);
  });

  return changed ? next : fields;
}
