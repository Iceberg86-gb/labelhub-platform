import { Button, Toast, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { useRef, useState } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { apiClient } from '../../../../shared/api/client';
import type { components } from '../../../../shared/api/generated/schema';
import { ReadOnlyValue } from './FieldFrame';

type UploadedFileValue = components['schemas']['UploadedFile'];

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
    setUploading(true);
    try {
      const { data, error } = await apiClient.POST('/sessions/{sessionId}/attachments', {
        params: { path: { sessionId } },
        body: { file: file.name },
        bodySerializer: () => {
          const formData = new FormData();
          formData.append('file', file);
          return formData;
        },
      });
      if (error || !data) {
        throw new Error('upload failed');
      }
      formilyField.setValue(data);
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
