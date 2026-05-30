import type { components } from '../../shared/api/generated/schema';

export type SchemaDocument = components['schemas']['SchemaDocument'];
export type SchemaField = components['schemas']['SchemaField'];
export type SchemaFieldType = components['schemas']['SchemaFieldType'];
export type SchemaFieldOption = components['schemas']['SchemaFieldOption'];
export type SchemaFieldValidation = components['schemas']['SchemaFieldValidation'];
export type LinkageAtomicCondition = components['schemas']['LinkageAtomicCondition'];
export type LinkageCondition = components['schemas']['LinkageCondition'];
export type LinkageConditionGroup = components['schemas']['LinkageConditionGroup'];
export type LinkageConditionOp = components['schemas']['LinkageConditionOp'];
export type LinkageConditionValue = components['schemas']['LinkageConditionValue'];
export type LabelSchema = components['schemas']['LabelSchema'];
export type SchemaVersion = components['schemas']['SchemaVersion'];

export const SCHEMA_FIELD_TYPES = [
  'text',
  'number',
  'single_select',
  'multi_select',
  'date',
  'file_upload',
  'rich_text',
  'json_editor',
  'llm_interaction',
  'show_item',
  'nested_object',
] satisfies SchemaFieldType[];

export const SCHEMA_FIELD_TYPE_LABELS: Record<SchemaFieldType, string> = {
  text: '文本',
  number: '数字',
  single_select: '单选',
  multi_select: '多选',
  date: '日期',
  file_upload: '文件上传',
  rich_text: '富文本',
  json_editor: 'JSON',
  llm_interaction: 'LLM 交互',
  show_item: '展示项',
  nested_object: '嵌套对象',
};

export function schemaVersionLabel(version?: SchemaVersion | null): string {
  if (!version) return '未绑定版本';
  return version.versionNumber != null ? `v${version.versionNumber}` : `#${version.id}`;
}
