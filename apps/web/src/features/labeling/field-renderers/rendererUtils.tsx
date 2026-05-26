import { Typography } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';
import type { SchemaField } from '../../../entities/schema/schemaTypes';

export function FieldFrame({
  field,
  errors,
  children,
  showRequiredMarker = true,
}: {
  field: SchemaField;
  errors?: string[];
  children: ReactNode;
  showRequiredMarker?: boolean;
}) {
  return (
    <div className={['labeling-field', errors?.length ? 'labeling-field--error' : ''].join(' ')}>
      <div className="labeling-field__header">
        <Typography.Text strong>{field.label || '未命名字段'}</Typography.Text>
        {showRequiredMarker && field.validation?.required ? <span className="labeling-field__required">必填</span> : null}
      </div>
      {field.help ? (
        <Typography.Text type="tertiary" size="small">
          {field.help}
        </Typography.Text>
      ) : null}
      <div className="labeling-field__control">{children}</div>
      <FieldErrors errors={errors} />
    </div>
  );
}

export function FieldErrors({ errors }: { errors?: string[] }) {
  if (!errors?.length) return null;

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

export function ReadOnlyValue({ value, empty = '(未填)' }: { value: ReactNode; empty?: string }) {
  const hasValue = value !== null && value !== undefined && value !== '';
  return <div className="labeling-field__readonly">{hasValue ? value : empty}</div>;
}
