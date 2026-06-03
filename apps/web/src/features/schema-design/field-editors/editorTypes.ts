import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../../entities/schema/schemaValidation';

export type FieldEditorProps = {
  field: SchemaField;
  availableFields: SchemaField[];
  onChange: (field: SchemaField) => void;
  errors: FieldValidationError[];
  errorsByField: Map<string, FieldValidationError[]>;
  selectedStableId: string | null;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
};
