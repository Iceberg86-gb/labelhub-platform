import { Button } from '@douyinfe/semi-ui';
import { IconBold, IconItalic, IconLink, IconList, IconOrderedList } from '@douyinfe/semi-icons';
import { Field as FormilyField } from '@formily/core';
import { useField } from '@formily/react';
import { useCallback, useRef, type MouseEvent, type ReactNode } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

type RichTextCommand = 'bold' | 'italic' | 'insertUnorderedList' | 'insertOrderedList' | 'createLink';

export function LabelHubRichTextField({ field }: { field?: SchemaField }) {
  const formilyField = useField<FormilyField>();
  const editorRef = useRef<HTMLDivElement | null>(null);
  const value = typeof formilyField.value === 'string' ? formilyField.value : field?.content ?? '';
  const syncEditorValue = useCallback(() => {
    if (editorRef.current) {
      formilyField.setValue(editorRef.current.innerHTML);
    }
  }, [formilyField]);
  const executeCommand = useCallback((command: RichTextCommand, commandValue?: string) => {
    if (typeof document.execCommand !== 'function') {
      return;
    }
    editorRef.current?.focus();
    document.execCommand(command, false, commandValue);
    syncEditorValue();
  }, [syncEditorValue]);
  const insertLink = useCallback(() => {
    const url = normalizeLinkUrl(window.prompt('请输入 http(s) 链接地址'));
    if (!url) {
      return;
    }
    executeCommand('createLink', url);
  }, [executeCommand]);

  if (formilyField.readPretty) {
    return <div className="labelhub-rich-text-readonly" dangerouslySetInnerHTML={{ __html: value }} />;
  }

  return (
    <div className="labelhub-rich-text">
      <div className="labelhub-rich-text-toolbar" role="toolbar" aria-label="富文本格式工具栏">
        <RichTextToolbarButton
          icon={<IconBold />}
          title="加粗"
          onClick={() => executeCommand('bold')}
        />
        <RichTextToolbarButton
          icon={<IconItalic />}
          title="斜体"
          onClick={() => executeCommand('italic')}
        />
        <RichTextToolbarButton
          icon={<IconList />}
          title="无序列表"
          onClick={() => executeCommand('insertUnorderedList')}
        />
        <RichTextToolbarButton
          icon={<IconOrderedList />}
          title="有序列表"
          onClick={() => executeCommand('insertOrderedList')}
        />
        <RichTextToolbarButton
          icon={<IconLink />}
          title="插入链接"
          onClick={insertLink}
        />
      </div>
      <div
        ref={editorRef}
        className="labelhub-rich-text-editor"
        contentEditable
        suppressContentEditableWarning
        role="textbox"
        aria-label={field?.label}
        data-placeholder={field?.placeholder ?? '请输入富文本'}
        dangerouslySetInnerHTML={{ __html: value }}
        onInput={(event) => formilyField.setValue(event.currentTarget.innerHTML)}
      />
    </div>
  );
}

function RichTextToolbarButton({ icon, title, onClick }: { icon: ReactNode; title: string; onClick: () => void }) {
  return (
    <span className="labelhub-rich-text-toolbar__button" title={title}>
      <Button
        icon={icon}
        aria-label={title}
        size="small"
        theme="borderless"
        htmlType="button"
        onMouseDown={preserveEditorSelection}
        onClick={onClick}
      />
    </span>
  );
}

function preserveEditorSelection(event: MouseEvent<HTMLButtonElement>) {
  event.preventDefault();
}

function normalizeLinkUrl(rawValue: string | null): string | undefined {
  const trimmed = rawValue?.trim();
  if (!trimmed) {
    return undefined;
  }
  try {
    const url = new URL(trimmed);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}
