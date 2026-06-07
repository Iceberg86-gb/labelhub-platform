import { Typography } from '@douyinfe/semi-ui';
import { observer, useField } from '@formily/react';
import type { ReactNode } from 'react';
import type { GeneralField } from '@formily/core';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

export interface FieldFrameProps {
  field?: SchemaField;
  children?: ReactNode;
  externalErrors?: string[];
  showRequiredMarker?: boolean;
}

export const FieldFrame = observer(function FieldFrame({ field, children, externalErrors, showRequiredMarker = true }: FieldFrameProps) {
  const formilyField = useField<GeneralField>();
  const errors = [...formilyErrors(formilyField), ...(externalErrors ?? [])];
  const schemaField = fieldFromPropsOrState(field, formilyField);
  const required = Boolean(schemaField?.validation?.required || (formilyField as GeneralField & { required?: boolean }).required);

  return (
    <div className={['labeling-field', errors.length ? 'labeling-field--error' : ''].join(' ')} data-labeling-field-id={schemaField?.stableId}>
      <div className="labeling-field__header">
        <Typography.Text strong>{schemaField?.label || formilyField.title || '未命名字段'}</Typography.Text>
        {showRequiredMarker && required ? (
          <span className="labeling-field__required">必填</span>
        ) : null}
      </div>
      {schemaField?.help ? (
        <Typography.Text type="tertiary" size="small">
          {schemaField.help}
        </Typography.Text>
      ) : null}
      <div className="labeling-field__control">{children}</div>
      <FieldErrors errors={errors} />
    </div>
  );
});

function FieldErrors({ errors }: { errors: string[] }) {
  if (!errors.length) return null;

  return (
    <div className="labeling-field__errors">
      {errors.map((error) => (
        <Typography.Text key={error} className="field-error-text">
          {error}
        </Typography.Text>
      ))}
    </div>
  );
}

function formilyErrors(field: GeneralField): string[] {
  const maybeFieldWithErrors = field as GeneralField & { selfErrors?: unknown[] };
  return (maybeFieldWithErrors.selfErrors ?? []).map((error: unknown) => String(error));
}

function fieldFromPropsOrState(field: SchemaField | undefined, formilyField: GeneralField): SchemaField | undefined {
  return field ?? (formilyField.componentProps?.field as SchemaField | undefined);
}

export function ReadOnlyValue({ value, empty = '(未填)' }: { value: ReactNode; empty?: string }) {
  const hasValue = value !== null && value !== undefined && value !== '';
  return <div className="labeling-field__readonly">{hasValue ? value : empty}</div>;
}
