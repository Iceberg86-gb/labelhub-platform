import type { SchemaFieldType as LabelHubSchemaFieldType } from '../../../../entities/schema/schemaTypes';

export const LABEL_HUB_COMPONENTS = {
  text: 'LabelHubTextField',
  textarea: 'LabelHubTextareaField',
  number: 'LabelHubNumberField',
  single_select: 'LabelHubSelectField',
  multi_select: 'LabelHubSelectField',
  date: 'LabelHubDateField',
  file_upload: 'LabelHubFileUploadField',
  rich_text: 'LabelHubRichTextField',
  json_editor: 'LabelHubJsonField',
  llm_interaction: 'LabelHubLlmInteractionField',
  show_item: 'LabelHubShowItem',
  nested_object: 'LabelHubNestedObjectField',
  tab_container: 'LabelHubTabsContainer',
} as const satisfies Record<LabelHubSchemaFieldType, string>;

export type SchemaFieldType = keyof typeof LABEL_HUB_COMPONENTS;
export type LabelHubComponentName = (typeof LABEL_HUB_COMPONENTS)[SchemaFieldType];
