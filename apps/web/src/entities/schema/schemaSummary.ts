import type { SchemaDocument, SchemaField, SchemaFieldType } from './schemaTypes';
import { SCHEMA_FIELD_TYPES } from './schemaTypes';
import { schemaFields } from './runtimeSchema';

export type SchemaSummary = {
  totalCount: number;
  topLevelCount: number;
  nestedCount: number;
  typeDistribution: Record<SchemaFieldType, number>;
};

export function summarizeSchema(document: SchemaDocument): SchemaSummary {
  const typeDistribution = emptyDistribution();
  let topLevelCount = 0;
  let nestedCount = 0;

  function visit(fields: SchemaField[], depth: number) {
    fields.forEach((field) => {
      typeDistribution[field.type] += 1;
      if (depth === 0) {
        topLevelCount += 1;
      } else {
        nestedCount += 1;
      }
      if (field.children?.length) {
        visit(field.children, depth + 1);
      }
      if (field.tabs?.length) {
        field.tabs.forEach((tab) => visit(tab.children ?? [], depth + 1));
      }
    });
  }

  visit(schemaFields(document), 0);

  return {
    totalCount: topLevelCount + nestedCount,
    topLevelCount,
    nestedCount,
    typeDistribution,
  };
}

function emptyDistribution(): Record<SchemaFieldType, number> {
  return SCHEMA_FIELD_TYPES.reduce(
    (acc, type) => {
      acc[type] = 0;
      return acc;
    },
    {} as Record<SchemaFieldType, number>,
  );
}
