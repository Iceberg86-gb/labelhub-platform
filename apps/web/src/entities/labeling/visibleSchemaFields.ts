import type { SchemaField } from '../schema/schemaTypes';
import type { AnswerPayload } from '../submission/answerPayload';
import { buildFlatValueIndex, isFieldVisible, type FlatValueIndex } from './linkageEvaluator';

interface VisibleSchemaFieldsResult {
  fields: SchemaField[];
  signature: string;
}

interface FilterResult {
  fields: SchemaField[];
  changed: boolean;
  signatureParts: string[];
}

export function filterVisibleSchemaFields(fields: SchemaField[], payload: AnswerPayload): SchemaField[] {
  return selectVisibleSchemaFields(fields, payload).fields;
}

export function selectVisibleSchemaFields(fields: SchemaField[], payload: AnswerPayload): VisibleSchemaFieldsResult {
  const flatValues = buildFlatValueIndex(fields, payload);
  const result = filterFields(fields, flatValues);
  return {
    fields: result.changed ? result.fields : fields,
    signature: result.signatureParts.join('\u001f'),
  };
}

export function createVisibleSchemaFieldsSelector(): (fields: SchemaField[], payload: AnswerPayload) => SchemaField[] {
  let previous: { source: SchemaField[]; signature: string; fields: SchemaField[] } | null = null;

  return (fields, payload) => {
    const current = selectVisibleSchemaFields(fields, payload);
    if (previous?.source === fields && previous.signature === current.signature) {
      return previous.fields;
    }
    previous = { source: fields, signature: current.signature, fields: current.fields };
    return current.fields;
  };
}

function filterFields(fields: SchemaField[], flatValues: FlatValueIndex, parentPath = ''): FilterResult {
  const visibleFields: SchemaField[] = [];
  const signatureParts: string[] = [];
  let changed = false;

  fields.forEach((field) => {
    const path = parentPath ? `${parentPath}.${field.stableId}` : field.stableId;
    if (!isFieldVisible(field, flatValues)) {
      changed = true;
      return;
    }

    if (field.type === 'nested_object') {
      const children = field.children ?? [];
      const childResult = filterFields(children, flatValues, path);
      const nextField = childResult.changed ? { ...field, children: childResult.fields } : field;
      visibleFields.push(nextField);
      signatureParts.push(path, ...childResult.signatureParts);
      changed ||= childResult.changed;
      return;
    }

    visibleFields.push(field);
    signatureParts.push(path);
  });

  return { fields: visibleFields, changed, signatureParts };
}
