import { InputNumber } from '@douyinfe/semi-ui';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function NumberFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<number>) {
  return (
    <FieldFrame field={field} errors={errors} showRequiredMarker={!readOnly}>
      {readOnly ? (
        <ReadOnlyValue value={value} />
      ) : (
        <InputNumber
          value={value}
          validateStatus={errors?.length ? 'error' : 'default'}
          onChange={(next) => {
            const numeric = typeof next === 'number' ? next : Number(next);
            onChange(Number.isFinite(numeric) ? numeric : 0);
          }}
        />
      )}
    </FieldFrame>
  );
}
