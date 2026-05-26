import { Input, InputNumber, Switch, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors, numberOrUndefined, updateValidation } from './editorUtils';

export function NumberFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="基础信息">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：年龄"
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
      <EditorSection title="数值范围">
        <div className="field-editor-grid">
          <label className="field-editor-row">
            <Typography.Text>最小值</Typography.Text>
            <InputNumber value={field.validation?.min} onChange={(value) => onChange(updateValidation(field, { min: numberOrUndefined(value) }))} />
          </label>
          <label className="field-editor-row">
            <Typography.Text>最大值</Typography.Text>
            <InputNumber value={field.validation?.max} onChange={(value) => onChange(updateValidation(field, { max: numberOrUndefined(value) }))} />
          </label>
        </div>
      </EditorSection>
    </div>
  );
}
