import { Select, Tag, Typography } from '@douyinfe/semi-ui';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function SelectFieldRenderer({
  field,
  value,
  onChange,
  readOnly,
  errors,
}: FieldRendererProps<string | string[]>) {
  const options = field.options ?? [];
  const isMulti = field.type === 'multi_select';

  const renderReadOnly = () => {
    if (isMulti) {
      const values = Array.isArray(value) ? value : [];
      if (!values.length) return <ReadOnlyValue value={null} />;
      return (
        <div className="labeling-select-tags">
          {values.map((item) => (
            <Tag key={item} color="blue">
              {options.find((option) => option.value === item)?.label ?? item}
            </Tag>
          ))}
        </div>
      );
    }

    const selected = typeof value === 'string' ? options.find((option) => option.value === value) : undefined;
    return <ReadOnlyValue value={selected?.label ?? value} />;
  };

  return (
    <FieldFrame field={field} errors={errors}>
      {readOnly ? (
        renderReadOnly()
      ) : (
        <>
          <Select
            multiple={isMulti}
            value={value}
            placeholder="请选择"
            validateStatus={errors?.length ? 'error' : 'default'}
            style={{ width: '100%' }}
            onChange={(next) => {
              onChange(isMulti ? (Array.isArray(next) ? next.map(String) : []) : String(next ?? ''));
            }}
          >
            {options.map((option) => (
              <Select.Option key={option.value} value={option.value}>
                {option.label}
              </Select.Option>
            ))}
          </Select>
          {!options.length ? (
            <Typography.Text type="warning" size="small">
              此字段没有可选项。
            </Typography.Text>
          ) : null}
        </>
      )}
    </FieldFrame>
  );
}
