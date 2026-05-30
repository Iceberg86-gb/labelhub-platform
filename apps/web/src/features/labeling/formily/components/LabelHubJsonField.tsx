import { TextArea, Toast } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { ReadOnlyValue } from './FieldFrame';

export function LabelHubJsonField({ field }: { field?: SchemaField }) {
  const formilyField = useField<FormilyField>();
  const value = formilyField.value == null ? '' : JSON.stringify(formilyField.value, null, 2);

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value} />;
  }

  return (
    <TextArea
      autosize
      value={value}
      placeholder={field?.placeholder ?? '{ }'}
      validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
      onChange={(next) => {
        if (!next.trim()) {
          formilyField.setValue(undefined);
          return;
        }
        try {
          formilyField.setValue(JSON.parse(next));
        } catch {
          Toast.warning('JSON 格式暂未生效');
        }
      }}
    />
  );
}
