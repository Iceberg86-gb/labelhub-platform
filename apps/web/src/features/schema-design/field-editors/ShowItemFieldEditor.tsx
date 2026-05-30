import { Input, TextArea, Typography } from '@douyinfe/semi-ui';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors } from './editorUtils';

export function ShowItemFieldEditor({ field, onChange, errors }: FieldEditorProps) {
  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="展示项">
        <label className="field-editor-row">
          <Typography.Text>标题</Typography.Text>
          <Input value={field.label} validateStatus={errors.length ? 'error' : 'default'} onChange={(label) => onChange({ ...field, label })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>展示内容</Typography.Text>
          <TextArea autosize value={field.content} placeholder="给标注员展示的说明、样例或只读上下文" onChange={(content) => onChange({ ...field, content })} />
        </label>
        <label className="field-editor-row">
          <Typography.Text>源数据路径</Typography.Text>
          <Input value={field.sourcePath} placeholder="例如 prompt、question.text、items.0.title" onChange={(sourcePath) => onChange({ ...field, sourcePath })} />
        </label>
      </EditorSection>
    </div>
  );
}
