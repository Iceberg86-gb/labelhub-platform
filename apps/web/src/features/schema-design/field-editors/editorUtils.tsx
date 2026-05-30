import { Input, Typography } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';
import { CUSTOM_VALIDATION_FUNCTIONS } from '../../../entities/schema/customValidation';
import type { SchemaField, SchemaFieldValidation } from '../../../entities/schema/schemaTypes';
import type { FieldValidationError } from '../../../entities/schema/schemaValidation';

type FieldErrorsProps = {
  errors: FieldValidationError[];
};

export function FieldErrors({ errors }: FieldErrorsProps) {
  if (!errors.length) return null;

  return (
    <div className="field-error-list">
      {errors.map((error) => (
        <Typography.Text key={`${error.fieldPath}-${error.reason}`} className="field-error-text">
          {error.reason}
        </Typography.Text>
      ))}
    </div>
  );
}

type EditorSectionProps = {
  title: string;
  children: ReactNode;
};

export function EditorSection({ title, children }: EditorSectionProps) {
  return (
    <section className="field-editor-section">
      <Typography.Text strong>{title}</Typography.Text>
      {children}
    </section>
  );
}

type CustomValidationSectionProps = {
  field: SchemaField;
  onChange: (field: SchemaField) => void;
};

export function CustomValidationSection({ field, onChange }: CustomValidationSectionProps) {
  return (
    <EditorSection title="自定义校验">
      <label className="field-editor-row">
        <Typography.Text>函数名</Typography.Text>
        <Input
          value={field.validation?.customFunction}
          placeholder={CUSTOM_VALIDATION_FUNCTIONS.join(' / ')}
          onChange={(customFunction) => onChange(updateValidation(field, { customFunction: customFunction || undefined }))}
        />
      </label>
    </EditorSection>
  );
}

export function updateValidation(field: SchemaField, patch: Partial<SchemaFieldValidation>): SchemaField {
  return {
    ...field,
    validation: {
      ...(field.validation ?? {}),
      ...patch,
    },
  };
}

export function numberOrUndefined(value: number | string | null | undefined): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : undefined;
}
