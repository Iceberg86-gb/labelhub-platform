import { Button, Input, Modal, Space, TextArea, Toast, Typography } from '@douyinfe/semi-ui';
import { IconUpload } from '@douyinfe/semi-icons';
import { useMemo, useRef, useState } from 'react';
import type { SchemaDocument, SchemaField } from '../../entities/schema/schemaTypes';
import { useImportSchemaTemplateMutation } from './useImportSchemaTemplateMutation';

type SchemaImportModalProps = {
  visible: boolean;
  onClose: () => void;
};

type ParsedSchemaPackage = {
  name?: string;
  description?: string;
  schemaJson: SchemaDocument;
  fieldCount: number;
};

export function SchemaImportModal({ visible, onClose }: SchemaImportModalProps) {
  const importSchema = useImportSchemaTemplateMutation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [rawJson, setRawJson] = useState('');

  const parsed = useMemo(() => parseSchemaPackage(rawJson), [rawJson]);
  const canSubmit = Boolean(name.trim()) && parsed.ok && !importSchema.isPending;

  const reset = () => {
    setName('');
    setDescription('');
    setRawJson('');
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleClose = () => {
    if (!importSchema.isPending) {
      reset();
      onClose();
    }
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.currentTarget.files?.[0];
    if (!file) {
      return;
    }
    const text = await file.text();
    setRawJson(text);
    const next = parseSchemaPackage(text);
    if (next.ok) {
      if (!name.trim() && next.value.name) {
        setName(next.value.name);
      }
      if (!description.trim() && next.value.description) {
        setDescription(next.value.description);
      }
    }
  };

  const handleSubmit = async () => {
    if (!name.trim()) {
      Toast.warning('请填写模板名称。');
      return;
    }
    if (!parsed.ok) {
      Toast.error(parsed.error);
      return;
    }

    try {
      await importSchema.mutateAsync({
        name: name.trim(),
        description: description.trim() || undefined,
        schemaJson: parsed.value.schemaJson,
      });
      Toast.success('模板已导入');
      handleClose();
    } catch (error) {
      Toast.error(errorMessage(error, '模板导入失败。'));
    }
  };

  return (
    <Modal
      title="导入 Schema 模板"
      visible={visible}
      onCancel={handleClose}
      maskClosable={!importSchema.isPending}
      closeOnEsc={!importSchema.isPending}
      width={720}
      footer={
        <Space>
          <Button onClick={handleClose} disabled={importSchema.isPending}>
            取消
          </Button>
          <Button theme="solid" type="primary" onClick={handleSubmit} loading={importSchema.isPending} disabled={!canSubmit}>
            导入模板
          </Button>
        </Space>
      }
    >
      <div className="schema-import-modal">
        <input
          ref={fileInputRef}
          className="schema-import-modal__file-input"
          type="file"
          accept="application/json,.json"
          onChange={handleFileChange}
        />
        <Button icon={<IconUpload />} onClick={() => fileInputRef.current?.click()}>
          选择 JSON
        </Button>
        <Input value={name} onChange={setName} placeholder="模板名称" />
        <Input value={description} onChange={setDescription} placeholder="模板描述" />
        <TextArea
          value={rawJson}
          onChange={setRawJson}
          autosize={{ minRows: 8, maxRows: 14 }}
          placeholder='粘贴导出的模板包，或直接粘贴 {"fields":[...]} / JSON Schema v2。'
        />
        <div className="schema-import-modal__preview">
          {parsed.ok ? (
            <Typography.Text type="secondary">将导入 {parsed.value.fieldCount} 个字段。</Typography.Text>
          ) : rawJson.trim() ? (
            <Typography.Text type="danger">{parsed.error}</Typography.Text>
          ) : (
            <Typography.Text type="tertiary">请选择或粘贴 Schema JSON。</Typography.Text>
          )}
        </div>
      </div>
    </Modal>
  );
}

type ParseResult =
  | { ok: true; value: ParsedSchemaPackage }
  | { ok: false; error: string };

function parseSchemaPackage(rawJson: string): ParseResult {
  if (!rawJson.trim()) {
    return { ok: false, error: '请提供 Schema JSON。' };
  }

  try {
    const parsed = JSON.parse(rawJson) as unknown;
    const root = asRecord(parsed);
    if (!root) {
      return { ok: false, error: 'Schema JSON 必须是对象。' };
    }
    const schemaCandidate = asRecord(root.schemaJson) ?? root;
    const schemaJson = schemaCandidate as SchemaDocument;
    const fields = schemaFields(schemaJson);
    if (!fields.length) {
      return { ok: false, error: '未识别到 fields 或 x-labelhub-fields。' };
    }
    return {
      ok: true,
      value: {
        name: typeof root.name === 'string' ? root.name : undefined,
        description: typeof root.description === 'string' ? root.description : undefined,
        schemaJson,
        fieldCount: countFields(fields),
      },
    };
  } catch {
    return { ok: false, error: 'JSON 格式无效。' };
  }
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function schemaFields(document: SchemaDocument): SchemaField[] {
  const record = document as Record<string, unknown>;
  if (Array.isArray(record.fields)) {
    return record.fields as SchemaField[];
  }
  if (Array.isArray(record['x-labelhub-fields'])) {
    return record['x-labelhub-fields'] as SchemaField[];
  }
  return [];
}

function countFields(fields: SchemaField[]): number {
  return fields.reduce((total, field) => {
    const children = Array.isArray(field.children) ? countFields(field.children) : 0;
    const tabChildren = Array.isArray(field.tabs)
      ? field.tabs.reduce((sum, tab) => sum + countFields(tab.children ?? []), 0)
      : 0;
    return total + 1 + children + tabChildren;
  }, 0);
}

function errorMessage(error: unknown, fallback: string) {
  return typeof error === 'object' && error && 'message' in error ? String(error.message) : fallback;
}
