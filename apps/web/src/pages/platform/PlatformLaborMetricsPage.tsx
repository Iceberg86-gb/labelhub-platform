import { Button, Spin, Table, Typography } from '@douyinfe/semi-ui';
import { EmptyState } from '../../shared/ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import { usePlatformLaborMetricsQuery, type PlatformLaborMetrics } from '../../features/platform-metrics/usePlatformLaborMetricsQuery';

type LaborRow = PlatformLaborMetrics['submissions'][number];

const emptyRework: PlatformLaborMetrics['rework'] = {
  supersededSubmissionCount: 0,
  multiRoundReviewActionCount: 0,
  returnedForRevisionSubmissionCount: 0,
};

function formatCount(value?: number) {
  return typeof value === 'number' ? value.toLocaleString('zh-CN') : '0';
}

function formatTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
}

function personLabel(row: LaborRow) {
  return row.displayName ?? row.username ?? `USER#${row.userId}`;
}

function MetricCard({ caption, label, value }: { caption: string; label: string; value: number }) {
  return (
    <div className="platform-metrics-card">
      <span>{label}</span>
      <strong>{formatCount(value)}</strong>
      <small>{caption}</small>
    </div>
  );
}

function LaborTable({ rows, type }: { rows: LaborRow[]; type: 'submission' | 'review' }) {
  if (!rows.length) {
    return <div className="platform-metrics-empty-row">暂无记录</div>;
  }

  const columns = [
    {
      title: '账号',
      render: (_: unknown, row: LaborRow) => (
        <div className="platform-metrics-person">
          <Typography.Text strong>{personLabel(row)}</Typography.Text>
          <Typography.Text type="tertiary">@{row.username ?? row.userId}</Typography.Text>
        </div>
      ),
    },
    { title: type === 'submission' ? '提交数' : '审核数', dataIndex: 'count', width: 120, render: (value: number) => formatCount(value) },
    ...(type === 'review'
      ? [
          { title: '初审', dataIndex: 'initialReviewCount', width: 100, render: (value: number) => formatCount(value) },
          { title: '复核', dataIndex: 'seniorReviewCount', width: 100, render: (value: number) => formatCount(value) },
          { title: '通过', dataIndex: 'approveActionCount', width: 100, render: (value: number) => formatCount(value) },
          { title: '打回', dataIndex: 'returnActionCount', width: 100, render: (value: number) => formatCount(value) },
          { title: '拒绝', dataIndex: 'rejectActionCount', width: 100, render: (value: number) => formatCount(value) },
        ]
      : []),
  ];

  return <Table className="platform-metrics-table" columns={columns} dataSource={rows} rowKey={(row) => `${type}-${row?.userId ?? 'user'}`} pagination={false} />;
}

export function PlatformLaborMetricsPage() {
  const metrics = usePlatformLaborMetricsQuery();
  const data = metrics.data;
  const rework = data?.rework ?? emptyRework;

  return (
    <section className="platform-metrics-page" aria-label="Platform labor metrics">
      <header className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">PA 事实计量</Typography.Text>
          <Typography.Title heading={2}>人力计量</Typography.Title>
          <Typography.Text type="tertiary">只统计提交、审核和返工信号的数量，不读取标注答案或审核意见内容。</Typography.Text>
        </div>
        <Button icon={<IconRefresh />} onClick={() => metrics.refetch()} loading={metrics.isFetching}>
          刷新
        </Button>
      </header>

      {metrics.isLoading ? (
        <div className="task-state-panel">
          <Spin size="large" />
        </div>
      ) : null}

      {metrics.isError ? (
        <div className="task-state-panel">
          <EmptyState variant="inline" title="人力计量数据加载失败" description={metrics.error instanceof Error ? metrics.error.message : '请稍后重试。'} />
          <Button onClick={() => metrics.refetch()}>重新加载</Button>
        </div>
      ) : null}

      {!metrics.isLoading && !metrics.isError ? (
        <>
          {data?.empty ? (
            <div className="task-state-panel platform-metrics-empty">
              <EmptyState variant="inline" title="暂无记录" description="当前没有可展示的提交、审核或返工计量。这里不会把无记录渲染成统计值为 0。" />
            </div>
          ) : null}

          <section className="platform-metrics-summary" aria-label="rework metrics">
            <MetricCard label="被取代" value={rework.supersededSubmissionCount} caption="submissions.superseded_by_id 不为空" />
            <MetricCard label="多轮审核" value={rework.multiRoundReviewActionCount} caption="review_actions.round_no 大于 1" />
            <MetricCard label="被打回" value={rework.returnedForRevisionSubmissionCount} caption="submissions.status 为 returned_for_revision" />
          </section>

          <div className="platform-metrics-grid">
            <section className="platform-metrics-panel">
              <div className="platform-metrics-panel__header">
                <Typography.Title heading={4}>提交计量</Typography.Title>
                <Typography.Text type="tertiary">按 labeler_id 分组，最近同步 {formatTime(data?.generatedAt)}</Typography.Text>
              </div>
              <LaborTable rows={data?.submissions ?? []} type="submission" />
            </section>

            <section className="platform-metrics-panel">
              <div className="platform-metrics-panel__header">
                <Typography.Title heading={4}>审核计量</Typography.Title>
                <Typography.Text type="tertiary">按 reviewer_id 分组，并拆分初审、复核和动作次数。</Typography.Text>
              </div>
              <LaborTable rows={data?.reviews ?? []} type="review" />
            </section>
          </div>
        </>
      ) : null}
    </section>
  );
}
