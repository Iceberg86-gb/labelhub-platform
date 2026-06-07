import type { FieldEditorProps } from './editorTypes';
import { DateFieldEditor } from './DateFieldEditor';
import { FileUploadFieldEditor } from './FileUploadFieldEditor';
import { JsonFieldEditor } from './JsonFieldEditor';
import { LlmInteractionFieldEditor } from './LlmInteractionFieldEditor';
import { NestedObjectFieldEditor } from './NestedObjectFieldEditor';
import { NumberFieldEditor } from './NumberFieldEditor';
import { SelectFieldEditor } from './SelectFieldEditor';
import { RichTextFieldEditor } from './RichTextFieldEditor';
import { ShowItemFieldEditor } from './ShowItemFieldEditor';
import { TabContainerFieldEditor } from './TabContainerFieldEditor';
import { TextFieldEditor } from './TextFieldEditor';
import { LinkageJsonEditor } from './LinkageJsonEditor';
import { LinkageConditionBuilder } from './LinkageConditionBuilder';

export function FieldEditor(props: FieldEditorProps) {
  const editor = renderConcreteFieldEditor(props);

  if (!editor) return null;

  return (
    <div className="field-editor">
      {editor}
      <LinkageConditionBuilder field={props.field} availableFields={props.availableFields} onChange={props.onChange} />
      <LinkageJsonEditor field={props.field} onChange={props.onChange} />
    </div>
  );
}

function renderConcreteFieldEditor(props: FieldEditorProps) {
  switch (props.field.type) {
    case 'text':
    case 'textarea':
      return <TextFieldEditor {...props} />;
    case 'number':
      return <NumberFieldEditor {...props} />;
    case 'single_select':
    case 'multi_select':
      return <SelectFieldEditor {...props} />;
    case 'date':
      return <DateFieldEditor {...props} />;
    case 'file_upload':
      return <FileUploadFieldEditor {...props} />;
    case 'rich_text':
      return <RichTextFieldEditor {...props} />;
    case 'json_editor':
      return <JsonFieldEditor {...props} />;
    case 'llm_interaction':
      return <LlmInteractionFieldEditor {...props} />;
    case 'show_item':
      return <ShowItemFieldEditor {...props} />;
    case 'nested_object':
      return <NestedObjectFieldEditor {...props} />;
    case 'tab_container':
      return <TabContainerFieldEditor {...props} />;
    default: {
      const _exhaustive: never = props.field.type;
      return null;
    }
  }
}
