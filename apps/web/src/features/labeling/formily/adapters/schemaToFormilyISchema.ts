import type { ISchema } from '@formily/react';
import type { SchemaField, SchemaFieldOption, SchemaFieldValidation } from '../../../../entities/schema/schemaTypes';
import { LABEL_HUB_COMPONENTS } from './componentRegistry';

export function schemaToFormilyISchema(fields: SchemaField[]): ISchema {
  return {
    type: 'object',
    properties: fieldsToProperties(fields),
    ...(requiredFieldIds(fields).length ? { required: requiredFieldIds(fields) } : {}),
  };
}

function fieldsToProperties(fields: SchemaField[]): Record<string, ISchema> {
  return Object.fromEntries(fields.map((field) => [field.stableId, fieldToSchema(field)]));
}

function fieldToSchema(field: SchemaField): ISchema {
  const base: ISchema = {
    type: schemaType(field),
    title: field.label,
    description: field.help,
    enum: fieldOptions(field.options),
    ...validationSchema(field.validation),
    'x-decorator': 'FieldFrame',
    'x-decorator-props': { field },
    'x-component': LABEL_HUB_COMPONENTS[field.type],
    'x-component-props': {
      field,
      placeholder: field.placeholder,
      mode: field.type === 'multi_select' ? 'multiple' : undefined,
    },
  };

  if (field.type === 'nested_object') {
    return {
      ...base,
      type: 'object',
      properties: fieldsToProperties(field.children ?? []),
      ...(requiredFieldIds(field.children ?? []).length ? { required: requiredFieldIds(field.children ?? []) } : {}),
    };
  }

  return removeUndefined(base);
}

function schemaType(field: SchemaField): ISchema['type'] {
  switch (field.type) {
    case 'number':
      return 'number';
    case 'nested_object':
      return 'object';
    case 'multi_select':
      return 'array';
    case 'text':
    case 'single_select':
    case 'date':
    case 'file_upload':
      return 'string';
    default: {
      const _exhaustive: never = field.type;
      return _exhaustive;
    }
  }
}

function validationSchema(validation?: SchemaFieldValidation): Partial<ISchema> {
  if (!validation) return {};

  return removeUndefined({
    minLength: validation.minLength,
    maxLength: validation.maxLength,
    minimum: validation.min,
    maximum: validation.max,
    pattern: validation.pattern,
  });
}

function fieldOptions(options?: SchemaFieldOption[]): ISchema['enum'] {
  if (!options?.length) return undefined;
  return options.map((option) => ({ label: option.label, value: option.value }));
}

function requiredFieldIds(fields: SchemaField[]): string[] {
  return fields.filter((field) => field.validation?.required).map((field) => field.stableId);
}

function removeUndefined<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entryValue]) => entryValue !== undefined)) as T;
}
