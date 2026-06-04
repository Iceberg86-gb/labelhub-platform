import type { ReactNode } from 'react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { renderClient } from '../../features/labeling/formily/__tests__/renderClient';

const currentVersionQueryMock = vi.hoisted(() => vi.fn());

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
    'aria-label': ariaLabel,
    onClick,
  }: {
    children?: ReactNode;
    className?: string;
    'aria-label'?: string;
    onClick?: () => void;
  }) => (
    <button aria-label={ariaLabel} className={className} onClick={onClick}>
      {children}
    </button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>{title}{description}</div>
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
  }: {
    fields: Array<{ stableId: string; label: string; type: string }>;
    onChange: (fields: Array<{ stableId: string; label: string; type: string }>) => void;
  }) => (
    <section className="designer-builder-stub">
      Builder
      <button onClick={() => onChange([...fields, { stableId: 'summary', label: 'Summary', type: 'text' }])}>
        simulate draft change
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
  FieldEditor: () => <section>Field editor</section>,
}));

vi.mock('../../features/schema-design/useSchemaCurrentVersionQuery', () => ({
  useSchemaCurrentVersionQuery: currentVersionQueryMock,
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
});
