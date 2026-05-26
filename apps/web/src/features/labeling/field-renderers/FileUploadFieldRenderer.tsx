import { Input, Typography } from '@douyinfe/semi-ui';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function FileUploadFieldRenderer({
  field,
  value,
  onChange,
  readOnly,
  errors,
}: FieldRendererProps<string>) {
  return (
    <FieldFrame field={field} errors={errors}>
      {readOnly ? (
        <ReadOnlyValue value={value} />
      ) : (
        <Input
          value={value ?? ''}
          placeholder="输入文件 URL 或文件名(M2 阶段)"
          validateStatus={errors?.length ? 'error' : 'default'}
          onChange={onChange}
        />
      )}
      <Typography.Text type="tertiary" size="small">
        M2 阶段:文件上传暂以文本占位,M3 集成文件存储
      </Typography.Text>
    </FieldFrame>
  );
}
