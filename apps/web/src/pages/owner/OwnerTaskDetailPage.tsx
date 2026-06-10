import { Button, Card, Spin, Timeline, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { EmptyState } from '../../shared/ui';
import { IconArrowLeft, IconEdit, IconRefresh } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { TaskStatusBadge } from '../../entities/task/TaskStatusBadge';
import { DatasetUploadSection } from '../../features/dataset/DatasetUploadSection';
import { TrustedExportCard } from '../../features/export/TrustedExportCard';
import { AiReviewRuleEntryCard } from '../../features/ai-review-rule/AiReviewRuleEntryCard';
import { AiReviewRuleEditorDrawer } from '../../features/ai-review-rule/AiReviewRuleEditorDrawer';
import { buildTaskSchemaDraft, findSchemaForTask } from '../../features/schema-design/taskSchemaNavigation';
import { useCreateSchemaMutation } from '../../features/schema-design/useCreateSchemaMutation';
import { useSchemasQuery } from '../../features/schema-design/useSchemasQuery';
import { OwnerTaskSubmissionsSection } from '../../features/submission/OwnerTaskSubmissionsSection';
import type { TaskStatus } from '../../features/task/list-tasks/useTasksQuery';
import { TaskNextStepGuidance } from '../../features/task/task-detail/TaskNextStepGuidance';
import { useTaskDetailQuery } from '../../features/task/task-detail/useTaskDetailQuery';
import { useTaskTransitionsQuery, type TaskTransition } from '../../features/task/task-transitions/useTaskTransitionsQuery';
import { TransitionButtons } from '../../features/task/transition-task/TransitionButtons';
import { TransitionTaskModal } from '../../features/task/transition-task/TransitionTaskModal';
import { transitionLabels } from '../../features/task/transition-task/transitionRules';
import { EditTaskModal } from '../../features/task/update-task/EditTaskModal';
import { useTaskWorkflowProgressQuery, type TaskWorkflowProgress } from '../../features/task/workflow-progress/useTaskWorkflowProgressQuery';
import { getUser } from '../../shared/api/auth-storage';
import { RoleBadge } from '../../shared/ui/RoleBadge';
import { TaskAiPrereviewPanel } from '../../features/ai/TaskAiPrereviewPanel';

function parseTaskId(raw?: string) {
  const taskId = Number(raw);
  return Number.isInteger(taskId) && taskId > 0 ? taskId : null;
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function actorLabel(actorId: number) {
  return getUser()?.id === actorId ? '我' : `用户 #${actorId}`;
}

function transitionDot(status: TaskStatus) {
  return <span className={`timeline-dot timeline-dot-${status}`} />;
}

function TransitionTimeline({ transitions }: { transitions: TaskTransition[] }) {
  if (transitions.length === 0) {
    return <EmptyState variant="inline" title="暂无状态迁移" description="发布、暂停、恢复或结束任务后，迁移记录会出现在这里。" />;
  }

  return (
    <Timeline mode="left" aria-label="Task transition timeline">
      {transitions.map((transition) => (
        <Timeline.Item
          key={transition.id}
          dot={transitionDot(transition.toStatus)}
          time={formatDateTime(transition.createdAt)}
        >
          <div className="transition-item">
            <Typography.Text strong>
              {transition.fromStatus ? transitionLabels[transition.fromStatus] : '(初始)'} → {transitionLabels[transition.toStatus]}
            </Typography.Text>
            <Typography.Text>{transition.reason || '未填写说明'}</Typography.Text>
            <Typography.Text type="tertiary">{actorLabel(transition.actorId)}</Typography.Text>
          </div>
        </Timeline.Item>
      ))}
    </Timeline>
  );
}

const workflowProgressSteps: Array<{
  key: keyof Pick<
    TaskWorkflowProgress,
    'unclaimedCount'
    | 'labelingCount'
    | 'submittedCount'
    | 'aiPrereviewCompletedCount'
    | 'pendingReviewCount'
    | 'pendingSeniorReviewCount'
    | 'approvedCount'
    | 'rejectedCount'
  >;
  label: string;
  tone: 'neutral' | 'info' | 'accent' | 'warning' | 'success' | 'danger';
}> = [
  { key: 'unclaimedCount', label: '待领取', tone: 'neutral' },
  { key: 'labelingCount', label: '标注中', tone: 'info' },
  { key: 'submittedCount', label: '已提交', tone: 'accent' },
  { key: 'aiPrereviewCompletedCount', label: 'AI 预审完成', tone: 'success' },
  { key: 'pendingReviewCount', label: '待初审', tone: 'warning' },
  { key: 'pendingSeniorReviewCount', label: '待仲裁', tone: 'warning' },
  { key: 'approvedCount', label: '已通过', tone: 'success' },
  { key: 'rejectedCount', label: '已打回', tone: 'danger' },
];

function countText(value?: number) {
  return String(value ?? 0);
}

function claimedPercent(progress?: TaskWorkflowProgress) {
  if (!progress || progress.quotaTotal <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(0, (progress.quotaClaimed / progress.quotaTotal) * 100));
}

function WorkflowProgressCard({
  progress,
  isLoading,
  isError,
  onRetry,
}: {
  progress?: TaskWorkflowProgress;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}) {
  return (
    <Card className="owner-workflow-progress-card" aria-label="Owner task workflow progress">
      <div className="owner-workflow-progress-card__head">
        <div>
          <Typography.Title heading={5}>全过程进度</Typography.Title>
          <Typography.Text type="tertiary">标注、AI 预审与审核阶段聚合。</Typography.Text>
        </div>
        <div className="owner-workflow-progress-card__quota" aria-label="领取进度">
          <span>领取</span>
          <strong>{progress ? `${progress.quotaClaimed}/${progress.quotaTotal}` : '-'}</strong>
        </div>
      </div>

      {isLoading ? (
        <div className="owner-workflow-progress-card__loading">
          <Spin />
        </div>
      ) : isError ? (
        <div className="owner-workflow-progress-card__loading">
          <EmptyState variant="inline" title="全过程进度加载失败" />
          <Button onClick={onRetry}>重新加载</Button>
        </div>
      ) : progress ? (
        <>
          <div className="owner-workflow-progress-meter" aria-hidden>
            <span style={{ width: `${claimedPercent(progress)}%` }} />
          </div>
          <div className="owner-workflow-progress-grid">
            {workflowProgressSteps.map((step) => (
              <div className={`owner-workflow-progress-step owner-workflow-progress-step--${step.tone}`} key={step.key}>
                <span>{step.label}</span>
                <strong>{countText(progress[step.key])}</strong>
              </div>
            ))}
          </div>
        </>
      ) : (
        <EmptyState variant="inline" title="暂无全过程进度" description="任务发布并产生领取、提交或审核记录后会显示在这里。" />
      )}
    </Card>
  );
}

export function OwnerTaskDetailPage() {
  const navigate = useNavigate();
  const { taskId: rawTaskId } = useParams();
  const taskId = parseTaskId(rawTaskId);
  const [targetStatus, setTargetStatus] = useState<TaskStatus | null>(null);
  const [aiReviewRuleEditorOpen, setAiReviewRuleEditorOpen] = useState(false);
  const [taskEditorOpen, setTaskEditorOpen] = useState(false);
  const taskQuery = useTaskDetailQuery(taskId ?? 0);
  const transitionsQuery = useTaskTransitionsQuery(taskId ?? 0);
  const workflowProgressQuery = useTaskWorkflowProgressQuery(taskId ?? 0);
  const schemasQuery = useSchemasQuery({ page: 1, size: 100 });
  const createSchemaMutation = useCreateSchemaMutation();
  const task = taskQuery.data;

  const tags = useMemo(() => task?.tags?.filter(Boolean) ?? [], [task?.tags]);
  const editableTaskFields = task?.status === 'draft' || task?.status === 'paused';
  const taskSchema = useMemo(
    () => (task ? findSchemaForTask(schemasQuery.data?.items ?? [], task.id) : undefined),
    [schemasQuery.data?.items, task],
  );

  const scrollToDataset = () => {
    document.querySelector('.detail-dataset-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const openTaskSchemaDesigner = async () => {
    if (!task) {
      return;
    }

    if (taskSchema) {
      navigate(`/owner/schemas/${taskSchema.id}/design`);
      return;
    }

    try {
      const created = await createSchemaMutation.mutateAsync(buildTaskSchemaDraft(task));
      Toast.success('已创建任务模板（Schema）。');
      navigate(`/owner/schemas/${created.id}/design`);
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error
        ? String((error as { message?: unknown }).message)
        : '模板（Schema）创建失败。';
      Toast.error(message);
    }
  };

  const openAiReviewRuleEditor = () => {
    setAiReviewRuleEditorOpen(true);
  };

  if (!taskId) {
    return (
      <section className="task-detail-page">
        <EmptyState variant="inline" title="任务 ID 无效" description="请从任务列表重新进入详情页。" />
      </section>
    );
  }

  if (taskQuery.isLoading) {
    return (
      <section className="task-detail-page">
        <div className="task-state-panel">
          <Spin size="large" />
        </div>
      </section>
    );
  }

  if (taskQuery.isError || !task) {
    return (
      <section className="task-detail-page">
        <div className="task-state-panel">
          <EmptyState variant="inline" title="任务详情加载失败" description={taskQuery.error instanceof Error ? taskQuery.error.message : '请稍后重试。'} />
          <Button onClick={() => taskQuery.refetch()}>重新加载</Button>
        </div>
      </section>
    );
  }

  return (
    <section className="task-detail-page task-detail-page--owner" aria-label="Owner task detail">
      <div className="detail-heading">
        <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate('/owner/tasks')}>
          返回列表
        </Button>
        <Button icon={<IconRefresh />} onClick={() => {
          taskQuery.refetch();
          transitionsQuery.refetch();
          workflowProgressQuery.refetch();
        }}>
          刷新
        </Button>
      </div>

      <div className="detail-grid">
        <Card className="detail-main-card owner-task-command-center">
          <div className="owner-task-command-head">
            <div className="detail-title-copy">
              <div className="owner-page-hero__meta">
                <RoleBadge role="OWNER" />
                <Typography.Text>任务命令中心</Typography.Text>
              </div>
              <Typography.Title heading={3} className="page-title">
                {task.title}
              </Typography.Title>
              <Typography.Text type="tertiary">{task.description || '暂无任务描述。'}</Typography.Text>
            </div>
            <div className="task-detail-actions">
              <TaskStatusBadge status={task.status} />
              <Tooltip content={editableTaskFields ? '编辑任务基础信息' : '仅草稿或已暂停任务可编辑'}>
                <Button
                  icon={<IconEdit />}
                  onClick={() => setTaskEditorOpen(true)}
                  disabled={!editableTaskFields}
                >
                  编辑
                </Button>
              </Tooltip>
            </div>
          </div>

          <dl className="task-meta-grid owner-task-summary-grid">
            <div>
              <dt>领取/题量</dt>
              <dd>{task.quotaClaimed}/{task.quotaTotal}</dd>
            </div>
            <div>
              <dt>截止时间</dt>
              <dd>{formatDateTime(task.deadlineAt)}</dd>
            </div>
            <div>
              <dt>标签</dt>
              <dd>{tags.length ? tags.join(' / ') : '-'}</dd>
            </div>
            <div>
              <dt>Owner</dt>
              <dd>{getUser()?.displayName ?? '当前用户'}</dd>
            </div>
          </dl>

          <div className="task-extended-copy">
            <Typography.Title heading={6}>富文本说明</Typography.Title>
            <Typography.Paragraph>{task.instructionRichText || '暂无富文本说明。'}</Typography.Paragraph>
            <Typography.Title heading={6}>奖励规则</Typography.Title>
            <pre>{task.rewardRule ? JSON.stringify(task.rewardRule, null, 2) : '暂无奖励规则。'}</pre>
          </div>

          <div className="transition-action-panel">
            <Typography.Title heading={5}>状态操作</Typography.Title>
            <TransitionButtons task={task} onSelect={setTargetStatus} />
          </div>
        </Card>

        <WorkflowProgressCard
          progress={workflowProgressQuery.data}
          isLoading={workflowProgressQuery.isLoading}
          isError={workflowProgressQuery.isError}
          onRetry={() => workflowProgressQuery.refetch()}
        />

        <TaskAiPrereviewPanel taskId={task.id} />

        {task.status === 'draft' ? (
          <Card className="task-setup-guidance-card">
            <TaskNextStepGuidance
              task={task}
              onNavigateToSchema={openTaskSchemaDesigner}
              onScrollToDataset={scrollToDataset}
              onPublish={() => setTargetStatus('published')}
              schemaActionLoading={createSchemaMutation.isPending || schemasQuery.isLoading}
              schemaActionDisabled={schemasQuery.isError}
            />
          </Card>
        ) : null}

        <AiReviewRuleEntryCard taskId={task.id} onOpenEditor={openAiReviewRuleEditor} />

        <Card className="detail-dataset-card">
          <DatasetUploadSection task={task} />
        </Card>

        <TrustedExportCard taskId={task.id} />

        <Card className="detail-timeline-card detail-timeline-card--quiet detail-timeline-card--compact">
          <div className="timeline-heading">
            <Typography.Title heading={5}>任务状态日志</Typography.Title>
            <Typography.Text type="tertiary">仅记录发布、暂停、恢复或结束等任务状态变化。</Typography.Text>
          </div>
          {transitionsQuery.isLoading ? <Spin /> : <TransitionTimeline transitions={transitionsQuery.data ?? []} />}
        </Card>

        <Card className="detail-submissions-card">
          <div className="submissions-section-header">
            <div>
              <Typography.Title heading={5}>已提交记录</Typography.Title>
              <Typography.Text type="tertiary">Owner 可从这里进入历史模板（Schema）作答与 AI 预审。</Typography.Text>
            </div>
          </div>
          <OwnerTaskSubmissionsSection taskId={task.id} />
        </Card>
      </div>

      <TransitionTaskModal task={task} targetStatus={targetStatus} onClose={() => setTargetStatus(null)} />
      <EditTaskModal task={task} visible={taskEditorOpen} onClose={() => setTaskEditorOpen(false)} />
      <AiReviewRuleEditorDrawer
        taskId={task.id}
        open={aiReviewRuleEditorOpen}
        onClose={() => setAiReviewRuleEditorOpen(false)}
      />
    </section>
  );
}
