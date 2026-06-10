import { Button, Toast, Typography } from '@douyinfe/semi-ui';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { useEffect, useRef, useState } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import { apiClient, authorizedFetch } from '../../../../shared/api/client';
import type { components } from '../../../../shared/api/generated/schema';
import { ReadOnlyValue } from './FieldFrame';

type UploadedFileValue = components['schemas']['UploadedFile'];
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

export function LabelHubFileUploadField({ field, sessionId }: { field?: SchemaField; sessionId?: number }) {
  const formilyField = useField<FormilyField>();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const localPreviewRef = useRef<string | null>(null);
  const downloadedPreviewRef = useRef<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null);
  const [downloadedPreviewUrl, setDownloadedPreviewUrl] = useState<string | null>(null);
  const [pendingUploadValue, setPendingUploadValue] = useState<UploadedFileValue | null>(null);
  const value = asUploadedFile(formilyField.value);
  const imageAttachment = isImageAttachment(value);
  const previewSessionId = sessionId ?? sessionIdFromObjectKey(value?.objectKey);
  const accept = acceptedFileTypesAttr(field);

  useEffect(() => {
    if (!imageAttachment || !value?.objectKey || !previewSessionId) {
      replaceDownloadedPreview(null);
      return;
    }

    let canceled = false;
    void downloadAttachmentBlob(previewSessionId, value.objectKey)
      .then((blob) => {
        if (canceled) return;
        const url = URL.createObjectURL(blob);
        replaceDownloadedPreview(url);
        replaceLocalPreview(null);
      })
      .catch(() => {
        if (!canceled) replaceDownloadedPreview(null);
      });

    return () => {
      canceled = true;
    };
  }, [imageAttachment, previewSessionId, value?.objectKey]);

  useEffect(() => () => {
    revokePreview(localPreviewRef.current);
    revokePreview(downloadedPreviewRef.current);
  }, []);

  const previewUrl = localPreviewUrl ?? downloadedPreviewUrl;

  if (formilyField.readPretty) {
    return value ? (
      <div className="labelhub-file-upload labelhub-file-upload--readonly">
        {attachmentPreview(previewUrl, value)}
      </div>
    ) : (
      <ReadOnlyValue value="" />
    );
  }

  const handleFile = async (file?: File) => {
    if (!file || !sessionId) return;
    const maxBytes = (field?.maxFileSizeMb ?? 50) * 1024 * 1024;
    if (file.size > maxBytes) {
      Toast.error(`文件不能超过 ${field?.maxFileSizeMb ?? 50} MB`);
      return;
    }
    setPendingUploadValue(pendingUploadedFileValue(file));
    if (file.type.startsWith('image/')) {
      replaceLocalPreview(URL.createObjectURL(file));
    } else {
      replaceLocalPreview(null);
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
      setPendingUploadValue(null);
    } catch {
      replaceLocalPreview(null);
      setPendingUploadValue(null);
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
        accept={accept}
        style={{ display: 'none' }}
        onChange={(event) => void handleFile(event.target.files?.[0])}
      />
      <Button loading={uploading} disabled={!sessionId} onClick={() => inputRef.current?.click()}>
        上传文件
      </Button>
      {value ? attachmentPreview(previewUrl, value) : null}
      {!value && pendingUploadValue ? attachmentPreview(localPreviewUrl, pendingUploadValue) : null}
      {!sessionId ? <Typography.Text type="tertiary">预览模式不上传文件</Typography.Text> : null}
    </div>
  );

  function replaceLocalPreview(nextUrl: string | null) {
    if (localPreviewRef.current && localPreviewRef.current !== nextUrl) {
      revokePreview(localPreviewRef.current);
    }
    localPreviewRef.current = nextUrl;
    setLocalPreviewUrl(nextUrl);
  }

  function replaceDownloadedPreview(nextUrl: string | null) {
    if (downloadedPreviewRef.current && downloadedPreviewRef.current !== nextUrl) {
      revokePreview(downloadedPreviewRef.current);
    }
    downloadedPreviewRef.current = nextUrl;
    setDownloadedPreviewUrl(nextUrl);
  }
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

function attachmentPreview(previewUrl: string | null, value: UploadedFileValue) {
  const displayName = attachmentDisplayName(value);
  return (
    <div className="labelhub-file-upload-file">
      {previewUrl ? (
        <a className="labelhub-file-upload-preview-link" href={previewUrl} target="_blank" rel="noreferrer">
          <img className="labelhub-file-upload-thumbnail" src={previewUrl} alt={displayName} />
        </a>
      ) : null}
      <span className="labelhub-file-upload-name" title={displayName}>
        <Typography.Text>{displayName}</Typography.Text>
      </span>
    </div>
  );
}

function attachmentDisplayName(value: UploadedFileValue): string {
  const fileName = value.fileName.trim();
  if (fileName) return fileName;
  return attachmentFallbackName(value);
}

function attachmentFallbackName(value: UploadedFileValue): string {
  const objectKeyBasename = basename(value.objectKey);
  const readableBasename = objectKeyBasename.replace(
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-/i,
    '',
  );
  if (isReadableAttachmentName(readableBasename)) return readableBasename;
  if (isReadableAttachmentName(objectKeyBasename)) return objectKeyBasename;
  const extension =
    attachmentExtension(value.contentType.split(';')[0]?.split('/').pop()?.split('+')[0]) ??
    attachmentExtension(readableBasename.split('.').pop()) ??
    attachmentExtension(objectKeyBasename.split('-').pop());
  if (extension) return `附件.${extension}`;
  return value.contentType.trim() || '附件';
}

function isReadableAttachmentName(name: string): boolean {
  const trimmed = name.trim();
  return trimmed.includes('.') && !/^[0-9a-f-]+$/i.test(trimmed);
}

function attachmentExtension(rawExtension: string | undefined): string | null {
  const normalized = rawExtension?.replace(/^\./, '').trim().toLowerCase();
  if (!normalized || !/^[a-z0-9]{1,10}$/.test(normalized)) return null;
  return normalized === 'jpeg' ? 'jpg' : normalized;
}

function basename(path: string): string {
  return path.split('/').filter(Boolean).pop() ?? '';
}

function isImageAttachment(value: UploadedFileValue | null): boolean {
  return Boolean(value?.contentType?.toLowerCase().startsWith('image/'));
}

function pendingUploadedFileValue(file: File): UploadedFileValue {
  return {
    objectKey: '',
    fileName: file.name,
    contentType: file.type || 'application/octet-stream',
    sizeBytes: file.size,
  };
}

function acceptedFileTypesAttr(field?: SchemaField): string | undefined {
  return field?.acceptedFileTypes?.length ? field.acceptedFileTypes.join(',') : undefined;
}

function sessionIdFromObjectKey(objectKey?: string): number | undefined {
  const match = objectKey?.match(/\/session-(\d+)\//);
  if (!match?.[1]) return undefined;
  const parsed = Number(match[1]);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

async function downloadAttachmentBlob(sessionId: number, objectKey: string): Promise<Blob> {
  const endpoint = `${apiBaseUrl.replace(/\/$/, '')}/sessions/${sessionId}/attachments/${attachmentRef(objectKey)}`;
  const response = await authorizedFetch(endpoint);
  if (!response.ok) {
    throw new Error('download failed');
  }
  return response.blob();
}

function attachmentRef(objectKey: string): string {
  const bytes = new TextEncoder().encode(objectKey);
  let binary = '';
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function revokePreview(url: string | null | undefined) {
  if (url) {
    URL.revokeObjectURL(url);
  }
}
