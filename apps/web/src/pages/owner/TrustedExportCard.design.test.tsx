import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { TrustedExportCard } from '../../features/export/TrustedExportCard';
import type { ExportSnapshot } from '../../entities/export/exportTypes';

const taskExportsQueryMock = vi.hoisted(() => vi.fn());
const createExportMutationMock = vi.hoisted(() => vi.fn());
const downloadExportPackageMutationMock = vi.hoisted(() => vi.fn());
const archiveExportSnapshotMutationMock = vi.hoisted(() => vi.fn());
const taskExportFieldsQueryMock = vi.hoisted(() => vi.fn());
const routeState = vi.hoisted(() => ({
  searchParams: new URLSearchParams(),
  setSearchParams: vi.fn(),
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconArchive: () => <span />,
  IconArrowRight: () => <span />,
  IconChevronDown: () => <span />,
  IconChevronUp: () => <span />,
  IconConfigStroked: () => <span />,
  IconDelete: () => <span />,
  IconDownload: () => <span />,
  IconInfoCircle: () => <span />,
  IconList: () => <span />,
  IconRefresh: () => <span />,
  IconServer: () => <span />,
  IconShield: () => <span />,
  IconUpload: () => <span />,
  IconUser: () => <span />,
}));

function MockSelect({ children, className }: { children?: ReactNode; className?: string }) {
  return <div className={className}>{children}</div>;
}

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className, icon }: { children?: ReactNode; className?: string; icon?: ReactNode }) => (
    <button className={className}>
      {icon}
      {children}
    </button>
  ),
  Card: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <section className={className}>{children}</section>
  ),
  Checkbox: ({ className }: { className?: string }) => <input className={className} type="checkbox" readOnly />,
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Input: ({ className, placeholder, value }: { className?: string; placeholder?: string; value?: string }) => (
    <input className={className} placeholder={placeholder} value={value} readOnly />
  ),
  Pagination: () => <nav />,
  Popconfirm: ({ children }: { children?: ReactNode }) => <>{children}</>,
  Select: MockSelect,
  Space: ({ children, className }: { children?: ReactNode; className?: string }) => <div className={className}>{children}</div>,
  Spin: () => <div />,
  Table: ({ columns, dataSource, className }: { columns: Array<any>; dataSource: Array<any>; className?: string }) => (
    <table className={className}>
      <tbody>
        {dataSource.map((record) => (
          <tr key={record.id}>
            {columns.map((column, index) => (
              <td key={column.dataIndex ?? index}>
                {column.render ? column.render(record[column.dataIndex], record) : String(record[column.dataIndex] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => <span className={className}>{children}</span>,
  Tooltip: ({ children, content }: { children?: ReactNode; content?: ReactNode }) => (
    <span>
      {children}
      {content}
    </span>
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Paragraph: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <p className={className}>{children}</p>
    ),
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h2 className={className}>{children}</h2>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  useSearchParams: () => [routeState.searchParams, routeState.setSearchParams],
}));

vi.mock('../../features/export/useTaskExportsQuery', () => ({
  useTaskExportsQuery: taskExportsQueryMock,
}));

vi.mock('../../features/export/useCreateExportMutation', async () => {
  const actual = await vi.importActual<typeof import('../../features/export/useCreateExportMutation')>('../../features/export/useCreateExportMutation');
  return {
    ...actual,
    useCreateExportMutation: createExportMutationMock,
  };
});

vi.mock('../../features/export/useDownloadExportPackageMutation', () => ({
  useDownloadExportPackageMutation: downloadExportPackageMutationMock,
}));

vi.mock('../../features/export/useTaskExportFieldsQuery', () => ({
  useTaskExportFieldsQuery: taskExportFieldsQueryMock,
}));

vi.mock('../../features/export/useArchiveExportSnapshotMutation', () => ({
  useArchiveExportSnapshotMutation: archiveExportSnapshotMutationMock,
}));

vi.mock('../../features/export/ExportSnapshotDiffModal', () => ({
  ExportSnapshotDiffModal: () => <section>快照对比</section>,
}));

vi.mock('../../shared/ui/TruncatedHash', () => ({
  TruncatedHash: ({ value }: { value: string }) => <code>{value}</code>,
}));

describe('TrustedExportCard design shell', () => {
  it('renders exports as a quiet reproducibility console with builder and snapshot surfaces', () => {
    routeState.searchParams = new URLSearchParams();
    taskExportsQueryMock.mockReturnValue({
      data: { items: [makeSnapshot()], total: 1 },
      error: null,
      isError: false,
      isLoading: false,
    });
    createExportMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    downloadExportPackageMutationMock.mockReturnValue({ isPending: false, mutate: vi.fn() });
    archiveExportSnapshotMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    taskExportFieldsQueryMock.mockReturnValue({ data: makeCatalog(), isLoading: false, isError: false });

    const html = renderToString(<TrustedExportCard taskId={22} />);

    expect(html).toContain('trusted-export-card trusted-export-card--console');
    expect(html).toContain('trusted-export-console-hero');
    expect(html).toContain('trusted-export-status-strip');
    expect(html).toContain('trusted-export-flow-strip');
    expect(html).toContain('trusted-export-command-strip');
    expect(html).toContain('trusted-export-builder');
    expect(html).toContain('trusted-export-training-detail');
    expect(html).toContain('trusted-export-training-schema-preview');
    expect(html).toContain('trusted-export-mapping-panel');
    expect(html).toContain('trusted-export-mapping-summary');
    expect(html).toContain('trusted-export-snapshot-panel');
    expect(html).toContain('trusted-export-table-shell');
    expect(html).toContain('trusted-export-table');
    expect(html).toContain('可信快照控制台');
    expect(html).toContain('当前任务已生成的活跃快照总数');
    expect(html).toContain('配置字段');
    expect(html).toContain('训练格式');
    expect(html).toContain('训练格式详情');
    expect(html).toContain('字段绑定');
    expect(html).toContain('每行结构');
    expect(html).toContain('表格快照使用下方高级字段映射生成 CSV 与 Excel');
    expect(html).toContain('高级字段映射');
    expect(html).toContain('已启用');
    expect(html).toContain('9');
    expect(html).toContain('系统字段');
    expect(html).toContain('7');
    expect(html).toContain('业务字段');
    expect(html).toContain('2');
    expect(html).toContain('展开编辑');
    expect(html).toContain('可复现');
    expect(html).toContain('导出标注结果包');
    expect(html).toContain('导出训练数据包');
    expect(html).toContain('标注包');
    expect(html).not.toContain('训练包');
    expect(html).not.toContain('trusted-export-mapping-row');
    expect(html).toContain('trusted-export-recommendation');
    expect(html).toContain('trusted-export-recommendation__tag');
    expect(html).toContain('偏好对比训练');
    expect(html).toContain('检测到成对候选');
    expect(html).toContain('采用推荐');
  });

  it('shows the training package download when a non-empty training jsonl exists', () => {
    routeState.searchParams = new URLSearchParams();
    taskExportsQueryMock.mockReturnValue({
      data: { items: [makeSnapshot({ trainingLines: 12 })], total: 1 },
      error: null,
      isError: false,
      isLoading: false,
    });
    createExportMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    downloadExportPackageMutationMock.mockReturnValue({ isPending: false, mutate: vi.fn() });
    archiveExportSnapshotMutationMock.mockReturnValue({ isPending: false, mutateAsync: vi.fn() });
    taskExportFieldsQueryMock.mockReturnValue({ data: makeCatalog(), isLoading: false, isError: false });

    const html = renderToString(<TrustedExportCard taskId={22} />);

    expect(html).toContain('标注包');
    expect(html).toContain('训练包');
  });
});

function makeCatalog() {
  return {
    submissionCount: 12,
    fields: [
      { source: 'task_id', label: '任务 ID', group: 'system', nonEmptyRatio: 1, sampleValues: ['1'] },
      { source: 'item.prompt', label: 'prompt', group: 'item', nonEmptyRatio: 1, sampleValues: ['解释什么是过拟合'] },
      { source: 'item.response_a', label: 'response_a', group: 'item', nonEmptyRatio: 1, sampleValues: ['回答A'] },
      { source: 'item.response_b', label: 'response_b', group: 'item', nonEmptyRatio: 1, sampleValues: ['回答B'] },
      { source: 'answer.preferred', label: 'preferred', group: 'answer', nonEmptyRatio: 1, sampleValues: ['A'], distinctValues: ['A', 'B'] },
    ],
    sampleRows: [
      { 'item.prompt': '解释什么是过拟合', 'item.response_a': '回答A', 'item.response_b': '回答B', 'answer.preferred': 'A' },
    ],
    recommendedFormat: 'trl_dpo_jsonl',
    recommendationReason: '检测到成对候选回答与偏好字段,建议使用偏好对比训练。',
    recommendedBindings: {
      promptSource: 'item.prompt',
      preferenceSource: 'answer.preferred',
      choiceSources: { A: 'item.response_a', B: 'item.response_b' },
    },
  };
}

function makeSnapshot(options: { trainingLines?: number } = {}): ExportSnapshot {
  const trainingLines = options.trainingLines ?? 0;
  return {
    id: 91,
    archivedAt: null,
    canonicalizationVersion: 'v1',
    exportJobId: 7,
    fileHash: 'file-hash',
    fileManifest: [
      { lines: 12, name: 'training-results.csv', sha256: 'csv-sha', sizeBytes: 1024 },
      { lines: 12, name: 'training-results.xlsx', sha256: 'xlsx-sha', sizeBytes: 2048 },
      { lines: trainingLines, name: 'trl-dpo.jsonl', sha256: 'dpo-jsonl-sha', sizeBytes: trainingLines * 10 },
      { lines: 1, name: 'manifest.json', sha256: 'manifest-sha', sizeBytes: 512 },
    ],
    generatedAt: '2026-05-30T09:00:00Z',
    manifestHash: 'manifest-hash',
    objectKey: 'exports/91',
    recordCounts: { submissions: 12 },
    sourceStateHash: 'source-state-hash',
    taskId: 22,
  };
}
