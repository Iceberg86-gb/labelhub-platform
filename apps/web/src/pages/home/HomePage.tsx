import { IconUserCircle } from '@douyinfe/semi-icons';
import { Spin, Typography } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useReviewerQueueQuery } from '../../features/quality/useReviewerQueueQuery';
import { useMarketplaceQuery } from '../../features/labeling/useMarketplaceQuery';
import { useMySessionsQuery } from '../../features/labeling/useMySessionsQuery';
import { useSchemasQuery } from '../../features/schema-design/useSchemasQuery';
import { useTasksQuery } from '../../features/task/list-tasks/useTasksQuery';
import { getUser } from '../../shared/api/auth-storage';
import { roleRoutePriority } from '../../shared/auth/roleRoutes';
import {
  IconAiAssist,
  IconAnnotationWorkbench,
  IconDesignerBlock,
  IconReviewFlow,
  IconStatusFlow,
  IconTask,
  IconVersionHistory,
} from '../../shared/ui/LabelHubIcons';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const entryMeta = {
  '/owner/tasks': {
    title: '任务管理',
    description: '创建、配置和发布标注任务',
    icon: IconTask,
    tone: 'primary',
  },
  '/owner/schemas': {
    title: 'Designer 画布',
    description: '搭建字段、容器和联动规则',
    icon: IconDesignerBlock,
    tone: 'accent',
  },
  '/owner/llm': {
    title: 'LLM 接入',
    description: '配置 API Key、模型和辅助范围',
    icon: IconAiAssist,
    tone: 'info',
  },
  '/owner/audit-logs': {
    title: '审计日志',
    description: '追踪操作留痕和版本记录',
    icon: IconVersionHistory,
    tone: 'muted',
  },
  '/admin/user-roles': {
    title: '用户权限',
    description: '授予或撤销审核角色',
    icon: IconUserCircle,
    tone: 'muted',
  },
  '/labeler/marketplace': {
    title: '任务广场',
    description: '领取可作答任务',
    icon: IconTask,
    tone: 'info',
  },
  '/labeler/my': {
    title: '标注工作台',
    description: '继续未完成数据和提交记录',
    icon: IconAnnotationWorkbench,
    tone: 'info',
  },
  '/reviewer/submissions': {
    title: '审核队列',
    description: '处理人工初审、通过或打回',
    icon: IconReviewFlow,
    tone: 'warning',
  },
  '/reviewer/submissions?reviewLevel=senior_reviewer': {
    title: '复核队列',
    description: '高级审核终态裁决',
    icon: IconStatusFlow,
    tone: 'warning',
  },
} as const;

type MetricTone = 'primary' | 'accent' | 'info' | 'warning' | 'success' | 'neutral';

type DashboardMetricProps = {
  caption: string;
  label: string;
  tone?: MetricTone;
  value?: number;
};

type DashboardPanelProps = {
  children: ReactNode;
  description: string;
  title: string;
};

type DashboardListProps = {
  emptyText: string;
  items: Array<{
    href: string;
    meta: string;
    title: string;
  }>;
  title: string;
};

function formatCount(value?: number) {
  return typeof value === 'number' ? value.toLocaleString('zh-CN') : '-';
}

function DashboardMetric({ caption, label, tone = 'neutral', value }: DashboardMetricProps) {
  return (
    <div className={`home-dashboard-metric home-dashboard-metric--${tone}`}>
      <span>{label}</span>
      <strong>{formatCount(value)}</strong>
      <small>{caption}</small>
    </div>
  );
}

function DashboardPanel({ children, description, title }: DashboardPanelProps) {
  return (
    <section className="home-dashboard-panel">
      <div className="home-dashboard-panel__header">
        <span>{title}</span>
        <small>{description}</small>
      </div>
      {children}
    </section>
  );
}

function DashboardList({ emptyText, items, title }: DashboardListProps) {
  return (
    <div className="home-dashboard-list">
      <div className="home-dashboard-list__title">{title}</div>
      {items.length ? (
        <div className="home-dashboard-list__items">
          {items.map((item) => (
            <Link className="home-dashboard-row" to={item.href} key={`${item.href}-${item.title}`}>
              <span>{item.title}</span>
              <small>{item.meta}</small>
            </Link>
          ))}
        </div>
      ) : (
        <div className="home-dashboard-empty">{emptyText}</div>
      )}
    </div>
  );
}

export function HomePage() {
  const user = getUser();
  const roles = user?.roles ?? [];
  const hasOwner = roles.includes('OWNER');
  const hasLabeler = roles.includes('LABELER');
  const hasReviewer = roles.includes('REVIEWER');
  const hasSeniorReviewer = roles.includes('SENIOR_REVIEWER');
  const hasAnyReviewer = hasReviewer || hasSeniorReviewer;

  const ownerTasks = useTasksQuery({ page: 1, size: 5, enabled: hasOwner });
  const ownerPublishedTasks = useTasksQuery({ page: 1, size: 1, status: 'published', enabled: hasOwner });
  const ownerDraftTasks = useTasksQuery({ page: 1, size: 1, status: 'draft', enabled: hasOwner });
  const ownerSchemas = useSchemasQuery({ page: 1, size: 5, enabled: hasOwner });
  const marketplace = useMarketplaceQuery({ page: 1, size: 5, enabled: hasLabeler });
  const mySessions = useMySessionsQuery({ page: 1, size: 5, enabled: hasLabeler });
  const inProgressSessions = useMySessionsQuery({ page: 1, size: 1, workStatus: 'in_progress', enabled: hasLabeler });
  const returnedSessions = useMySessionsQuery({ page: 1, size: 1, workStatus: 'returned_for_revision', enabled: hasLabeler });
  const reviewerQueue = useReviewerQueueQuery({ page: 1, size: 5, reviewLevel: 'reviewer', enabled: hasReviewer });
  const seniorReviewerQueue = useReviewerQueueQuery({
    page: 1,
    size: 5,
    reviewLevel: 'senior_reviewer',
    enabled: hasSeniorReviewer,
  });

  const activeQueries = [
    ownerTasks,
    ownerPublishedTasks,
    ownerDraftTasks,
    ownerSchemas,
    marketplace,
    mySessions,
    inProgressSessions,
    returnedSessions,
    reviewerQueue,
    seniorReviewerQueue,
  ].filter((query) => query.fetchStatus !== 'idle' || query.data);
  const isDashboardLoading = activeQueries.some((query) => query.isLoading);
  const hasDashboardError = activeQueries.some((query) => query.isError);
  const entries = roleRoutePriority
    .filter((item) => roles.includes(item.role))
    .map((item) => ({
      ...item,
      meta: entryMeta[item.path as keyof typeof entryMeta],
    }))
    .filter((entry) => entry.meta);

  const ownerTaskRows = (ownerTasks.data?.items ?? []).map((task) => ({
    href: `/owner/tasks/${task.id}`,
    title: task.title,
    meta: `${task.status} · 已领 ${task.quotaClaimed}/${task.quotaTotal}`,
  }));
  const labelerTaskRows = (marketplace.data?.items ?? []).map((task) => ({
    href: `/labeler/marketplace`,
    title: task.title,
    meta: `可领 ${task.availableItemCount} · 已领 ${task.quotaClaimed}/${task.quotaTotal}`,
  }));
  const sessionRows = (mySessions.data?.items ?? []).map((session) => ({
    href: `/labeler/sessions/${session.id}`,
    title: `Session #${session.id}`,
    meta: `任务 #${session.taskId} · ${session.workStatus}`,
  }));
  const reviewerSourceItems = [
    ...(hasReviewer ? reviewerQueue.data?.items ?? [] : []),
    ...(hasSeniorReviewer ? seniorReviewerQueue.data?.items ?? [] : []),
  ];
  const reviewerRows = (reviewerSourceItems ?? []).map((submission) => ({
    href: `/reviewer/submissions/${submission.id}`,
    title: submission.taskTitle,
    meta: `${submission.reviewLevel} · AI ${submission.aiRecommendation ?? 'manual'}`,
  }));

  return (
    <section className="home-page" aria-label="LabelHub workspace home">
      <header className="home-hero">
        <div className="home-hero__copy">
          <Typography.Text className="home-hero__eyebrow">欢迎回来</Typography.Text>
          <Typography.Title heading={1} className="home-hero__title">
            从真实数据开始今天的标注流转
          </Typography.Title>
          <Typography.Text className="home-hero__subtitle">
            LabelHub 将当前账号可访问的任务、标注和审核数据汇总成工作台看板，入口仍按角色权限并集展示。
          </Typography.Text>
        </div>

        <div className="home-role-tags" aria-label="当前角色权限">
          {roles.map((role) => (
            <RoleBadge key={role} role={role} />
          ))}
        </div>
      </header>

      <section className="home-dashboard" aria-label="实时数据看板">
        <div className="home-dashboard__header">
          <div>
            <Typography.Text className="home-dashboard__eyebrow">实时数据看板</Typography.Text>
            <Typography.Title heading={3} className="home-dashboard__title">
              当前账号工作概览
            </Typography.Title>
          </div>
          <span className={hasDashboardError ? 'home-dashboard__status is-error' : 'home-dashboard__status'}>
            {hasDashboardError ? '部分数据加载失败' : isDashboardLoading ? '正在同步' : '数据已同步'}
          </span>
        </div>

        {isDashboardLoading ? (
          <div className="home-dashboard-loading">
            <Spin size="large" />
          </div>
        ) : null}

        <div className="home-dashboard__content">
          {hasOwner ? (
            <DashboardPanel title="任务负责人" description="任务配置、Schema 搭建与发布状态">
              <div className="home-dashboard-metrics">
                <DashboardMetric label="任务总数" value={ownerTasks.data?.total} caption="全部任务" tone="primary" />
                <DashboardMetric label="已发布" value={ownerPublishedTasks.data?.total} caption="可领取任务" tone="accent" />
                <DashboardMetric label="草稿" value={ownerDraftTasks.data?.total} caption="待完善配置" tone="neutral" />
                <DashboardMetric label="Schema" value={ownerSchemas.data?.total} caption="标注表单" tone="info" />
              </div>
              <DashboardList title="最近任务" items={ownerTaskRows} emptyText="暂无任务，先创建一个任务。" />
            </DashboardPanel>
          ) : null}

          {hasLabeler ? (
            <DashboardPanel title="标注员" description="可领取任务、进行中数据与返修提醒">
              <div className="home-dashboard-metrics">
                <DashboardMetric label="可领取" value={marketplace.data?.total} caption="任务广场" tone="info" />
                <DashboardMetric label="我的数据" value={mySessions.data?.total} caption="全部会话" tone="primary" />
                <DashboardMetric label="进行中" value={inProgressSessions.data?.total} caption="待提交" tone="accent" />
                <DashboardMetric label="待修改" value={returnedSessions.data?.total} caption="审核打回" tone="warning" />
              </div>
              <div className="home-dashboard-split">
                <DashboardList title="可领取任务" items={labelerTaskRows} emptyText="暂无可领取任务。" />
                <DashboardList title="我的最近数据" items={sessionRows} emptyText="暂无已领取数据。" />
              </div>
            </DashboardPanel>
          ) : null}

          {hasAnyReviewer ? (
            <DashboardPanel title="人工审核" description="AI 证据仅作参考，人工裁决仍是主流程">
              <div className="home-dashboard-metrics">
                {hasReviewer ? (
                  <DashboardMetric label="初审待处理" value={reviewerQueue.data?.total} caption="Reviewer 队列" tone="warning" />
                ) : null}
                {hasSeniorReviewer ? (
                  <DashboardMetric label="复核待处理" value={seniorReviewerQueue.data?.total} caption="Senior 队列" tone="accent" />
                ) : null}
                <DashboardMetric label="AI 辅助" value={reviewerRows.length} caption="当前页证据项" tone="neutral" />
              </div>
              <DashboardList title="待审样本" items={reviewerRows} emptyText="暂无待审样本。" />
            </DashboardPanel>
          ) : null}
        </div>
      </section>

      <section className="home-entry-grid" aria-label="可用入口">
        {entries.map(({ role, path, meta }) => {
          const Icon = meta.icon;

          return (
            <Link className={`home-entry-card home-entry-card--${meta.tone}`} to={path} key={`${role}-${path}`}>
              <span className="home-entry-card__icon">
                <Icon />
              </span>
              <span>
                <strong>{meta.title}</strong>
                <span>{meta.description}</span>
              </span>
            </Link>
          );
        })}
      </section>
    </section>
  );
}
