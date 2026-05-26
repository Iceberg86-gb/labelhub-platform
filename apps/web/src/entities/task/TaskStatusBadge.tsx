import { Tag } from '@douyinfe/semi-ui';
import type { components } from '../../shared/api/generated/schema';

type TaskStatus = components['schemas']['TaskStatus'];

const statusMeta: Record<TaskStatus, { label: string; color: 'blue' | 'green' | 'amber' | 'grey' }> = {
  draft: { label: '草稿', color: 'grey' },
  published: { label: '发布中', color: 'green' },
  paused: { label: '已暂停', color: 'amber' },
  ended: { label: '已结束', color: 'blue' },
};

type TaskStatusBadgeProps = {
  status: TaskStatus;
};

export function TaskStatusBadge({ status }: TaskStatusBadgeProps) {
  const meta = statusMeta[status];

  return (
    <Tag className="task-status-badge" color={meta.color}>
      {meta.label}
    </Tag>
  );
}
