import { Input, Switch, TextArea, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { CustomValidationSection, EditorSection, FieldErrors, updateValidation } from './editorUtils';

export function LlmInteractionFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="AI 交互字段">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input value={field.label} validateStatus={errors.length ? 'error' : 'default'} onChange={(label) => onChange({ ...field, label })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>输入提示</Typography.Text>
          <Input value={field.placeholder} placeholder="告诉标注员要问什么" onChange={(placeholder) => onChange({ ...field, placeholder })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>字段 Prompt</Typography.Text>
          <TextArea autosize value={field.aiPrompt} placeholder="根据输入给出建议" onChange={(aiPrompt) => onChange({ ...field, aiPrompt })} />
        </label>
        <div className="field-editor-toggle">
          <Typography.Text>必填</Typography.Text>
          <Switch checked={Boolean(field.validation?.required)} onChange={(required) => onChange(updateValidation(field, { required }))} />
        </div>
      </EditorSection>
      <CustomValidationSection field={field} onChange={onChange} />
    </div>
  );
}
