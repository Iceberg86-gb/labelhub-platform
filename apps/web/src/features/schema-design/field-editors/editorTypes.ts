import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../../entities/schema/schemaValidation';

export type LinkageDirtyState = {
  dirty: boolean;
  canApply: boolean;
  apply: () => void;
  discard: () => void;
};

export type FieldEditorProps = {
  field: SchemaField;
  availableFields: SchemaField[];
  onChange: (field: SchemaField) => void;
  onLinkageDirtyStateChange?: (state: LinkageDirtyState | null) => void;
  sourcePathOptions?: Array<{ value: string; typeLabel: string }>;
  errors: FieldValidationError[];
  errorsByField: Map<string, FieldValidationError[]>;
  selectedStableId: string | null;
  onSelect: (stableId: string) => void;
  onDelete: (stableId: string) => void;
};
