import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { formatShowItemValue, resolveShowItemValue } from '../../showItemSource';

export function LabelHubShowItem({ field, itemPayload }: { field?: SchemaField; itemPayload?: unknown }) {
  const value = formatShowItemValue(resolveShowItemValue(field, itemPayload));
  return (
    <div className="labelhub-show-item">
      {field?.label ? <div className="labelhub-show-item__title">{field.label}</div> : null}
      <pre className="labelhub-show-item__body">{value}</pre>
    </div>
  );
}
