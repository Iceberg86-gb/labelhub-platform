import type { SchemaDocument } from './schemaTypes';

export function previewJson(document: SchemaDocument): string {
  return JSON.stringify(document, null, 2);
}

