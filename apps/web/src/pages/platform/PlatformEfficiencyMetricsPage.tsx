import { Button, Spin, Typography } from '@douyinfe/semi-ui';
import { EmptyState } from '../../shared/ui';
import { IconRefresh } from '@douyinfe/semi-icons';
import { usePlatformEfficiencyMetricsQuery, type PlatformEfficiencyMetrics } from '../../features/platform-metrics/usePlatformEfficiencyMetricsQuery';

const emptyIdempotency: PlatformEfficiencyMetrics['idempotency'] = {
  callCount: 0,
  uniqueKeyCount: 0,
  duplicateKeyCount: 0,
  cacheHitTokens: 0,
};

const emptyUnitCost: PlatformEfficiencyMetrics['unitCost'] = {
  totalCost: 0,
  distinctSubmissionCount: 0,
  distinctDatasetItemCount: 0,
  costPerSubmission: 0,
  costPerDatasetItem: 0,
};

function formatCount(value?: number) {
  return typeof value === 'number' ? value.toLocaleString('zh-CN') : '0';
}

function formatCost(value?: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 6,
  }).format(value ?? 0);
}

function formatTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
}

function FactCard({ caption, label, value }: { caption: string; label: string; value: string }) {
  return (
    <div className="platform-metrics-card">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{caption}</small>
    </div>
  );
}

export function PlatformEfficiencyMetricsPage() {
  const metrics = usePlatformEfficiencyMetricsQuery();
  const data = metrics.data;
  const idempotency = data?.idempotency ?? emptyIdempotency;
  const unitCost = data?.unitCost ?? emptyUnitCost;

  return (
    <section className="platform-metrics-page" aria-label="Platform token reuse metrics">
      <header className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">PA 事实计量</Typography.Text>
          <Typography.Title heading={2}>Token 复用计量</Typography.Title>
          <Typography.Text type="tertiary">只展示 idempotency、cache hit token 与单位数据成本的事实数字，成本来自 ai_calls.cost_decimal。</Typography.Text>
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
          <EmptyState variant="inline" title="Token 复用计量加载失败" description={metrics.error instanceof Error ? metrics.error.message : '请稍后重试。'} />
          <Button onClick={() => metrics.refetch()}>重新加载</Button>
        </div>
      ) : null}

      {!metrics.isLoading && !metrics.isError ? (
        <>
          {data?.empty ? (
            <div className="task-state-panel platform-metrics-empty">
              <EmptyState variant="inline" title="暂无记录" description="当前没有 ai_calls 事实数据。这里不会把无记录渲染成统计值为 0。" />
            </div>
          ) : null}

          <section className="platform-metrics-summary" aria-label="token reuse metrics">
            <FactCard label="AI 调用数" value={formatCount(idempotency.callCount)} caption="ai_calls 总行数" />
            <FactCard label="idempotency key" value={formatCount(idempotency.uniqueKeyCount)} caption="唯一 idempotency_key 数" />
            <FactCard label="重复 key" value={formatCount(idempotency.duplicateKeyCount)} caption="同 key 重复记录数" />
            <FactCard label="cache hit token" value={formatCount(idempotency.cacheHitTokens)} caption="SUM(cache_hit_tokens)" />
          </section>

          <section className="platform-metrics-panel">
            <div className="platform-metrics-panel__header">
              <Typography.Title heading={4}>单位数据成本</Typography.Title>
              <Typography.Text type="tertiary">按 distinct submission_id 与 dataset_item_id 两种口径展示，最近同步 {formatTime(data?.generatedAt)}</Typography.Text>
            </div>
            <div className="platform-metrics-unit-grid">
              <FactCard label="总成本" value={formatCost(unitCost.totalCost)} caption="SUM(ai_calls.cost_decimal)" />
              <FactCard label="submission 口径" value={formatCost(unitCost.costPerSubmission)} caption={`${formatCount(unitCost.distinctSubmissionCount)} 个 submission`} />
              <FactCard label="dataset item 口径" value={formatCost(unitCost.costPerDatasetItem)} caption={`${formatCount(unitCost.distinctDatasetItemCount)} 个 dataset item`} />
            </div>
          </section>
        </>
      ) : null}
    </section>
  );
}
