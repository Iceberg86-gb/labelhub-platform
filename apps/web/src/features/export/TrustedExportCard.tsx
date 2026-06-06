import { Button, Card, Checkbox, Empty, Input, Pagination, Popconfirm, Select, Space, Spin, Table, Tag, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import {
  IconArchive,
  IconConfigStroked,
  IconDownload,
  IconInfoCircle,
  IconList,
  IconRefresh,
  IconServer,
  IconShield,
  IconUpload,
  IconUser,
} from '@douyinfe/semi-icons';
import { Fragment, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ExportFieldMapping, ExportSnapshot } from '../../entities/export/exportTypes';
import { TruncatedHash } from '../../shared/ui/TruncatedHash';
import { CreateExportFailure, useCreateExportMutation } from './useCreateExportMutation';
import { ExportSnapshotDiffModal } from './ExportSnapshotDiffModal';
import { useTaskExportsQuery } from './useTaskExportsQuery';
import { useDownloadExportFileMutation } from './useDownloadExportFileMutation';
import { useArchiveExportSnapshotMutation } from './useArchiveExportSnapshotMutation';

type TrustedExportCardProps = {
  taskId: number;
};

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;
const DEFAULT_FIELD_MAPPING_ROWS: FieldMappingDraftRow[] = [
  { id: 'task_id', source: 'task_id', columnName: 'task_id', included: true },
  { id: 'dataset_item_id', source: 'dataset_item_id', columnName: 'dataset_item_id', included: true },
  { id: 'submission_id', source: 'submission_id', columnName: 'submission_id', included: true },
  { id: 'schema_version_id', source: 'schema_version_id', columnName: 'schema_version_id', included: true },
  { id: 'submitted_at', source: 'submitted_at', columnName: 'submitted_at', included: true },
  { id: 'final_verdict', source: 'final_verdict', columnName: 'final_verdict', included: true },
  { id: 'reviewed_at', source: 'reviewed_at', columnName: 'reviewed_at', included: true },
  { id: 'item-source', source: 'item.text', columnName: 'item_text', included: true },
  { id: 'answer-source', source: 'answer.title', columnName: 'answer_title', included: true },
];

const SYSTEM_FIELD_META: Record<string, { label: string; description: string }> = {
  task_id: { label: '任务 ID', description: '任务在系统中的唯一标识' },
  dataset_item_id: { label: '数据项 ID', description: '该条数据在数据集中的唯一标识' },
  submission_id: { label: '提交 ID', description: '标注员提交记录的唯一标识' },
  schema_version_id: { label: 'Schema 版本', description: '本条数据绑定的标注模板版本' },
  submitted_at: { label: '提交时间', description: '标注员提交答案的时间' },
  final_verdict: { label: '终审结论', description: '审核流程的最终裁决结果' },
  reviewed_at: { label: '审核时间', description: '终审完成的时间' },
};

const EXPORT_FLOW_STEPS = [
  { label: '配置字段', icon: <IconConfigStroked /> },
  { label: '导出', icon: <IconUpload /> },
  { label: '快照入列', icon: <IconList /> },
  { label: '对比 / 下载', icon: <IconDownload /> },
];

type FieldMappingDraftRow = {
  id: string;
  source: string;
  columnName: string;
  included: boolean;
};

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function TrustedExportCard({ taskId }: TrustedExportCardProps) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [mappingRows, setMappingRows] = useState<FieldMappingDraftRow[]>(DEFAULT_FIELD_MAPPING_ROWS);
  const [diffModalOpen, setDiffModalOpen] = useState(false);
  const [archivingSnapshotId, setArchivingSnapshotId] = useState<number | null>(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('exportPage')) ?? DEFAULT_PAGE;
  const size = parsePositiveInt(searchParams.get('exportSize')) ?? DEFAULT_SIZE;
  const showArchived = searchParams.get('exportArchived') === 'true';
  const exportsQuery = useTaskExportsQuery(taskId, { page, size, archived: showArchived });
  const createExport = useCreateExportMutation();
  const downloadExportFile = useDownloadExportFileMutation();
  const archiveExportSnapshot = useArchiveExportSnapshotMutation();
  const items = exportsQuery.data?.items ?? [];
  const manifestHashCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of items) {
      counts.set(item.manifestHash, (counts.get(item.manifestHash) ?? 0) + 1);
    }
    return counts;
  }, [items]);
  const canDiff = selectedIds.length === 2;
  const baseSnapshotId = canDiff ? selectedIds[0] ?? null : null;
  const compareSnapshotId = canDiff ? selectedIds[1] ?? null : null;
  const groupedMappingRows = groupMappingRows(mappingRows);

  const columns = useMemo(
    () => [
      {
        title: '选择',
        width: 70,
        render: (_: unknown, record: ExportSnapshot) => (
          <Checkbox checked={selectedIds.includes(record.id)} onChange={() => toggleSelection(record.id)} />
        ),
      },
      { title: 'Snapshot', dataIndex: 'id', width: 110, render: (value: number) => `#${value}` },
      {
        title: 'Manifest Hash',
        dataIndex: 'manifestHash',
        render: (value: string) => (
          <span className="export-manifest-hash-cell">
            <TruncatedHash value={value} ariaLabel="Export manifest hash" />
            {(manifestHashCounts.get(value) ?? 0) > 1 ? <Tag className="export-same-hash-tag">✓ 同 hash</Tag> : null}
          </span>
        ),
      },
      {
        title: '提交数',
        width: 100,
        render: (_: unknown, record: ExportSnapshot) => record.recordCounts?.submissions ?? 0,
      },
      {
        title: '生成时间',
        dataIndex: 'generatedAt',
        width: 150,
        render: (value: string) => formatDateTime(value),
      },
      ...(showArchived
        ? [
            {
              title: '归档时间',
              dataIndex: 'archivedAt',
              width: 150,
              render: (value?: string) => formatDateTime(value),
            },
          ]
        : []),
      {
        title: '下载',
        width: 220,
        render: (_: unknown, record: ExportSnapshot) => (
          <Space spacing="tight" wrap>
            {downloadableFiles(record).map((fileName) => (
              <Button
                key={fileName}
                size="small"
                loading={downloadExportFile.isPending}
                onClick={() => downloadExportFile.mutate({ snapshotId: record.id, fileName })}
              >
                {downloadLabel(fileName)}
              </Button>
            ))}
          </Space>
        ),
      },
      ...(showArchived
        ? []
        : [
            {
              title: '操作',
              width: 100,
              render: (_: unknown, record: ExportSnapshot) => (
                <Popconfirm
                  title="归档导出快照?"
                  content={
                    <Typography.Text style={{ display: 'block', maxWidth: 340, lineHeight: 1.6 }}>
                      归档后该快照会从默认列表隐藏,但快照记录和导出文件仍会保留,可在已归档视图中下载审计。
                    </Typography.Text>
                  }
                  position="leftTop"
                  okText="归档"
                  cancelText="取消"
                  okType="warning"
                  onConfirm={() => handleArchiveSnapshot(record)}
                >
                  <Button icon={<IconArchive />} size="small" theme="borderless" loading={archivingSnapshotId === record.id}>
                    归档
                  </Button>
                </Popconfirm>
              ),
            },
          ]),
    ],
    [archiveExportSnapshot, archivingSnapshotId, downloadExportFile, manifestHashCounts, selectedIds, showArchived, taskId],
  );

  async function handleCreateExport() {
    try {
      await createExport.mutateAsync({ taskId, fieldMapping: buildFieldMapping(mappingRows) });
      Toast.success('导出任务已提交');
    } catch (error) {
      const failure = error instanceof CreateExportFailure ? error : null;
      Toast.error(failure?.userMessage ?? '导出失败');
    }
  }

  function updateMappingRow(rowId: string, patch: Partial<FieldMappingDraftRow>) {
    setMappingRows((current) => current.map((row) => (row.id === rowId ? { ...row, ...patch } : row)));
  }

  function addMappingRow() {
    const id = `custom-${Date.now()}`;
    setMappingRows((current) => [...current, { id, source: '', columnName: '', included: true }]);
  }

  function removeMappingRow(rowId: string) {
    setMappingRows((current) => current.filter((row) => row.id !== rowId));
  }

  function toggleSelection(snapshotId: number) {
    setSelectedIds((current) => {
      if (current.includes(snapshotId)) return current.filter((id) => id !== snapshotId);
      if (current.length >= 2) return [current[1], snapshotId];
      return [...current, snapshotId];
    });
  }

  async function handleArchiveSnapshot(snapshot: ExportSnapshot) {
    setArchivingSnapshotId(snapshot.id);
    try {
      await archiveExportSnapshot.mutateAsync({ taskId, snapshotId: snapshot.id });
      setSelectedIds((current) => current.filter((id) => id !== snapshot.id));
      Toast.success(`已归档导出快照 #${snapshot.id}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '归档失败,请稍后重试');
    } finally {
      setArchivingSnapshotId(null);
    }
  }

  function updateParams(next: { page?: number; size?: number; archived?: boolean }) {
    const params = new URLSearchParams(searchParams);
    params.set('exportPage', String(next.page ?? page));
    params.set('exportSize', String(next.size ?? size));
    params.set('exportArchived', String(next.archived ?? showArchived));
    setSearchParams(params);
  }

  return (
    <Card className="trusted-export-card trusted-export-card--console" bordered={false}>
      <div className="trusted-export-header trusted-export-console-hero">
        <div className="trusted-export-console-hero__copy">
          <Typography.Title className="trusted-export-title" heading={4}>
            可信快照控制台
          </Typography.Title>
          <Typography.Text className="trusted-export-subtitle">
            将 task 的 source facts 物化为 canonical export 快照,用于训练数据交付与审计复现。
          </Typography.Text>
          <Typography.Text type="tertiary">导出仅包含审核通过(approved)的可信数据,通过即视为入库。</Typography.Text>
        </div>
        <div className="trusted-export-status-strip" aria-label="Export reproducibility summary">
          <span className="trusted-export-status-pill trusted-export-status-pill--strong">
            <span className="trusted-export-status-pill__icon"><IconServer /></span>
            <span>
              <strong>{exportsQuery.data?.total ?? 0} 个{showArchived ? '已归档' : '活跃'}快照</strong>
              <small>当前任务已生成的活跃快照总数</small>
            </span>
          </span>
          <span className="trusted-export-status-pill">
            <span className="trusted-export-status-pill__icon"><IconUser /></span>
            <span>
              <strong>已选 {selectedIds.length}/2</strong>
              <small>最多同时选择 2 个快照用于对比</small>
            </span>
          </span>
          <span className="trusted-export-status-pill trusted-export-status-pill--stable">
            <span className="trusted-export-status-pill__icon"><IconShield /></span>
            <span>
              <strong>可复现</strong>
              <small>所有快照均基于相同规范与数据生成</small>
            </span>
          </span>
        </div>
      </div>

      <div className="trusted-export-flow-strip" aria-label="Export workflow">
        {EXPORT_FLOW_STEPS.map((step, index) => (
          <Fragment key={step.label}>
            <span className="trusted-export-flow-step">
              <span className="trusted-export-flow-step__icon">{step.icon}</span>
              <span>{step.label}</span>
            </span>
            {index < EXPORT_FLOW_STEPS.length - 1 ? <span className="trusted-export-flow-connector" aria-hidden="true" /> : null}
          </Fragment>
        ))}
      </div>

      <div className="trusted-export-toolbar trusted-export-command-strip">
        <Typography.Text type="tertiary">
          共 {exportsQuery.data?.total ?? 0} 个{showArchived ? '已归档' : '活跃'}快照
          {selectedIds.length > 0 ? `,已选 ${selectedIds.length}/2` : ''}
        </Typography.Text>
        <Space className="trusted-export-command-group">
          <Select
            size="small"
            value={showArchived ? 'archived' : 'active'}
            onChange={(value) => {
              setSelectedIds([]);
              updateParams({ page: 1, archived: value === 'archived' });
            }}
            optionList={[
              { value: 'active', label: '活跃快照' },
              { value: 'archived', label: '已归档快照' },
            ]}
          />
          <Button icon={<IconRefresh />} disabled={!canDiff} onClick={() => setDiffModalOpen(true)}>
            对比所选
          </Button>
          <Button
            icon={<IconUpload />}
            theme="solid"
            type="primary"
            loading={createExport.isPending}
            onClick={handleCreateExport}
          >
            导出
          </Button>
        </Space>
      </div>

      <div className="trusted-export-console-grid">
        <div className="trusted-export-field-mapping trusted-export-builder">
          <div className="trusted-export-field-mapping-header trusted-export-builder__header">
            <Typography.Text strong>业务表字段映射</Typography.Text>
            <Button size="small" onClick={addMappingRow}>
              添加列
            </Button>
          </div>
          <div className="trusted-export-builder__table">
            <div className="trusted-export-builder__table-head">
              <span className="trusted-export-builder__table-head-spacer" />
              <div className="trusted-export-builder__table-head-grid">
                <span />
                <span className="trusted-export-builder__heading">
                  源字段
                  <IconInfoCircle />
                </span>
                <span className="trusted-export-builder__heading trusted-export-builder__heading--column">
                  <span>
                    导出列名
                    <IconInfoCircle />
                  </span>
                  <small>导出列名将写入交付文件,建议保持英文</small>
                </span>
                <span />
              </div>
            </div>
            <div className="trusted-export-field-mapping-list trusted-export-builder__list">
              {groupedMappingRows.map((group) => (
                <div className="trusted-export-builder__group" key={group.kind}>
                  <div className="trusted-export-builder__group-label">{group.label}</div>
                  <div className="trusted-export-builder__group-rows">
                    {group.rows.map((row) => (
                      <div className="trusted-export-mapping-row" key={row.id}>
                        <Checkbox checked={row.included} onChange={(event) => updateMappingRow(row.id, { included: Boolean(event.target.checked) })} />
                        <SourceFieldCell row={row} onChange={(patch) => updateMappingRow(row.id, patch)} />
                        <Input
                          className="trusted-export-mapping-row__column-input"
                          size="small"
                          value={row.columnName}
                          placeholder="导出列名"
                          onChange={(value) => updateMappingRow(row.id, { columnName: value })}
                        />
                        <Button size="small" type="danger" theme="borderless" onClick={() => removeMappingRow(row.id)}>
                          删除
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="trusted-export-snapshot-panel">
          {exportsQuery.isLoading ? <Spin /> : null}
          {exportsQuery.isError ? (
            <div className="trusted-export-empty-state">
              <Empty title="导出列表加载失败" description={exportsQuery.error instanceof Error ? exportsQuery.error.message : '请稍后重试。'} />
            </div>
          ) : null}
          {!exportsQuery.isLoading && !exportsQuery.isError && items.length === 0 ? (
            <div className="trusted-export-empty-state trusted-export-empty-state--snapshot">
              <Empty
                title={showArchived ? '暂无已归档快照' : '尚未导出'}
                description={showArchived ? '归档后的快照会保留在这里,仍可下载审计。' : '点击"导出"按钮创建可信训练数据集快照。'}
              />
            </div>
          ) : null}
          {items.length > 0 ? (
            <div className="trusted-export-table-shell">
              <Table className="trusted-export-table" columns={columns} dataSource={items} rowKey="id" pagination={false} size="small" />
              <div className="export-pagination">
                <Pagination
                  total={exportsQuery.data?.total ?? 0}
                  currentPage={page}
                  pageSize={size}
                  showSizeChanger
                  onPageChange={(nextPage) => updateParams({ page: nextPage })}
                  onPageSizeChange={(nextSize) => updateParams({ page: 1, size: nextSize })}
                />
              </div>
            </div>
          ) : null}
        </div>
      </div>

      {diffModalOpen && baseSnapshotId != null && compareSnapshotId != null ? (
        <ExportSnapshotDiffModal baseSnapshotId={baseSnapshotId} compareSnapshotId={compareSnapshotId} onClose={() => setDiffModalOpen(false)} />
      ) : null}
    </Card>
  );
}

function SourceFieldCell({ row, onChange }: { row: FieldMappingDraftRow; onChange: (patch: Partial<FieldMappingDraftRow>) => void }) {
  const meta = SYSTEM_FIELD_META[row.source];
  if (!meta) {
    return (
      <span className="trusted-export-mapping-row__source">
        <Input
          className="trusted-export-mapping-row__source-input"
          size="small"
          value={row.source}
          placeholder="source: item.prompt / answer.label"
          onChange={(source) => onChange({ source })}
        />
      </span>
    );
  }

  return (
    <span className="trusted-export-mapping-row__source">
      <span className="trusted-export-mapping-row__source-label">
        {meta.label}
        <Tooltip content={meta.description}>
          <IconInfoCircle size="small" />
        </Tooltip>
      </span>
      <code className="trusted-export-mapping-row__source-code">{row.source}</code>
    </span>
  );
}

function groupMappingRows(rows: FieldMappingDraftRow[]) {
  const systemRows = rows.filter((row) => Boolean(SYSTEM_FIELD_META[row.source]));
  const contentRows = rows.filter((row) => !SYSTEM_FIELD_META[row.source]);
  return [
    { kind: 'system', label: '系统溯源字段', rows: systemRows },
    { kind: 'content', label: '业务内容字段', rows: contentRows },
  ].filter((group) => group.rows.length > 0);
}

function buildFieldMapping(rows: FieldMappingDraftRow[]): ExportFieldMapping {
  return {
    columns: rows
      .filter((row) => row.source.trim() && row.columnName.trim())
      .map((row) => ({
        source: row.source.trim(),
        columnName: row.columnName.trim(),
        included: row.included,
      })),
  };
}

function parsePositiveInt(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}

function downloadableFiles(snapshot: ExportSnapshot) {
  const names = snapshot.fileManifest.map((file) => file.name).filter(Boolean);
  const preferred = ['training-results.csv', 'training-results.xlsx', 'manifest.json'];
  return preferred.filter((name) => names.includes(name));
}

function downloadLabel(fileName: string) {
  if (fileName.endsWith('.csv')) return 'CSV';
  if (fileName.endsWith('.xlsx')) return 'Excel';
  return 'Manifest';
}
