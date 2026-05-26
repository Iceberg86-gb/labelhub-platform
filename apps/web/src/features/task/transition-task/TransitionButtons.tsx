import { Button, Space, Typography } from '@douyinfe/semi-ui';
import type { Task, TaskStatus } from '../list-tasks/useTasksQuery';
import { actionLabelFor, transitionsFor } from './transitionRules';

type TransitionButtonsProps = {
  task: Task;
  onSelect: (targetStatus: TaskStatus) => void;
};

export function TransitionButtons({ task, onSelect }: TransitionButtonsProps) {
  const targets = transitionsFor(task.status);

  if (targets.length === 0) {
    return <Typography.Text type="tertiary">任务已结束，状态不可再迁移。</Typography.Text>;
  }

  return (
    <Space wrap>
      {targets.map((targetStatus) => (
        <Button key={targetStatus} theme="solid" type={targetStatus === 'ended' ? 'danger' : 'primary'} onClick={() => onSelect(targetStatus)}>
          {actionLabelFor(task.status, targetStatus)}
        </Button>
      ))}
    </Space>
  );
}
