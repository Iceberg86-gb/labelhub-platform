import { Typography } from '@douyinfe/semi-ui';
import type { SchemaField } from '../../../entities/schema/schemaTypes';

export function ShowItemRenderer({ field }: { field: SchemaField }) {
  return (
    <div className="labelhub-show-item">
      <Typography.Title heading={6}>{field.label}</Typography.Title>
      <Typography.Paragraph>{field.content ?? field.help}</Typography.Paragraph>
    </div>
  );
}
