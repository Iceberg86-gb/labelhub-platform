import type { SchemaField } from '../../../entities/schema/schemaTypes';
import { formatShowItemValue, resolveShowItemValue } from '../showItemSource';

export function ShowItemRenderer({ field, itemPayload }: { field: SchemaField; itemPayload?: unknown }) {
  const value = formatShowItemValue(resolveShowItemValue(field, itemPayload));
  return (
    <div className="labelhub-show-item">
      <div className="labelhub-show-item__title">{field.label}</div>
      <pre className="labelhub-show-item__body">{value}</pre>
    </div>
  );
}
