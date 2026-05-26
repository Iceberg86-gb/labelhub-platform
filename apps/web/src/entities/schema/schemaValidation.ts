import type { SchemaDocument, SchemaField } from './schemaTypes';

export interface FieldValidationError {
  fieldPath: string;
  stableId: string;
  reason: string;
}

export function validateSchemaForUI(document: SchemaDocument): FieldValidationError[] {
  const errors: FieldValidationError[] = [];
  validateFields(document.fields ?? [], 'fields', errors);
  return errors;
}

export function errorsByStableId(errors: FieldValidationError[]): Map<string, FieldValidationError[]> {
  const map = new Map<string, FieldValidationError[]>();

  errors.forEach((error) => {
    const list = map.get(error.stableId) ?? [];
    list.push(error);
    map.set(error.stableId, list);
  });

  return map;
}

function validateFields(fields: SchemaField[], pathPrefix: string, errors: FieldValidationError[]) {
  fields.forEach((field, index) => {
    const fieldPath = `${pathPrefix}[${index}]`;

    if (!field.label || field.label.trim() === '') {
      errors.push({ fieldPath, stableId: field.stableId, reason: '字段标签不能为空' });
    }

    if ((field.type === 'single_select' || field.type === 'multi_select') && (!field.options || field.options.length === 0)) {
      errors.push({ fieldPath, stableId: field.stableId, reason: '选择字段至少需要一个选项' });
    }

    if (field.type === 'nested_object') {
      if (!field.children || field.children.length === 0) {
        errors.push({ fieldPath, stableId: field.stableId, reason: '嵌套对象字段需要至少一个子字段' });
        return;
      }

      field.children.forEach((child, childIndex) => {
        if (child.type === 'nested_object') {
          errors.push({
            fieldPath: `${fieldPath}.children[${childIndex}]`,
            stableId: child.stableId,
            reason: 'UI 暂不支持多层嵌套对象',
          });
        }
      });
      validateFields(field.children, `${fieldPath}.children`, errors);
    }
  });
}
