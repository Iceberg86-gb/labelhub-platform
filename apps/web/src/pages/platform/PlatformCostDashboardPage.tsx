import { Button, Spin, Table, Typography } from '@douyinfe/semi-ui';
import { EmptyState } from '../../shared/ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import { usePlatformCostMetricsQuery, type PlatformCostMetrics } from '../../features/platform-cost/usePlatformCostMetricsQuery';

type Bucket = PlatformCostMetrics['dailyTrend'][number];

const emptyOverview: PlatformCostMetrics['overview'] = {
  callCount: 0,
  totalTokens: 0,
  totalCost: 0,
  attributedCallCount: 0,
  attributedTokens: 0,
  attributedCost: 0,
  unattributedCallCount: 0,
  unattributedTokens: 0,
  unattributedCost: 0,
};

function formatCount(value: number) {
  return value.toLocaleString('zh-CN');
}

function formatCost(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 6,
  }).format(value);
}

function formatTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
}

function metricRows(metrics?: PlatformCostMetrics) {
  const overview = metrics?.overview ?? emptyOverview;
  return [
    { label: '总 token', value: formatCount(overview.totalTokens), caption: `${formatCount(overview.callCount)} 次调用` },
    { label: '总成本', value: formatCost(overview.totalCost), caption: '来自 ai_calls.cost_decimal' },
    { label: '已归集 token', value: formatCount(overview.attributedTokens), caption: `${formatCount(overview.attributedCallCount)} 次调用` },
    { label: '未归集', value: formatCount(overview.unattributedTokens), caption: `${formatCost(overview.unattributedCost)} · ${formatCount(overview.unattributedCallCount)} 次` },
  ];
}

function maxTokens(rows: Bucket[]) {
  return Math.max(1, ...rows.map((row) => row.totalTokens));
}

function TrendBars({ rows }: { rows: Bucket[] }) {
  const max = maxTokens(rows);
  if (!rows.length) {
    return <div className="platform-cost-empty-row">暂无按天记录</div>;
  }
  return (
    <div className="platform-cost-trend" aria-label="按天 token 趋势">
      {rows.map((row) => (
        <div className="platform-cost-trend__row" key={row.date ?? 'unknown'}>
          <span>{row.date ?? '-'}</span>
          <div className="platform-cost-trend__track" aria-hidden>
            <i style={{ width: `${Math.max(4, Math.round((row.totalTokens / max) * 100))}%` }} />
          </div>
          <strong>{formatCount(row.totalTokens)}</strong>
          <small>{formatCost(row.totalCost)}</small>
        </div>
      ))}
    </div>
  );
}

function CostTable({ emptyText, labelColumn, rows }: { emptyText: string; labelColumn: string; rows: Bucket[] }) {
  const columns = [
    {
      title: labelColumn,
      render: (_: unknown, row: Bucket) => (
        <div className="platform-cost-group-cell">
          <Typography.Text strong>{row.groupName ?? row.modelName ?? row.date ?? '-'}</Typography.Text>
          {row.modelProvider ? <Typography.Text type="tertiary">{row.modelProvider}</Typography.Text> : null}
          {row.groupId ? <Typography.Text type="tertiary">ID {row.groupId}</Typography.Text> : null}
        </div>
      ),
    },
    { title: '调用次数', dataIndex: 'callCount', width: 120, render: (value: number) => formatCount(value) },
    { title: 'Token', dataIndex: 'totalTokens', width: 140, render: (value: number) => formatCount(value) },
    { title: '成本', dataIndex: 'totalCost', width: 140, render: (value: number) => formatCost(value) },
  ];

  if (!rows.length) {
    return <div className="platform-cost-empty-row">{emptyText}</div>;
  }

  return <Table className="platform-cost-table" columns={columns} dataSource={rows} rowKey={(row) => `${row?.groupId ?? row?.modelName ?? row?.date ?? 'metric'}`} pagination={false} />;
}

export function PlatformCostDashboardPage() {
  const metrics = usePlatformCostMetricsQuery();
  const data = metrics.data;

  return (
    <section className="platform-cost-page" aria-label="Platform token cost dashboard">
      <header className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">PA 成本事实</Typography.Text>
          <Typography.Title heading={2}>Token 成本管控</Typography.Title>
          <Typography.Text type="tertiary">按 ai_calls 已记录的 token 与 cost_decimal 展示事实计量，不重算单价。</Typography.Text>
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
          <EmptyState variant="inline" title="Token 成本数据加载失败" description={metrics.error instanceof Error ? metrics.error.message : '请稍后重试。'} />
          <Button onClick={() => metrics.refetch()}>重新加载</Button>
        </div>
      ) : null}

      {!metrics.isLoading && !metrics.isError ? (
        <>
          {data?.empty ? (
            <div className="task-state-panel platform-cost-empty">
              <EmptyState variant="inline" title="暂无 AI 调用记录" description="当前没有 ai_calls 事实数据。这里不会把无记录渲染成统计值为 0。" />
            </div>
          ) : null}

          <section className="platform-cost-summary" aria-label="Token and cost summary">
            {metricRows(data).map((item) => (
              <div className="platform-cost-metric" key={item.label}>
                <span>{item.label}</span>
                <strong>{item.value}</strong>
                <small>{item.caption}</small>
              </div>
            ))}
          </section>

          <div className="platform-cost-grid">
            <section className="platform-cost-panel">
              <div className="platform-cost-panel__header">
                <Typography.Title heading={4}>按天趋势</Typography.Title>
                <Typography.Text type="tertiary">30 秒轮询刷新，最近同步 {formatTime(data?.generatedAt)}</Typography.Text>
              </div>
              <TrendBars rows={data?.dailyTrend ?? []} />
            </section>

            <section className="platform-cost-panel">
              <div className="platform-cost-panel__header">
                <Typography.Title heading={4}>按模型归集</Typography.Title>
                <Typography.Text type="tertiary">按 provider 与 model 汇总 token、成本和调用次数。</Typography.Text>
              </div>
              <CostTable labelColumn="模型" emptyText="暂无模型归集记录" rows={data?.modelBreakdown ?? []} />
            </section>

            <section className="platform-cost-panel">
              <div className="platform-cost-panel__header">
                <Typography.Title heading={4}>按任务归集</Typography.Title>
                <Typography.Text type="tertiary">仅统计可经 submission 关联到 task 的 AI 调用。</Typography.Text>
              </div>
              <CostTable labelColumn="任务" emptyText="暂无任务归集记录" rows={data?.taskBreakdown ?? []} />
            </section>

            <section className="platform-cost-panel">
              <div className="platform-cost-panel__header">
                <Typography.Title heading={4}>按 Owner 归集</Typography.Title>
                <Typography.Text type="tertiary">经 task.owner_id 关联 Owner；未归集部分见总览。</Typography.Text>
              </div>
              <CostTable labelColumn="Owner" emptyText="暂无 Owner 归集记录" rows={data?.ownerBreakdown ?? []} />
            </section>
          </div>
        </>
      ) : null}
    </section>
  );
}
