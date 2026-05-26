import type { TaskStatus } from '../list-tasks/useTasksQuery';

export const transitionLabels: Record<TaskStatus, string> = {
  draft: '草稿',
  published: '发布中',
  paused: '已暂停',
  ended: '已结束',
};

export const transitionActionLabels: Partial<Record<TaskStatus, string>> = {
  published: '发布任务',
  paused: '暂停任务',
  ended: '结束任务',
};

export function transitionsFor(status: TaskStatus): TaskStatus[] {
  switch (status) {
    case 'draft':
      return ['published'];
    case 'published':
      return ['paused', 'ended'];
    case 'paused':
      return ['published', 'ended'];
    case 'ended':
      return [];
  }
}

export function actionLabelFor(currentStatus: TaskStatus, targetStatus: TaskStatus) {
  if (currentStatus === 'paused' && targetStatus === 'published') {
    return '恢复发布';
  }

  return transitionActionLabels[targetStatus] ?? `切换到${transitionLabels[targetStatus]}`;
}
