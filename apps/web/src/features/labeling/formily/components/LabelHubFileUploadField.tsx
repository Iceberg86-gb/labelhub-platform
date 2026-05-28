import { Input, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { ReadOnlyValue } from './FieldFrame';

export function LabelHubFileUploadField({ field }: { field?: SchemaField }) {
  const formilyField = useField<FormilyField>();
  const value = typeof formilyField.value === 'string' ? formilyField.value : '';

  if (formilyField.readPretty) {
    return (
      <>
        <ReadOnlyValue value={value} />
        <UploadPlaceholderNote />
      </>
    );
  }

  return (
    <>
      <Input
        value={value}
        placeholder={field?.placeholder ?? '输入文件 URL 或文件名(M2 阶段)'}
        validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
        onChange={(next) => formilyField.setValue(next)}
      />
      <UploadPlaceholderNote />
    </>
  );
}

function UploadPlaceholderNote() {
  return (
    <Typography.Text type="tertiary" size="small">
      M2 阶段:文件上传暂以文本占位,M3 集成文件存储
    </Typography.Text>
  );
}

