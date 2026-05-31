import { Button, Card, Empty, Spin, Timeline, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
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
import { getUser } from '../../shared/api/auth-storage';

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
    return <Empty title="暂无状态迁移" description="发布、暂停、恢复或结束任务后，迁移记录会出现在这里。" />;
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

export function OwnerTaskDetailPage() {
  const navigate = useNavigate();
  const { taskId: rawTaskId } = useParams();
  const taskId = parseTaskId(rawTaskId);
  const [targetStatus, setTargetStatus] = useState<TaskStatus | null>(null);
  const [aiReviewRuleEditorOpen, setAiReviewRuleEditorOpen] = useState(false);
  const [taskEditorOpen, setTaskEditorOpen] = useState(false);
  const taskQuery = useTaskDetailQuery(taskId ?? 0);
  const transitionsQuery = useTaskTransitionsQuery(taskId ?? 0);
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
      Toast.success('已创建任务 Schema。');
      navigate(`/owner/schemas/${created.id}/design`);
    } catch (error) {
      const message = typeof error === 'object' && error && 'message' in error
        ? String((error as { message?: unknown }).message)
        : 'Schema 创建失败。';
      Toast.error(message);
    }
  };

  const openAiReviewRuleEditor = () => {
    setAiReviewRuleEditorOpen(true);
  };

  if (!taskId) {
    return (
      <section className="task-detail-page">
        <Empty title="任务 ID 无效" description="请从任务列表重新进入详情页。" />
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
          <Empty title="任务详情加载失败" description={taskQuery.error instanceof Error ? taskQuery.error.message : '请稍后重试。'} />
          <Button onClick={() => taskQuery.refetch()}>重新加载</Button>
        </div>
      </section>
    );
  }

  return (
    <section className="task-detail-page" aria-label="Owner task detail">
      <div className="detail-heading">
        <Button icon={<IconArrowLeft />} theme="borderless" onClick={() => navigate('/owner/tasks')}>
          返回列表
        </Button>
        <Button icon={<IconRefresh />} onClick={() => {
          taskQuery.refetch();
          transitionsQuery.refetch();
        }}>
          刷新
        </Button>
      </div>

      <div className="detail-grid">
        <Card className="detail-main-card">
          <div className="detail-title-row">
            <div className="detail-title-copy">
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

          <dl className="task-meta-grid">
            <div>
              <dt>配额</dt>
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

        <Card className="detail-timeline-card">
          <div className="timeline-heading">
            <Typography.Title heading={5}>状态迁移记录</Typography.Title>
            <Typography.Text type="tertiary">来自 append-only task_transitions。</Typography.Text>
          </div>
          {transitionsQuery.isLoading ? <Spin /> : <TransitionTimeline transitions={transitionsQuery.data ?? []} />}
        </Card>

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

        <Card className="detail-submissions-card">
          <div className="submissions-section-header">
            <div>
              <Typography.Title heading={5}>已提交记录</Typography.Title>
              <Typography.Text type="tertiary">Owner 可从这里进入历史 Schema 作答与 AI 检查。</Typography.Text>
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
