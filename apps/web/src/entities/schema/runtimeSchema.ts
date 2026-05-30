import type { SchemaDocument, SchemaField } from './schemaTypes';

export const LABELHUB_SCHEMA_FORMAT_VERSION = 2;

export function schemaFields(document?: SchemaDocument | null): SchemaField[] {
  if (!document) return [];
  if (document['x-labelhub-schemaFormatVersion'] === LABELHUB_SCHEMA_FORMAT_VERSION) {
    return document['x-labelhub-fields'] ?? [];
  }
  return document.fields ?? [];
}

export function isJsonSchemaV2(document?: SchemaDocument | null): boolean {
  return document?.['x-labelhub-schemaFormatVersion'] === LABELHUB_SCHEMA_FORMAT_VERSION;
}

export function replaceSchemaFields(document: SchemaDocument, fields: SchemaField[]): SchemaDocument {
  if (!isJsonSchemaV2(document)) {
    return { ...document, fields };
  }
  return {
    ...document,
    properties: fieldsToJsonSchemaProperties(fields),
    required: requiredFieldIds(fields),
    'x-labelhub-fields': fields,
  };
}

function fieldsToJsonSchemaProperties(fields: SchemaField[]): NonNullable<SchemaDocument['properties']> {
  return Object.fromEntries(fields.map((field) => [field.stableId, fieldToJsonSchemaProperty(field)]));
}

function fieldToJsonSchemaProperty(field: SchemaField): NonNullable<SchemaDocument['properties']>[string] {
  const property: NonNullable<SchemaDocument['properties']>[string] = {
    title: field.label,
    ...(field.help ? { description: field.help } : {}),
  };
  if (field.type === 'number') {
    property.type = 'number';
  } else if (field.type === 'json_editor' || field.type === 'llm_interaction') {
    property.type = 'object';
  } else if (field.type === 'show_item') {
    property.type = 'null';
  } else if (field.type === 'multi_select') {
    property.type = 'array';
    property.items = { type: 'string', enum: field.options?.map((option) => option.value) ?? [] };
  } else if (field.type === 'nested_object') {
    const children = field.children ?? [];
    property.type = 'object';
    property.properties = fieldsToJsonSchemaProperties(children);
    const required = requiredFieldIds(children);
    if (required.length) {
      property.required = required;
    }
  } else {
    property.type = 'string';
    if (field.type === 'single_select') {
      property.enum = field.options?.map((option) => option.value) ?? [];
    }
  }
  if (field.validation?.minLength != null) property.minLength = field.validation.minLength;
  if (field.validation?.maxLength != null) property.maxLength = field.validation.maxLength;
  if (field.validation?.min != null) property.minimum = field.validation.min;
  if (field.validation?.max != null) property.maximum = field.validation.max;
  if (field.validation?.pattern) property.pattern = field.validation.pattern;
  return property;
}

function requiredFieldIds(fields: SchemaField[]) {
  return fields.filter((field) => field.validation?.required).map((field) => field.stableId);
}
