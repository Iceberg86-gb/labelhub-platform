import type { SchemaFieldType as LabelHubSchemaFieldType } from '../../../../entities/schema/schemaTypes';

export const LABEL_HUB_COMPONENTS = {
  text: 'LabelHubTextField',
  number: 'LabelHubNumberField',
  single_select: 'LabelHubSelectField',
  multi_select: 'LabelHubSelectField',
  date: 'LabelHubDateField',
  file_upload: 'LabelHubFileUploadField',
  nested_object: 'LabelHubNestedObjectField',
} as const satisfies Record<LabelHubSchemaFieldType, string>;

export type SchemaFieldType = keyof typeof LABEL_HUB_COMPONENTS;
export type LabelHubComponentName = (typeof LABEL_HUB_COMPONENTS)[SchemaFieldType];

