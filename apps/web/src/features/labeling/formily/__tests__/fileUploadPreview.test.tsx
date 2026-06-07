import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
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
