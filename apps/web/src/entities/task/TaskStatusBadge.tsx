import { Tag } from '@douyinfe/semi-ui';
import type { components } from '../../shared/api/generated/schema';

type TaskStatus = components['schemas']['TaskStatus'];

const statusMeta: Record<TaskStatus, { label: string; tone: string }> = {
  draft: { label: '草稿', tone: 'neutral' },
  published: { label: '发布中', tone: 'success' },
  paused: { label: '已暂停', tone: 'warning' },
  ended: { label: '已结束', tone: 'accent' },
};

type TaskStatusBadgeProps = {
  status: TaskStatus;
};

export function TaskStatusBadge({ status }: TaskStatusBadgeProps) {
  const meta = statusMeta[status];

  return (
    <Tag className={`task-status-badge semantic-tag semantic-tag--${meta.tone}`}>
      {meta.label}
    </Tag>
  );
}
