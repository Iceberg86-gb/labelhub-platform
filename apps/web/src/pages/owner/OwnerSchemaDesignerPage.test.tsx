import type { ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderClient } from '../../features/labeling/formily/__tests__/renderClient';

const currentVersionQueryMock = vi.hoisted(() => vi.fn());
const linkageApplyMock = vi.hoisted(() => vi.fn());
const linkageDiscardMock = vi.hoisted(() => vi.fn());

vi.mock('@douyinfe/semi-icons', () => ({
  IconArrowLeft: () => <span />,
  IconRefresh: () => <span />,
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Banner: ({ className, description }: { className?: string; description?: ReactNode }) => (
    <div className={className}>{description}</div>
  ),
  Button: ({
    children,
    className,
    disabled,
    'aria-label': ariaLabel,
    onClick,
  }: {
    children?: ReactNode;
    className?: string;
    disabled?: boolean;
    'aria-label'?: string;
    onClick?: () => void;
  }) => (
    <button aria-label={ariaLabel} className={className} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>{title}{description}</div>
  ),
  Modal: ({ children, visible }: { children?: ReactNode; visible?: boolean }) => (
    visible ? <div role="dialog">{children}</div> : null
  ),
  Spin: () => <div />,
  Toast: {
    success: vi.fn(),
    warning: vi.fn(),
  },
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useParams: () => ({ schemaId: '8' }),
}));

vi.mock('../../features/schema-design/DesignerFieldBuilder', () => ({
  DesignerFieldBuilder: ({
    fields,
    onChange,
    onSelect,
  }: {
    fields: Array<{ stableId: string; label: string; type: string }>;
    onChange: (fields: Array<{ stableId: string; label: string; type: string }>) => void;
    onSelect: (stableId: string) => void;
  }) => (
    <section className="designer-builder-stub">
      Builder
      <button onClick={() => onChange([...fields, { stableId: 'summary', label: 'Summary', type: 'text' }])}>
        simulate draft change
      </button>
      <button onClick={() => onSelect('title')}>
        simulate select title field
      </button>
      <button onClick={() => onSelect('summary')}>
        simulate select another field
      </button>
    </section>
  ),
}));

vi.mock('../../features/schema-design/PublishSchemaModal', () => ({
  PublishSchemaModal: () => null,
}));

vi.mock('../../features/schema-design/VersionHistoryDrawer', () => ({
  VersionHistoryDrawer: () => null,
}));

vi.mock('../../features/schema-design/field-editors/FieldEditor', () => ({
  FieldEditor: ({ onLinkageDirtyStateChange }: { onLinkageDirtyStateChange?: (state: unknown) => void }) => (
    <section>
      Field editor
      <button
        onClick={() => onLinkageDirtyStateChange?.({
          dirty: true,
          canApply: true,
          apply: linkageApplyMock,
          discard: linkageDiscardMock,
        })}
      >
        simulate unapplied linkage
      </button>
    </section>
  ),
}));

vi.mock('../../features/schema-design/useSchemaCurrentVersionQuery', () => ({
  useSchemaCurrentVersionQuery: currentVersionQueryMock,
}));

vi.mock('../../features/task/task-detail/useTaskDetailQuery', () => ({
  useTaskDetailQuery: () => ({ data: null }),
}));

vi.mock('../../features/dataset/useDatasetItemsQuery', () => ({
  useDatasetItemsQuery: () => ({ data: { items: [] } }),
}));

vi.mock('../../features/labeling/formily/preview/SchemaFormilyPreviewPanel', () => ({
  SchemaFormilyPreviewPanel: () => <aside className="designer-preview-panel">Preview</aside>,
}));

import { OwnerSchemaDesignerPage } from './OwnerSchemaDesignerPage';

function mockCurrentVersionQuery({
  currentVersionId = 3,
  versionNumber = 2,
}: {
  currentVersionId?: number | null;
  versionNumber?: number | null;
} = {}) {
  currentVersionQueryMock.mockReturnValue({
    document: {
      fields: [{ stableId: 'title', label: 'Title', type: 'text' }],
    },
    error: null,
    hasCurrentVersion: Boolean(currentVersionId),
    isError: false,
    isFetching: false,
    isLoading: false,
    refetch: vi.fn(),
    schema: {
      currentVersionId,
      description: '搭建质检表单',
      id: 8,
      name: '质检 Designer',
    },
    version: versionNumber
      ? {
          id: 3,
          schemaJson: { fields: [] },
          versionNumber,
        }
      : null,
  });
}

describe('OwnerSchemaDesignerPage design shell', () => {
  it('renders the Designer workspace as a four-column builder with separate inspector and preview panels', () => {
    mockCurrentVersionQuery();

    const view = renderClient(<OwnerSchemaDesignerPage />);
    const html = view.html();

    expect(html).toContain('schema-designer-page schema-designer-page--workspace');
    expect(html).toContain('schema-designer-header schema-designer-header--workspace');
    expect(html).toContain('schema-designer-grid schema-designer-grid--workspace');
    expect(html).toContain('schema-designer-panel schema-designer-panel--inspector');
    expect(html).toContain('designer-builder-stub');
    expect(html).toContain('designer-preview-panel');
    expect(html).toContain('返回模板（Schema）列表');
    view.unmount();
  });

  it('renders the published version state when the draft matches the current version', () => {
    mockCurrentVersionQuery();

    const view = renderClient(<OwnerSchemaDesignerPage />);
    expect(view.text()).toContain('已发布 v2');
    expect(view.text()).not.toContain('有未发布修改');
    expect(view.text()).not.toContain('未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。');
    view.unmount();
  });

  it('renders the never-published state when no current version exists', () => {
    mockCurrentVersionQuery({ currentVersionId: null, versionNumber: null });

    const view = renderClient(<OwnerSchemaDesignerPage />);
    expect(view.text()).toContain('尚未发布');
    expect(view.text()).not.toContain('已发布 v');
    expect(view.text()).not.toContain('未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。');
    view.unmount();
  });

  it('renders dirty state and keeps the compact warning dismissible without reopening', () => {
    mockCurrentVersionQuery();

    const view = renderClient(<OwnerSchemaDesignerPage />);
    const changeButton = view.container.querySelector('.designer-builder-stub button') as HTMLButtonElement;
    expect(changeButton).toBeTruthy();

    act(() => {
      changeButton.click();
    });

    expect(view.text()).toContain('有未发布修改 · 基于 v2');
    expect(view.html()).toContain('schema-designer-session-notice');
    expect(view.html()).toContain('schema-designer-session-notice__banner');
    expect(view.text()).toContain('未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。');

    const closeButton = view.container.querySelector('[aria-label="关闭未发布修改提示"]') as HTMLButtonElement;
    expect(closeButton).toBeTruthy();
    act(() => {
      closeButton.click();
    });

    expect(view.text()).not.toContain('未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。');

    act(() => {
      changeButton.click();
    });

    expect(view.text()).toContain('有未发布修改 · 基于 v2');
    expect(view.text()).not.toContain('未发布修改仅在当前页面会话中保留。离开页面前请先发布新版本。');
    view.unmount();
  });

  it('guards field switching when linkage edits are not applied and supports all leave choices', () => {
    const openGuard = () => {
      linkageApplyMock.mockClear();
      linkageDiscardMock.mockClear();
      mockCurrentVersionQuery();
      const view = renderClient(<OwnerSchemaDesignerPage />);
      const selectTitleButton = Array.from(view.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('simulate select title field'),
      ) as HTMLButtonElement;
      act(() => {
        selectTitleButton.click();
      });
      const dirtyButton = Array.from(view.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('simulate unapplied linkage'),
      ) as HTMLButtonElement;
      const selectButton = Array.from(view.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('simulate select another field'),
      ) as HTMLButtonElement;
      act(() => {
        dirtyButton.click();
      });
      act(() => {
        selectButton.click();
      });
      expect(view.text()).toContain('该联动规则尚未应用,离开将丢失修改');
      return view;
    };

    const applyView = openGuard();
    act(() => {
      (Array.from(applyView.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('应用并离开'),
      ) as HTMLButtonElement).click();
    });
    expect(linkageApplyMock).toHaveBeenCalledTimes(1);
    expect(linkageDiscardMock).not.toHaveBeenCalled();
    applyView.unmount();

    const discardView = openGuard();
    act(() => {
      (Array.from(discardView.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('放弃更改'),
      ) as HTMLButtonElement).click();
    });
    expect(linkageDiscardMock).toHaveBeenCalledTimes(1);
    expect(linkageApplyMock).not.toHaveBeenCalled();
    discardView.unmount();

    const continueView = openGuard();
    act(() => {
      (Array.from(continueView.container.querySelectorAll('button')).find((button) =>
        button.textContent?.includes('继续编辑'),
      ) as HTMLButtonElement).click();
    });
    expect(linkageApplyMock).not.toHaveBeenCalled();
    expect(linkageDiscardMock).not.toHaveBeenCalled();
    expect(continueView.text()).not.toContain('该联动规则尚未应用,离开将丢失修改');
    continueView.unmount();
  });
});
