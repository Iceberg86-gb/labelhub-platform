import { TextArea } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { ReadOnlyValue } from './FieldFrame';
import type { LabelHubFieldComponentProps } from './LabelHubTextField';

export function LabelHubTextareaField({ field, placeholder }: LabelHubFieldComponentProps) {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'string' ? formilyField.value : '';

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value} />;
  }

  return (
    <TextArea
      autosize={{ minRows: 3, maxRows: 8 }}
      value={value}
      placeholder={placeholder ?? field?.placeholder ?? '请输入多行文本'}
      validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
      onChange={(next) => formilyField.setValue(next)}
    />
  );
}
