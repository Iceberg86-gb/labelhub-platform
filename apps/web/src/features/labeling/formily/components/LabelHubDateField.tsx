import { DatePicker } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { ReadOnlyValue } from './FieldFrame';

export function LabelHubDateField() {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'string' ? formilyField.value : undefined;

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value} />;
  }

  return (
    <DatePicker
      type="date"
      value={value}
      validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
      style={{ width: '100%' }}
      onChange={(next, dateString) => {
        formilyField.setValue(typeof dateString === 'string' ? dateString : toIsoDate(next));
      }}
    />
  );
}

function toIsoDate(value: unknown): string {
  if (value instanceof Date) {
    return value.toISOString().slice(0, 10);
  }
  return typeof value === 'string' ? value : '';
}

