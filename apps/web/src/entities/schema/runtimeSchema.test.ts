import { describe, expect, it } from 'vitest';
import type { SchemaDocument, SchemaField } from './schemaTypes';
import { LABELHUB_SCHEMA_FORMAT_VERSION, isJsonSchemaV2, replaceSchemaFields, schemaFields } from './runtimeSchema';

const titleField: SchemaField = {
  stableId: 'title',
  label: 'Title',
  type: 'text',
  validation: {
    required: true,
    minLength: 3,
    maxLength: 120,
    pattern: '^[A-Z]',
  },
};

const scoreField: SchemaField = {
  stableId: 'score',
  label: 'Score',
  type: 'number',
  validation: {
    min: 1,
    max: 5,
  },
};

describe('runtime schema helpers', () => {
  it('reads fields from legacy schema documents', () => {
    const document: SchemaDocument = { fields: [titleField] };

    expect(isJsonSchemaV2(document)).toBe(false);
    expect(schemaFields(document)).toEqual([titleField]);
  });

  it('reads fields from JSON Schema v2 runtime metadata', () => {
    const document: SchemaDocument = {
      'x-labelhub-schemaFormatVersion': LABELHUB_SCHEMA_FORMAT_VERSION,
      $schema: 'https://json-schema.org/draft/2020-12/schema',
      type: 'object',
      properties: {},
      'x-labelhub-fields': [titleField],
    };

    expect(isJsonSchemaV2(document)).toBe(true);
    expect(schemaFields(document)).toEqual([titleField]);
  });

  it('updates legacy schema documents without changing their shape', () => {
    expect(replaceSchemaFields({ fields: [titleField] }, [scoreField])).toEqual({ fields: [scoreField] });
  });

  it('updates JSON Schema v2 properties, required list, and runtime fields together', () => {
    const document: SchemaDocument = {
      'x-labelhub-schemaFormatVersion': LABELHUB_SCHEMA_FORMAT_VERSION,
      $schema: 'https://json-schema.org/draft/2020-12/schema',
      type: 'object',
      properties: {},
      'x-labelhub-fields': [],
    };

    expect(replaceSchemaFields(document, [titleField, scoreField])).toMatchObject({
      required: ['title'],
      'x-labelhub-fields': [titleField, scoreField],
      properties: {
        title: {
          type: 'string',
          title: 'Title',
          minLength: 3,
          maxLength: 120,
          pattern: '^[A-Z]',
        },
        score: {
          type: 'number',
          title: 'Score',
          minimum: 1,
          maximum: 5,
        },
      },
    });
  });
});
