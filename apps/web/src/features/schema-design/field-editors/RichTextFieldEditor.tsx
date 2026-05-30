import { Input, Switch, TextArea, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors, updateValidation } from './editorUtils';

export function RichTextFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="富文本字段">
        <label className="field-editor-row">
          <Typography.Text>字段标签</Typography.Text>
          <Input value={field.label} validateStatus={errors.length ? 'error' : 'default'} onChange={(label) => onChange({ ...field, label })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>占位提示</Typography.Text>
          <Input value={field.placeholder} placeholder="请输入富文本" onChange={(placeholder) => onChange({ ...field, placeholder })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>默认内容 HTML</Typography.Text>
          <TextArea autosize value={field.content} placeholder="<p>默认内容</p>" onChange={(content) => onChange({ ...field, content })} />
        </label>
        <div className="field-editor-toggle">
          <Typography.Text>必填</Typography.Text>
          <Switch checked={Boolean(field.validation?.required)} onChange={(required) => onChange(updateValidation(field, { required }))} />
        </div>
      </EditorSection>
    </div>
  );
}
