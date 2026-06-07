import { act, useState } from 'react';
import type { Form } from '@formily/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { apiClient } from '../../../../shared/api/client';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
import { SchemaFormilyRenderer } from '../SchemaFormilyRenderer';
import { renderClient } from './renderClient';

const oldUploadedFile = {
  objectKey: 'session-attachments/20260607/task-44/session-55/old-pdf',
  fileName: 'pdf',
  contentType: 'application/pdf',
  sizeBytes: 12,
};

const newUploadedFile = {
  objectKey: 'session-attachments/20260607/task-44/session-55/file.pdf',
  fileName: '测试中文名.pdf',
  contentType: 'application/pdf',
  sizeBytes: 34,
};

const initialPayload = {
  evidence_file: oldUploadedFile,
} satisfies AnswerPayload;

const tabFields = [
  {
    stableId: 'extension_tabs',
    label: '扩展能力',
    type: 'tab_container',
    tabs: [
      {
        stableId: 'evidence_tab',
        label: '扩展能力',
        children: [
          {
            stableId: 'evidence_file',
            label: '证据文件',
            type: 'file_upload',
          } satisfies SchemaField,
        ],
      },
    ],
  },
] satisfies SchemaField[];

describe('LabelHubFileUploadField controlled echo', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('mounts unanswered tab file_upload fields without emitting undefined payload keys', async () => {
    let latestPayload: AnswerPayload | undefined;

    function Harness() {
      const [payload, setPayload] = useState<AnswerPayload>({});
      return (
        <SchemaFormilyRenderer
          schemaFields={tabFields}
          value={payload}
          onChange={(next) => {
            latestPayload = next;
            setTimeout(() => setPayload(next), 0);
          }}
          readOnly={false}
          sessionId={55}
        />
      );
    }

    const view = renderClient(<Harness />);

    await act(async () => {
      await Promise.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(latestPayload).toEqual({});
    expect(Object.hasOwn(latestPayload ?? {}, 'evidence_file')).toBe(false);
    expect(JSON.stringify(latestPayload)).not.toContain('undefined');
    view.unmount();
  });

  it('keeps uploaded file values when parent echoes onChange back into value', async () => {
    vi.spyOn(apiClient, 'POST').mockResolvedValue({ data: newUploadedFile, response: new Response(null, { status: 201 }) } as any);
    let latestPayload: AnswerPayload = initialPayload;
    let form: Form<Record<string, unknown>> | undefined;

    function Harness() {
      const [payload, setPayload] = useState<AnswerPayload>(initialPayload);
      latestPayload = payload;
      return (
        <SchemaFormilyRenderer
          schemaFields={tabFields}
          value={payload}
          onChange={setPayload}
          readOnly={false}
          sessionId={55}
          onFormReady={(nextForm) => {
            form = nextForm;
          }}
        />
      );
    }

    const view = renderClient(<Harness />);
    const input = view.container.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [new File(['pdf'], '测试中文名.pdf', { type: 'application/pdf' })],
    });

    await act(async () => {
      input.dispatchEvent(new Event('change', { bubbles: true }));
      await Promise.resolve();
      await Promise.resolve();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(JSON.stringify(form?.values)).toContain('测试中文名.pdf');
    expect((formilyValuesToAnswerPayload(form?.values, tabFields).evidence_file as typeof newUploadedFile).fileName)
      .toBe('测试中文名.pdf');
    expect(view.text()).toContain('测试中文名.pdf');
    expect(view.text()).not.toContain('pdf上传文件pdf');
    expect((latestPayload.evidence_file as typeof newUploadedFile).fileName).toBe('测试中文名.pdf');
    view.unmount();
  });
});
