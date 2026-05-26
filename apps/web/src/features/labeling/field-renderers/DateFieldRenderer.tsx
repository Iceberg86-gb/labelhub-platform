import { Input } from '@douyinfe/semi-ui';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function DateFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<string>) {
  return (
    <FieldFrame field={field} errors={errors}>
      {readOnly ? (
        <ReadOnlyValue value={value} />
      ) : (
        <Input
          type="date"
          value={value ?? ''}
          validateStatus={errors?.length ? 'error' : 'default'}
          onChange={onChange}
        />
      )}
    </FieldFrame>
  );
}
