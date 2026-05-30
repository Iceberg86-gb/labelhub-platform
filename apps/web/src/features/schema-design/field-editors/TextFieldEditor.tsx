import { Input, InputNumber, Switch, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { CustomValidationSection, EditorSection, FieldErrors, numberOrUndefined, updateValidation } from './editorUtils';

export function TextFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="基础信息">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：问题描述"
            onChange={(label) => onChange({ ...field, label })}
          />
        </label>
        <label className="field-editor-row">
          <Typography.Text>占位提示</Typography.Text>
          <Input value={field.placeholder} placeholder="输入框内的提示文字" onChange={(placeholder) => onChange({ ...field, placeholder })} />
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
      <EditorSection title="文本校验">
        <div className="field-editor-grid">
          <label className="field-editor-row">
            <Typography.Text>最小长度</Typography.Text>
            <InputNumber
              min={0}
              value={field.validation?.minLength}
              onChange={(value) => onChange(updateValidation(field, { minLength: numberOrUndefined(value) }))}
            />
          </label>
          <label className="field-editor-row">
            <Typography.Text>最大长度</Typography.Text>
            <InputNumber
              min={0}
              value={field.validation?.maxLength}
              onChange={(value) => onChange(updateValidation(field, { maxLength: numberOrUndefined(value) }))}
            />
          </label>
        </div>
        <label className="field-editor-row">
          <Typography.Text>正则表达式</Typography.Text>
          <Input
            value={field.validation?.pattern}
            placeholder="例如：^[A-Z]+$"
            onChange={(pattern) => onChange(updateValidation(field, { pattern: pattern || undefined }))}
          />
        </label>
      </EditorSection>
      <CustomValidationSection field={field} onChange={onChange} />
    </div>
  );
}
