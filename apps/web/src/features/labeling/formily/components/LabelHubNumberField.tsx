import { InputNumber } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { ReadOnlyValue } from './FieldFrame';

export function LabelHubNumberField() {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'number' ? formilyField.value : undefined;

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value} />;
  }

  return (
    <InputNumber
      value={value}
      validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
      onChange={(next) => {
        const numeric = typeof next === 'number' ? next : Number(next);
        formilyField.setValue(Number.isFinite(numeric) ? numeric : 0);
      }}
    />
  );
}

