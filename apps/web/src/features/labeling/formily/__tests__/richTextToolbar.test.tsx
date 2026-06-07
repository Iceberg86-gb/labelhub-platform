import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { SchemaFormilyRenderer } from '../SchemaFormilyRenderer';
import { renderClient } from './renderClient';

const richTextField = {
  stableId: 'reason',
  label: '理由',
  type: 'rich_text',
  placeholder: '请输入理由',
} satisfies SchemaField;

let restoreExecCommand: (() => void) | undefined;

afterEach(() => {
  restoreExecCommand?.();
  restoreExecCommand = undefined;
  vi.restoreAllMocks();
});

describe('LabelHubRichTextField toolbar', () => {
  it('renders the basic rich text toolbar in edit mode', () => {
    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={[richTextField]} value={{}} readOnly={false} onChange={() => {}} />,
    );

    expect(toolbarButton(view.container, '加粗')).not.toBeNull();
    expect(view.container.querySelector('[title="加粗"]')).not.toBeNull();
    expect(toolbarButton(view.container, '斜体')).not.toBeNull();
    expect(toolbarButton(view.container, '无序列表')).not.toBeNull();
    expect(toolbarButton(view.container, '有序列表')).not.toBeNull();
    expect(toolbarButton(view.container, '插入链接')).not.toBeNull();

    view.unmount();
  });

  it('executes bold and stores the edited HTML string', () => {
    let lastPayload: AnswerPayload | undefined;
    const view = renderClient(
      <SchemaFormilyRenderer
        schemaFields={[richTextField]}
        value={{ reason: 'plain' }}
        readOnly={false}
        onChange={(nextPayload) => {
          lastPayload = nextPayload;
        }}
      />,
    );
    const editor = view.container.querySelector('.labelhub-rich-text-editor') as HTMLDivElement;
    const execCommand = vi.fn((command: string) => {
      if (command === 'bold') {
        editor.innerHTML = '<b>plain</b>';
      }
      return true;
    });
    restoreExecCommand = mockExecCommand(execCommand);

    act(() => {
      toolbarButton(view.container, '加粗')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(execCommand).toHaveBeenCalledWith('bold', false, undefined);
    expect(lastPayload?.reason).toBe('<b>plain</b>');
    view.unmount();
  });

  it('does not render the toolbar in readOnly mode', () => {
    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={[richTextField]} value={{ reason: '<strong>已提交</strong>' }} readOnly onChange={() => {}} />,
    );

    expect(toolbarButton(view.container, '加粗')).toBeNull();
    expect(view.html()).toContain('<strong>已提交</strong>');

    view.unmount();
  });

  it('rejects javascript links from the prompt', () => {
    const view = renderClient(
      <SchemaFormilyRenderer schemaFields={[richTextField]} value={{ reason: 'plain' }} readOnly={false} onChange={() => {}} />,
    );
    const execCommand = vi.fn(() => true);
    restoreExecCommand = mockExecCommand(execCommand);
    vi.spyOn(window, 'prompt').mockReturnValue('javascript:alert(1)');

    act(() => {
      toolbarButton(view.container, '插入链接')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(execCommand).not.toHaveBeenCalled();
    view.unmount();
  });
});

function toolbarButton(container: HTMLElement, title: string): HTMLButtonElement | null {
  return container.querySelector(`[title="${title}"] button`);
}

function mockExecCommand(execCommand: typeof document.execCommand): () => void {
  const original = Object.getOwnPropertyDescriptor(document, 'execCommand');
  Object.defineProperty(document, 'execCommand', {
    configurable: true,
    writable: true,
    value: execCommand,
  });
  return () => {
    if (original) {
      Object.defineProperty(document, 'execCommand', original);
    } else {
      Reflect.deleteProperty(document, 'execCommand');
    }
  };
}
