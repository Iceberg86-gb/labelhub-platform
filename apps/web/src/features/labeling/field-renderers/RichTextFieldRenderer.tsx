import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';
import { sanitizeRichTextHtml } from '../../../shared/security/sanitizeRichText';

export function RichTextFieldRenderer({ field, value, onChange, readOnly, errors }: FieldRendererProps<string>) {
  return (
    <FieldFrame field={field} errors={errors} showRequiredMarker={!readOnly}>
      {readOnly ? (
        <ReadOnlyValue value={<span dangerouslySetInnerHTML={{ __html: sanitizeRichTextHtml(value) }} />} />
      ) : (
        <div
          className="labelhub-rich-text-editor"
          contentEditable
          suppressContentEditableWarning
          role="textbox"
          dangerouslySetInnerHTML={{ __html: sanitizeRichTextHtml(value ?? field.content ?? '') }}
          onInput={(event) => onChange(event.currentTarget.innerHTML)}
        />
      )}
    </FieldFrame>
  );
}
