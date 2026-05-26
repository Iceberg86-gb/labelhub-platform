import type { SchemaField } from '../../../entities/schema/schemaTypes';
import type { AnswerValue } from '../../../entities/submission/answerPayload';

export interface FieldRendererProps<TValue = AnswerValue> {
  field: SchemaField;
  value: TValue | undefined;
  onChange: (value: TValue) => void;
  readOnly: boolean;
  errors?: string[];
}
