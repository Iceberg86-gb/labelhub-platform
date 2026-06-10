import { Button, Pagination, Spin, Table, Typography } from '@douyinfe/semi-ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import { EmptyState, StatusBadge } from '../../shared/ui';
import { useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { OwnerSubmissionSummary } from '../../entities/submission/ownerTypes';
import { PrereviewStatusTag } from '../../entities/submission/PrereviewStatusTag';
import { useOwnerTaskSubmissionsQuery } from './useOwnerTaskSubmissionsQuery';

type OwnerTaskSubmissionsSectionProps = {
  taskId: number;
};

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const STATUS_LABELS: Record<string, string> = {
  submitted: '已提交',
  under_ai_review: '已提交',
};

export function OwnerTaskSubmissionsSection({ taskId }: OwnerTaskSubmissionsSectionProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('submissionPage')) ?? 1;
  const size = parsePositiveInt(searchParams.get('submissionSize')) ?? 20;
  const submissionsQuery = useOwnerTaskSubmissionsQuery(taskId, { page, size });
  const items = submissionsQuery.data?.items ?? [];

  const columns = useMemo(
    () => [
      {
        title: 'Submission',
        dataIndex: 'id',
        render: (_: unknown, record: OwnerSubmissionSummary) => (
          <div className="owner-submission-cell">
            <Typography.Text strong>#{record.id}</Typography.Text>
            <Typography.Text type="tertiary">Session #{record.sessionId}</Typography.Text>
          </div>
        ),
      },
      { title: 'Labeler', dataIndex: 'labelerId', width: 110, render: (value: number) => `#${value}` },
      { title: 'Schema', dataIndex: 'schemaVersionId', width: 110, render: (value: number) => `#${value}` },
      { title: '状态', dataIndex: 'status', width: 110, render: (value: string) => <StatusBadge tone="success">{statusLabel(value)}</StatusBadge> },
      { title: 'AI 预审', width: 120, render: (_: unknown, record: OwnerSubmissionSummary) => <PrereviewStatusTag status={record.prereviewStatus} signals={record.prereviewSignals} /> },
      { title: '提交时间', dataIndex: 'createdAt', width: 150, render: (value: string) => formatDateTime(value) },
      {
        title: '操作',
        width: 120,
        render: (_: unknown, record: OwnerSubmissionSummary) => (
          <Button size="small" onClick={() => navigate(`/owner/tasks/${taskId}/submissions/${record.id}`)}>
            查看详情
          </Button>
        ),
      },
    ],
    [navigate, taskId],
  );

  function updateParams(next: { page?: number; size?: number }) {
    const params = new URLSearchParams(searchParams);
    params.set('submissionPage', String(next.page ?? page));
    params.set('submissionSize', String(next.size ?? size));
    setSearchParams(params);
  }

  return (
    <div className="owner-submissions-section">
      <div className="dataset-list-toolbar">
        <Typography.Text type="tertiary">共 {submissionsQuery.data?.total ?? 0} 条提交记录</Typography.Text>
        <Button icon={<IconRefresh />} size="small" onClick={() => submissionsQuery.refetch()} loading={submissionsQuery.isFetching}>
          刷新
        </Button>
      </div>

      {submissionsQuery.isLoading ? <Spin /> : null}
      {submissionsQuery.isError ? (
        <EmptyState variant="inline" title="提交记录加载失败" description={submissionsQuery.error instanceof Error ? submissionsQuery.error.message : '请稍后重试。'} />
      ) : null}
      {!submissionsQuery.isLoading && !submissionsQuery.isError && items.length === 0 ? (
        <EmptyState variant="inline" title="暂无提交记录" description="Labeler 提交后,Owner 可在这里进入 AI 预审。" />
      ) : null}
      {items.length > 0 ? (
        <>
          <Table columns={columns} dataSource={items} rowKey="id" pagination={false} />
          <div className="task-pagination">
            <Pagination
              total={submissionsQuery.data?.total ?? 0}
              currentPage={page}
              pageSize={size}
              showSizeChanger
              onPageChange={(nextPage) => updateParams({ page: nextPage })}
              onPageSizeChange={(nextSize) => updateParams({ page: 1, size: nextSize })}
            />
          </div>
        </>
      ) : null}
    </div>
  );
}

function parsePositiveInt(value: string | null) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function formatDateTime(value?: string) {
  return value ? dateFormatter.format(new Date(value)) : '-';
}

function statusLabel(status: string) {
  return STATUS_LABELS[status] ?? status;
}
