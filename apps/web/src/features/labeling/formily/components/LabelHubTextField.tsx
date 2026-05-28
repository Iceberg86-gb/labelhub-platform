import { Input } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { ReadOnlyValue } from './FieldFrame';

export interface LabelHubFieldComponentProps {
  field?: SchemaField;
  placeholder?: string;
  mode?: 'multiple';
}

export function LabelHubTextField({ field, placeholder }: LabelHubFieldComponentProps) {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'string' ? formilyField.value : '';

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value} />;
  }

  return (
    <Input
      value={value}
      placeholder={placeholder ?? field?.placeholder ?? '请输入文本'}
      validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
      onChange={(next) => formilyField.setValue(next)}
    />
  );
}

