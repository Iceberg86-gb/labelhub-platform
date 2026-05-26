import { Button, Tag, Typography } from '@douyinfe/semi-ui';
import { IconLockStroked } from '@douyinfe/semi-icons';
import type { Task } from '../list-tasks/useTasksQuery';

type SetupStep = {
  id: 'schema' | 'dataset' | 'publish';
  title: string;
  status: 'done' | 'pending' | 'blocked';
  ctaLabel: string;
  onClick: () => void;
  disabled?: boolean;
  helpText: string;
};

type TaskNextStepGuidanceProps = {
  task: Task;
  onNavigateToSchema: () => void;
  onScrollToDataset: () => void;
  onPublish: () => void;
};

function statusTag(status: SetupStep['status']) {
  if (status === 'done') {
    return <Tag color="green">已完成</Tag>;
  }

  if (status === 'blocked') {
    return <Tag className="task-setup-step__blocked-tag" prefixIcon={<IconLockStroked />}>待前置</Tag>;
  }

  return <Tag color="blue">待处理</Tag>;
}

export function TaskNextStepGuidance({ task, onNavigateToSchema, onScrollToDataset, onPublish }: TaskNextStepGuidanceProps) {
  const schemaReady = task.currentSchemaVersionId != null;
  const datasetReady = task.currentDatasetId != null;
  const publishReady = schemaReady && datasetReady;

  const steps: SetupStep[] = [
    {
      id: 'schema',
      title: '设计 Schema',
      status: schemaReady ? 'done' : 'pending',
      ctaLabel: schemaReady ? '查看 Schema' : '去设计',
      onClick: onNavigateToSchema,
      helpText: schemaReady ? `已绑定 Schema #${task.currentSchemaVersionId}` : 'Schema 决定 labeler 看到和填写的字段。',
    },
    {
      id: 'dataset',
      title: '选择数据集',
      status: datasetReady ? 'done' : 'pending',
      ctaLabel: datasetReady ? '管理数据集' : '上传数据集',
      onClick: onScrollToDataset,
      helpText: datasetReady ? `已绑定数据集 #${task.currentDatasetId}` : '数据集是 labeler 要领取并标注的内容。',
    },
    {
      id: 'publish',
      title: '发布任务',
      status: publishReady ? 'pending' : 'blocked',
      ctaLabel: '发布',
      onClick: onPublish,
      disabled: !publishReady,
      helpText: publishReady ? '前置设置已就绪,可以发布任务。' : '需先完成 Schema 与数据集设置。',
    },
  ];

  return (
    <div className="task-setup-guidance" aria-label="Task setup guidance">
      <div className="task-setup-guidance__header">
        <div>
          <Typography.Title heading={5}>任务设置进度</Typography.Title>
          <Typography.Text type="tertiary">完成 Schema、数据集、发布三步后,labeler 才能开始领取。</Typography.Text>
        </div>
        <Tag color="blue">3 步</Tag>
      </div>

      <div className="task-setup-guidance__steps">
        {steps.map((step) => (
          <div className={`task-setup-step task-setup-step--${step.status}`} key={step.id}>
            <div className="task-setup-step__head">
              <Typography.Text strong>{step.title}</Typography.Text>
              {statusTag(step.status)}
            </div>
            <Typography.Text className="task-setup-step__copy" type="tertiary">
              {step.helpText}
            </Typography.Text>
            <div className="task-setup-step__actions">
              <Button disabled={step.disabled} size="small" theme={step.id === 'publish' ? 'solid' : 'light'} type={step.id === 'publish' ? 'primary' : 'tertiary'} onClick={step.onClick}>
                {step.ctaLabel}
              </Button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
