import { Button, Card, Spin, Toast, Typography } from '@douyinfe/semi-ui';
import { IconBolt, IconRefresh } from '@douyinfe/semi-icons';
import { EmptyState } from '../../shared/ui';
import { EnqueueTaskAiPrereviewsFailure, useEnqueueTaskAiPrereviewsMutation } from './useEnqueueTaskAiPrereviewsMutation';
import { useTaskAiPrereviewSummaryQuery, type TaskAiPrereviewSummary } from './useTaskAiPrereviewSummaryQuery';

type TaskAiPrereviewPanelProps = {
  taskId: number;
};

const STAT_META: Array<{
  key: keyof Pick<TaskAiPrereviewSummary, 'pendingCount' | 'processingCount' | 'completedCount' | 'failedCount'>;
  label: string;
  tone: 'neutral' | 'info' | 'success' | 'danger';
}> = [
  { key: 'pendingCount', label: '待预审', tone: 'neutral' },
  { key: 'processingCount', label: '预审中', tone: 'info' },
  { key: 'completedCount', label: '预审完成', tone: 'success' },
  { key: 'failedCount', label: '预审失败', tone: 'danger' },
];

export function TaskAiPrereviewPanel({ taskId }: TaskAiPrereviewPanelProps) {
  const summaryQuery = useTaskAiPrereviewSummaryQuery(taskId);
  const enqueueMutation = useEnqueueTaskAiPrereviewsMutation(taskId);
  const summary = summaryQuery.data;
  const enqueueableCount = Number(summary?.enqueueableCount ?? 0);
  const totalCount = Number(summary?.totalCount ?? 0);
  const completedCount = Number(summary?.completedCount ?? 0);
  const progress = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;
  const queuedCount = summary ? Number(summary.pendingCount ?? 0) + Number(summary.processingCount ?? 0) : 0;
  const isWaitingForAgent = Boolean(summary && summary.enqueueableCount <= 0 && queuedCount > 0);
  const actionLabel = actionText(summary);

  async function enqueue() {
    if (enqueueableCount <= 0) {
      Toast.info('暂无需要发起的 AI 预审');
      return;
    }
    try {
      const result = await enqueueMutation.mutateAsync();
      if (result.enqueuedCount > 0) {
        Toast.success(`已发起 ${result.enqueuedCount} 条 AI 预审`);
      } else {
        Toast.info('暂无需要发起的 AI 预审');
      }
    } catch (error) {
      const failure = error instanceof EnqueueTaskAiPrereviewsFailure ? error : null;
      Toast.error(failure?.userMessage ?? 'AI 预审发起失败,请稍后重试');
    }
  }

  return (
    <Card className="task-ai-prereview-card" bordered={false}>
      <div className="task-ai-prereview-card__head">
        <div>
          <Typography.Title heading={5}>AI 预审</Typography.Title>
          <Typography.Text type="tertiary">覆盖全部已提交记录，异步进入 AI 预审队列。</Typography.Text>
        </div>
        <div className="task-ai-prereview-card__actions">
          <Button
            icon={<IconRefresh />}
            size="small"
            onClick={() => summaryQuery.refetch()}
            loading={summaryQuery.isFetching && !summaryQuery.isLoading}
          >
            刷新
          </Button>
          <Button
            icon={<IconBolt />}
            theme="solid"
            type="primary"
            loading={enqueueMutation.isPending}
            disabled={summaryQuery.isLoading || summaryQuery.isError || enqueueableCount <= 0}
            onClick={enqueue}
          >
            {actionLabel}
          </Button>
        </div>
      </div>

      {summaryQuery.isLoading ? (
        <div className="task-ai-prereview-card__state">
          <Spin />
        </div>
      ) : null}

      {summaryQuery.isError ? (
        <EmptyState variant="inline" title="AI 预审进度加载失败" description="刷新后重试。" />
      ) : null}

      {!summaryQuery.isLoading && !summaryQuery.isError && summary ? (
        <>
          <div className="task-ai-prereview-meter" aria-label="AI 预审完成度">
            <span style={{ width: `${progress}%` }} />
          </div>
          <div className="task-ai-prereview-grid">
            {STAT_META.map((stat) => (
              <div key={stat.key} className={`task-ai-prereview-stat task-ai-prereview-stat--${stat.tone}`}>
                <span>{stat.label}</span>
                <strong>{summary[stat.key]}</strong>
              </div>
            ))}
          </div>
          <div className="task-ai-prereview-card__foot">
            <Typography.Text type="tertiary">
              {isWaitingForAgent
                ? `已排队 ${queuedCount} / 总提交 ${summary.totalCount}, Agent 启动后会自动处理`
                : `可发起 ${summary.enqueueableCount} / 总提交 ${summary.totalCount}`}
            </Typography.Text>
          </div>
        </>
      ) : null}
    </Card>
  );
}

function actionText(summary?: TaskAiPrereviewSummary) {
  if (!summary) {
    return '暂无可发起';
  }
  const queuedCount = Number(summary.pendingCount ?? 0) + Number(summary.processingCount ?? 0);
  if (summary.enqueueableCount <= 0 && queuedCount > 0) {
    return '等待 Agent';
  }
  if (summary.enqueueableCount <= 0) {
    return '暂无可发起';
  }
  if (summary.failedCount > 0 && summary.pendingCount === 0) {
    return `重试失败项 ${summary.enqueueableCount} 条`;
  }
  return `一键 AI 预审 ${summary.enqueueableCount} 条`;
}
