import { Input } from '@douyinfe/semi-ui';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function TextFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<string>) {
  return (
    <FieldFrame field={field} errors={errors}>
      {readOnly ? (
        <ReadOnlyValue value={value} />
      ) : (
        <Input
          value={value ?? ''}
          placeholder={field.placeholder ?? '请输入文本'}
          validateStatus={errors?.length ? 'error' : 'default'}
          onChange={onChange}
        />
      )}
    </FieldFrame>
  );
}
