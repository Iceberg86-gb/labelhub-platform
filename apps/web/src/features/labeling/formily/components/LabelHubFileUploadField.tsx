import { Button, Toast, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { useRef, useState } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { getAccessToken } from '../../../../shared/api/auth-storage';
import { ReadOnlyValue } from './FieldFrame';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

type UploadedFileValue = {
  objectKey: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
};

export function LabelHubFileUploadField({ field, sessionId }: { field?: SchemaField; sessionId?: number }) {
  const formilyField = useField<FormilyField>();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);
  const value = asUploadedFile(formilyField.value);

  if (formilyField.readPretty) {
    return <ReadOnlyValue value={value?.fileName ?? value?.objectKey ?? ''} />;
  }

  const handleFile = async (file?: File) => {
    if (!file || !sessionId) return;
    const maxBytes = (field?.maxFileSizeMb ?? 50) * 1024 * 1024;
    if (file.size > maxBytes) {
      Toast.error(`文件不能超过 ${field?.maxFileSizeMb ?? 50} MB`);
      return;
    }
    const token = getAccessToken();
    if (!token) {
      Toast.error('登录已过期,请重新登录');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    setUploading(true);
    try {
      const response = await fetch(`${API_BASE}/sessions/${sessionId}/attachments`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });
      if (!response.ok) {
        throw new Error('upload failed');
      }
      formilyField.setValue((await response.json()) as UploadedFileValue);
    } catch {
      Toast.error('文件上传失败');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  return (
    <div className="labelhub-file-upload">
      <input
        ref={inputRef}
        type="file"
        accept={field?.acceptedFileTypes?.join(',')}
        style={{ display: 'none' }}
        onChange={(event) => void handleFile(event.target.files?.[0])}
      />
      <Button loading={uploading} disabled={!sessionId} onClick={() => inputRef.current?.click()}>
        上传文件
      </Button>
      {value ? <Typography.Text>{value.fileName}</Typography.Text> : null}
      {!sessionId ? <Typography.Text type="tertiary">预览模式不上传文件</Typography.Text> : null}
    </div>
  );
}

function asUploadedFile(value: unknown): UploadedFileValue | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const record = value as Partial<UploadedFileValue>;
  return typeof record.objectKey === 'string' && typeof record.fileName === 'string'
    ? {
        objectKey: record.objectKey,
        fileName: record.fileName,
        contentType: typeof record.contentType === 'string' ? record.contentType : 'application/octet-stream',
        sizeBytes: typeof record.sizeBytes === 'number' ? record.sizeBytes : 0,
      }
    : null;
}
