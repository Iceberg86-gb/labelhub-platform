import type { FieldValidator } from '@formily/core';
import { validateCustomFunctionValue } from '../../../../entities/schema/customValidation';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

export function schemaToFormilyValidators(field: SchemaField): FieldValidator | undefined {
  const validation = field.validation;
  if (!validation) return undefined;

  const validators: FieldValidator[] = [];

  if (validation.required) {
    validators.push({ required: true, message: '此字段必填' });
  }

  if (field.type === 'text' || field.type === 'textarea') {
    if (validation.minLength != null) {
      validators.push({ minLength: validation.minLength, message: `最少 ${validation.minLength} 字` });
    }
    if (validation.maxLength != null) {
      validators.push({ maxLength: validation.maxLength, message: `最多 ${validation.maxLength} 字` });
    }
    if (validation.pattern) {
      validators.push({ pattern: validation.pattern, message: '格式不正确' });
    }
  }

  if (field.type === 'number') {
    if (validation.min != null) {
      validators.push({ min: validation.min, message: `不能小于 ${validation.min}` });
    }
    if (validation.max != null) {
      validators.push({ max: validation.max, message: `不能大于 ${validation.max}` });
    }
  }

  if (validation.customFunction) {
    validators.push({
      validator(value) {
        return validateCustomFunctionValue(field.type, validation.customFunction, value) ?? null;
      },
    });
  }

  return validators.length ? validators : undefined;
}
