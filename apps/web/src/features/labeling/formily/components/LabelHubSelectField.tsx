import { Select, Tag, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { ReadOnlyValue } from './FieldFrame';

export function LabelHubSelectField({ field, mode }: { field?: SchemaField; mode?: 'multiple' }) {
  const formilyField = useField<FormilyField>();
  const options = field?.options ?? [];
  const isMulti = mode === 'multiple' || field?.type === 'multi_select';
  const value = isMulti
    ? Array.isArray(formilyField.value)
      ? formilyField.value.map(String)
      : []
    : typeof formilyField.value === 'string'
      ? formilyField.value
      : undefined;

  if (formilyField.readPretty) {
    return renderReadOnlyValue({ isMulti, value, field });
  }

  return (
    <>
      <Select
        multiple={isMulti}
        value={value}
        placeholder="请选择"
        validateStatus={formilyField.selfErrors.length ? 'error' : 'default'}
        style={{ width: '100%' }}
        onChange={(next) => {
          formilyField.setValue(isMulti ? (Array.isArray(next) ? next.map(String) : []) : String(next ?? ''));
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
  );
}

function renderReadOnlyValue({
  isMulti,
  value,
  field,
}: {
  isMulti: boolean;
  value: string | string[] | undefined;
  field?: SchemaField;
}) {
  const options = field?.options ?? [];
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
}

