import { Button, Card, Checkbox, Empty, Pagination, Space, Spin, Table, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconUpload } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ExportSnapshot } from '../../entities/export/exportTypes';
import { CreateExportFailure, useCreateExportMutation } from './useCreateExportMutation';
import { ExportSnapshotDiffModal } from './ExportSnapshotDiffModal';
import { useTaskExportsQuery } from './useTaskExportsQuery';

type TrustedExportCardProps = {
  taskId: number;
};

const DEFAULT_PAGE = 1;
const DEFAULT_SIZE = 20;

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function TrustedExportCard({ taskId }: TrustedExportCardProps) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [diffModalOpen, setDiffModalOpen] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('exportPage')) ?? DEFAULT_PAGE;
  const size = parsePositiveInt(searchParams.get('exportSize')) ?? DEFAULT_SIZE;
  const exportsQuery = useTaskExportsQuery(taskId, { page, size });
  const createExport = useCreateExportMutation();
  const items = exportsQuery.data?.items ?? [];
  const canDiff = selectedIds.length === 2;
  const baseSnapshotId = canDiff ? selectedIds[0] ?? null : null;
  const compareSnapshotId = canDiff ? selectedIds[1] ?? null : null;

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
        render: (value: string) => <Typography.Text className="mono-value">{shortHash(value)}</Typography.Text>,
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
    ],
    [selectedIds],
  );

  async function handleCreateExport() {
    try {
      await createExport.mutateAsync({ taskId });
      Toast.success('导出成功');
    } catch (error) {
      const failure = error instanceof CreateExportFailure ? error : null;
      Toast.error(failure?.userMessage ?? '导出失败');
    }
  }

  function toggleSelection(snapshotId: number) {
    setSelectedIds((current) => {
      if (current.includes(snapshotId)) return current.filter((id) => id !== snapshotId);
      if (current.length >= 2) return [current[1], snapshotId];
      return [...current, snapshotId];
    });
  }

  function updateParams(next: { page?: number; size?: number }) {
    const params = new URLSearchParams(searchParams);
    params.set('exportPage', String(next.page ?? page));
    params.set('exportSize', String(next.size ?? size));
    setSearchParams(params);
  }

  return (
    <Card className="trusted-export-card">
      <div className="trusted-export-header">
        <div>
          <Typography.Title heading={5}>Trusted Export(可信训练数据集)</Typography.Title>
          <Typography.Text type="tertiary">将 task 的 source facts 物化为 canonical export 快照。</Typography.Text>
        </div>
      </div>

      <div className="trusted-export-toolbar">
        <Typography.Text type="tertiary">
          共 {exportsQuery.data?.total ?? 0} 个快照{selectedIds.length > 0 ? `,已选 ${selectedIds.length}/2` : ''}
        </Typography.Text>
        <Space>
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

      {exportsQuery.isLoading ? <Spin /> : null}
      {exportsQuery.isError ? (
        <Empty title="导出列表加载失败" description={exportsQuery.error instanceof Error ? exportsQuery.error.message : '请稍后重试。'} />
      ) : null}
      {!exportsQuery.isLoading && !exportsQuery.isError && items.length === 0 ? (
        <Empty title="尚未导出" description={'点击"导出"按钮创建可信训练数据集快照。'} />
      ) : null}
      {items.length > 0 ? (
        <>
          <Table columns={columns} dataSource={items} rowKey="id" pagination={false} size="small" />
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
        </>
      ) : null}

      {diffModalOpen && baseSnapshotId != null && compareSnapshotId != null ? (
        <ExportSnapshotDiffModal baseSnapshotId={baseSnapshotId} compareSnapshotId={compareSnapshotId} onClose={() => setDiffModalOpen(false)} />
      ) : null}
    </Card>
  );
}

function parsePositiveInt(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function shortHash(value?: string | null) {
  return value ? `${value.slice(0, 16)}...` : '-';
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}
