import type { components } from '../../shared/api/generated/schema';
import { StatusBadge, type BadgeTone } from '../../shared/ui';

type TaskStatus = components['schemas']['TaskStatus'];

const statusMeta: Record<TaskStatus, { label: string; tone: BadgeTone }> = {
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
    <StatusBadge tone={meta.tone} className="task-status-badge">
      {meta.label}
    </StatusBadge>
  );
}
