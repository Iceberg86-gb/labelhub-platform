import type { FieldEditorProps } from './editorTypes';
import { DateFieldEditor } from './DateFieldEditor';
import { FileUploadFieldEditor } from './FileUploadFieldEditor';
import { NestedObjectFieldEditor } from './NestedObjectFieldEditor';
import { NumberFieldEditor } from './NumberFieldEditor';
import { SelectFieldEditor } from './SelectFieldEditor';
import { TextFieldEditor } from './TextFieldEditor';

export function FieldEditor(props: FieldEditorProps) {
  switch (props.field.type) {
    case 'text':
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
    case 'nested_object':
      return <NestedObjectFieldEditor {...props} />;
    default: {
      const _exhaustive: never = props.field.type;
      return null;
    }
  }
}
