import { Button, Input, Switch, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconPlus } from '@douyinfe/semi-icons';
import type { SchemaFieldOption } from '../../../entities/schema/schemaTypes';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors, updateValidation } from './editorUtils';

function nextOption(options: SchemaFieldOption[]): SchemaFieldOption {
  const nextIndex = options.length + 1;
  return { label: `选项 ${nextIndex}`, value: `option_${nextIndex}` };
}

export function SelectFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  const options = field.options ?? [];
  const updateOption = (index: number, patch: Partial<SchemaFieldOption>) => {
    const next = options.map((option, optionIndex) => (optionIndex === index ? { ...option, ...patch } : option));
    onChange({ ...field, options: next });
  };

  const removeOption = (index: number) => {
    onChange({ ...field, options: options.filter((_, optionIndex) => optionIndex !== index) });
  };

  const addOption = () => {
    onChange({ ...field, options: [...options, nextOption(options)] });
  };

  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title={field.type === 'multi_select' ? '多选字段' : '单选字段'}>
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：类别"
            onChange={(label) => onChange({ ...field, label })}
          />
        </label>
        <label className="field-editor-row">
          <Typography.Text>帮助说明</Typography.Text>
          <Input value={field.help} placeholder="给标注员看的简短说明" onChange={(help) => onChange({ ...field, help })} />
        </label>
        <div className="field-editor-toggle">
          <Typography.Text>必填</Typography.Text>
          <Switch checked={Boolean(field.validation?.required)} onChange={(required) => onChange(updateValidation(field, { required }))} />
        </div>
      </EditorSection>
      <EditorSection title="选项">
        <div className="select-options-list">
          {options.map((option, index) => (
            <div className="select-option-row" key={`${field.stableId}-${index}`}>
              <Input
                value={option.label}
                placeholder="选项标签"
                onChange={(label) => updateOption(index, { label })}
              />
              <Input
                value={option.value}
                placeholder="选项值"
                onChange={(value) => updateOption(index, { value })}
              />
              <Button
                icon={<IconDelete />}
                theme="borderless"
                type="danger"
                aria-label="删除选项"
                onClick={() => removeOption(index)}
              />
            </div>
          ))}
        </div>
        <Button icon={<IconPlus />} onClick={addOption}>
          添加选项
        </Button>
      </EditorSection>
    </div>
  );
}
