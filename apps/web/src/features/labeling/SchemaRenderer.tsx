import type { SchemaField } from '../../entities/schema/schemaTypes';
import type { AnswerPayload, AnswerValue } from '../../entities/submission/answerPayload';
import { getFieldValue, setFieldValue } from '../../entities/submission/answerPayload';
import { DateFieldRenderer } from './field-renderers/DateFieldRenderer';
import { FileUploadFieldRenderer } from './field-renderers/FileUploadFieldRenderer';
import { JsonFieldRenderer } from './field-renderers/JsonFieldRenderer';
import { NestedObjectFieldRenderer } from './field-renderers/NestedObjectFieldRenderer';
import { NumberFieldRenderer } from './field-renderers/NumberFieldRenderer';
import { RichTextFieldRenderer } from './field-renderers/RichTextFieldRenderer';
import { SelectFieldRenderer } from './field-renderers/SelectFieldRenderer';
import { ShowItemRenderer } from './field-renderers/ShowItemRenderer';
import { TextFieldRenderer } from './field-renderers/TextFieldRenderer';

// M7-P2 C7: retained as fallback and benchmark baseline while SchemaFormilyRenderer proves stable in production paths.
export interface SchemaRendererProps {
  fields: SchemaField[];
  value: AnswerPayload;
  onChange: (value: AnswerPayload) => void;
  readOnly: boolean;
  errors?: Map<string, string[]>;
}

export function SchemaRenderer({ fields, value, onChange, readOnly, errors }: SchemaRendererProps) {
  if (!fields.length) {
    return <div className="schema-renderer schema-renderer--empty">暂无字段。</div>;
  }

  return (
    <div className="schema-renderer">
      {fields.map((field) => {
        const fieldValue = getFieldValue(value, field.stableId);
        const fieldErrors = errors?.get(field.stableId);
        const handleFieldChange = (newValue: AnswerValue) => {
          onChange(setFieldValue(value, field.stableId, newValue));
        };

        switch (field.type) {
          case 'text':
            return (
              <TextFieldRenderer
                key={field.stableId}
                field={field}
                value={typeof fieldValue === 'string' ? fieldValue : undefined}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'number':
            return (
              <NumberFieldRenderer
                key={field.stableId}
                field={field}
                value={typeof fieldValue === 'number' ? fieldValue : undefined}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'single_select':
          case 'multi_select':
            return (
              <SelectFieldRenderer
                key={field.stableId}
                field={field}
                value={asSelectValue(fieldValue)}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'date':
            return (
              <DateFieldRenderer
                key={field.stableId}
                field={field}
                value={typeof fieldValue === 'string' ? fieldValue : undefined}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'file_upload':
            return (
              <FileUploadFieldRenderer
                key={field.stableId}
                field={field}
                value={fieldValue}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'rich_text':
            return (
              <RichTextFieldRenderer
                key={field.stableId}
                field={field}
                value={typeof fieldValue === 'string' ? fieldValue : undefined}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'json_editor':
          case 'llm_interaction':
            return (
              <JsonFieldRenderer
                key={field.stableId}
                field={field}
                value={fieldValue}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          case 'show_item':
            return <ShowItemRenderer key={field.stableId} field={field} />;
          case 'nested_object':
            return (
              <NestedObjectFieldRenderer
                key={field.stableId}
                field={field}
                value={fieldValue && typeof fieldValue === 'object' && !Array.isArray(fieldValue) ? fieldValue : undefined}
                onChange={handleFieldChange}
                readOnly={readOnly}
                errors={fieldErrors}
              />
            );
          default: {
            const _exhaustive: never = field.type;
            return _exhaustive;
          }
        }
      })}
    </div>
  );
}

function asSelectValue(value: AnswerValue | undefined): string | string[] | undefined {
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
    return value as string[];
  }
  return undefined;
}
