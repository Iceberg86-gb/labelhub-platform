import type { AnswerValue } from '../../../entities/submission/answerPayload';
import type { FieldRendererProps } from './FieldRendererProps';
import { FieldFrame, ReadOnlyValue } from './rendererUtils';

export function FileUploadFieldRenderer({ field, value, readOnly, errors }: FieldRendererProps<AnswerValue>) {
  const fileName = fileNameOf(value);
  return (
    <FieldFrame field={field} errors={errors} showRequiredMarker={!readOnly}>
      <ReadOnlyValue value={fileName} />
    </FieldFrame>
  );
}

function fileNameOf(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '';
  const fileName = (value as { fileName?: unknown }).fileName;
  const objectKey = (value as { objectKey?: unknown }).objectKey;
  return typeof fileName === 'string' ? fileName : typeof objectKey === 'string' ? objectKey : '';
}
