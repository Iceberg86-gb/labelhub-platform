import { act, useState } from 'react';
import type { Form } from '@formily/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { apiClient } from '../../../../shared/api/client';
import { SchemaFormilyRenderer } from '../SchemaFormilyRenderer';
import { renderClient } from './renderClient';

const fileField = {
  stableId: 'attachment',
  label: '附件',
  type: 'file_upload',
} satisfies SchemaField;

const imageObjectKey = 'session-attachments/20260530/task-44/session-55/123e4567-e89b-12d3-a456-426614174000-photo.png';

describe('LabelHubFileUploadField image preview', () => {
  afterEach(() => {
    window.localStorage?.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    restoreUrlMocks();
  });

  it('downloads image attachments with JWT and renders a thumbnail', async () => {
    const { createObjectURL, revokeObjectURL } = mockObjectUrls('blob:downloaded-image');
    const fetchMock = mockImageDownload();
    storeAccessToken('token-123');

    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[fileField]}
        value={{ attachment: imageValue(imageObjectKey) }}
        readOnly={false}
        onChange={() => {}}
        sessionId={55}
      />,
    );
    await flushEffects();

    const img = view.container.querySelector('.labelhub-file-upload-thumbnail') as HTMLImageElement | null;
    expect(img?.src).toBe('blob:downloaded-image');
    expect(img?.alt).toBe('photo.png');
    expect(fetchMock).toHaveBeenCalledWith(`/api/sessions/55/attachments/${attachmentRef(imageObjectKey)}`, {
      headers: { Authorization: 'Bearer token-123' },
    });
    expect(createObjectURL).toHaveBeenCalledOnce();

    view.unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:downloaded-image');
  });

  it('keeps non-image attachments as the existing file name row', async () => {
    const fetchMock = mockImageDownload();
    mockObjectUrls('blob:unused');
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[fileField]}
        value={{
          attachment: {
            objectKey: 'session-attachments/20260530/task-44/session-55/123e4567-e89b-12d3-a456-426614174000-report.pdf',
            fileName: 'report.pdf',
            contentType: 'application/pdf',
            sizeBytes: 12,
          },
        }}
        readOnly={false}
        onChange={() => {}}
        sessionId={55}
      />,
    );
    await flushEffects();

    expect(view.text()).toContain('report.pdf');
    expect(view.container.querySelector('.labelhub-file-upload-thumbnail')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
    view.unmount();
  });

  it('keeps a non-image fileName even when it is extension-like', async () => {
    mockImageDownload();
    mockObjectUrls('blob:unused');
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[fileField]}
        value={{
          attachment: {
            objectKey: 'session-attachments/20260530/task-44/session-55/123e4567-e89b-12d3-a456-426614174000-proof-document.pdf',
            fileName: 'pdf',
            contentType: 'application/pdf',
            sizeBytes: 12,
          },
        }}
        readOnly={false}
        onChange={() => {}}
        sessionId={55}
      />,
    );
    await flushEffects();

    expect(view.text()).toContain('pdf');
    expect(view.text()).not.toContain('123e4567-e89b-12d3-a456-426614174000-proof-document.pdf');
    expect(view.container.querySelector('.labelhub-file-upload-name')).not.toBeNull();
    view.unmount();
  });

  it('uses a readable generic fallback only when fileName is empty', async () => {
    mockImageDownload();
    mockObjectUrls('blob:unused');
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[fileField]}
        value={{
          attachment: {
            objectKey: 'session-attachments/20260530/task-44/session-55/a2fcfc21-4ea2-4c79-9bbf-90b745a896c0-pdf',
            fileName: '',
            contentType: 'application/pdf',
            sizeBytes: 12,
          },
        }}
        readOnly={false}
        onChange={() => {}}
        sessionId={55}
      />,
    );
    await flushEffects();

    expect(view.text()).toContain('附件.pdf');
    expect(view.text()).not.toContain('a2fcfc21-4ea2-4c79-9bbf-90b745a896c0-pdf');
    view.unmount();
  });

  it('keeps acceptedFileTypes empty as an unrestricted chooser and shows PDF upload feedback', async () => {
    const postSpy = vi.spyOn(apiClient, 'POST').mockImplementation(() => new Promise(() => {}));

    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[{ ...fileField, acceptedFileTypes: [] }]}
        value={{}}
        readOnly={false}
        onChange={() => {}}
        sessionId={55}
      />,
    );
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input.accept).toBe('');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['pdf'], 'evidence.pdf', { type: 'application/pdf' })],
    });

    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
      await Promise.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(postSpy).toHaveBeenCalledOnce();
    expect(view.text()).toContain('evidence.pdf');
    expect(view.container.querySelector('.labelhub-file-upload-thumbnail')).toBeNull();
    view.unmount();
  });

  it('writes UploadedFile into Formily value and emitted payload for fields inside tabs', async () => {
    const uploadedFile = {
      objectKey: 'session-attachments/20260607/task-44/session-55/abc-evidence.pdf',
      fileName: 'evidence.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1234,
    };
    vi.spyOn(apiClient, 'POST').mockResolvedValue({ data: uploadedFile, response: new Response(null, { status: 201 }) } as any);
    const onChange = vi.fn<(next: AnswerPayload) => void>();
    let form: Form<Record<string, unknown>> | undefined;

    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[{
          stableId: 'evidence_tabs',
          label: '证据 Tabs',
          type: 'tab_container',
          tabs: [
            { stableId: 'main', label: 'Main', children: [{ ...fileField, stableId: 'evidence_file', acceptedFileTypes: [] }] },
          ],
        }]}
        value={{}}
        readOnly={false}
        onChange={onChange}
        onFormReady={(nextForm) => {
          form = nextForm;
        }}
        sessionId={55}
      />,
    );
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['pdf'], 'evidence.pdf', { type: 'application/pdf' })],
    });

    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(form?.values.evidence_file).toEqual(uploadedFile);
    expect(onChange).toHaveBeenLastCalledWith({ evidence_file: uploadedFile });
    expect(view.text()).toContain('evidence.pdf');
    view.unmount();
  });

  it('keeps UploadedFile when parent mirrors onChange back into renderer value', async () => {
    const uploadedFile = {
      objectKey: 'session-attachments/20260607/task-44/session-55/abc-evidence.pdf',
      fileName: 'evidence.pdf',
      contentType: 'application/pdf',
      sizeBytes: 1234,
    };
    vi.spyOn(apiClient, 'POST').mockResolvedValue({ data: uploadedFile, response: new Response(null, { status: 201 }) } as any);
    const payloads: AnswerPayload[] = [];

    const view = renderClient(
      <MirroredRenderer
        schemaFields={[{
          stableId: 'evidence_tabs',
          label: '证据 Tabs',
          type: 'tab_container',
          tabs: [
            { stableId: 'main', label: 'Main', children: [{ ...fileField, stableId: 'evidence_file', acceptedFileTypes: [] }] },
          ],
        }]}
        onPayload={(next) => payloads.push(next)}
      />,
    );
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['pdf'], 'evidence.pdf', { type: 'application/pdf' })],
    });

    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(payloads.at(-1)).toEqual({ evidence_file: uploadedFile });
    expect(view.text()).toContain('evidence.pdf');
    view.unmount();
  });

  it('shows a local objectURL preview while image upload is pending', () => {
    const { revokeObjectURL } = mockObjectUrls('blob:local-image');
    mockPendingUpload();

    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={[fileField]} value={{}} readOnly={false} onChange={() => {}} sessionId={55} />,
    );
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['image'], 'photo.png', { type: 'image/png' })],
    });

    act(() => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });

    const img = view.container.querySelector('.labelhub-file-upload-thumbnail') as HTMLImageElement | null;
    expect(img?.src).toBe('blob:local-image');

    view.unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:local-image');
  });

  it('renders the thumbnail in readOnly mode by deriving sessionId from objectKey', async () => {
    const fetchMock = mockImageDownload();
    mockObjectUrls('blob:readonly-image');

    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[fileField]}
        value={{ attachment: imageValue(imageObjectKey) }}
        readOnly
        onChange={() => {}}
      />,
    );
    await flushEffects();

    expect(view.container.querySelector('.labelhub-file-upload-thumbnail')).not.toBeNull();
    expect(view.text()).toContain('photo.png');
    expect(view.container.querySelector('button')).toBeNull();
    expect(fetchMock).toHaveBeenCalledWith(`/api/sessions/55/attachments/${attachmentRef(imageObjectKey)}`, {
      headers: undefined,
    });
    view.unmount();
  });
});

function MirroredRenderer({
  schemaFields,
  onPayload,
}: {
  schemaFields: SchemaField[];
  onPayload: (payload: AnswerPayload) => void;
}) {
  const [payload, setPayload] = useState<AnswerPayload>({});
  return (
    <SchemaFormilyRenderer
      schemaFields={schemaFields}
      value={payload}
      readOnly={false}
      onChange={(next) => {
        onPayload(next);
        queueMicrotask(() => setPayload(next));
      }}
      sessionId={55}
    />
  );
}

function imageValue(objectKey: string) {
  return {
    objectKey,
    fileName: 'photo.png',
    contentType: 'image/png',
    sizeBytes: 5,
  };
}

function mockImageDownload() {
  const fetchMock = vi.fn(async () => new Response(new Blob(['image'], { type: 'image/png' }), { status: 200 }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function mockPendingUpload() {
  const fetchMock = vi.fn(() => new Promise<Response>(() => {}));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

let originalCreateObjectURL: typeof URL.createObjectURL | undefined;
let originalRevokeObjectURL: typeof URL.revokeObjectURL | undefined;
let hadCreateObjectURL = false;
let hadRevokeObjectURL = false;

function mockObjectUrls(url: string) {
  hadCreateObjectURL = 'createObjectURL' in URL;
  hadRevokeObjectURL = 'revokeObjectURL' in URL;
  originalCreateObjectURL = URL.createObjectURL;
  originalRevokeObjectURL = URL.revokeObjectURL;
  const createObjectURL = vi.fn(() => url);
  const revokeObjectURL = vi.fn();
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
  return { createObjectURL, revokeObjectURL };
}

function restoreUrlMocks() {
  if (hadCreateObjectURL) {
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: originalCreateObjectURL });
  } else {
    Reflect.deleteProperty(URL, 'createObjectURL');
  }
  if (hadRevokeObjectURL) {
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: originalRevokeObjectURL });
  } else {
    Reflect.deleteProperty(URL, 'revokeObjectURL');
  }
  hadCreateObjectURL = false;
  hadRevokeObjectURL = false;
  originalCreateObjectURL = undefined;
  originalRevokeObjectURL = undefined;
}

function storeAccessToken(token: string) {
  installLocalStorage();
  window.localStorage.setItem('labelhub.access_token', token);
  window.localStorage.setItem('labelhub.expires_at', '2099-01-01T00:00:00.000Z');
}

function installLocalStorage() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
      clear: () => values.clear(),
    },
  });
}

function attachmentRef(objectKey: string) {
  return btoa(objectKey).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function flushEffects() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}
