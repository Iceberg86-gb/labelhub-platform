import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { IconPlay } from '@douyinfe/semi-icons';

export type LabelerTaskDetailDrawerTask = {
  availableItemCount: number;
  deadlineAt?: string;
  description?: string;
  id: number;
  instructionRichText?: string;
  quotaClaimed: number;
  quotaTotal: number;
  rewardRule?: string;
  tags?: string[];
  title: string;
};

type LabelerTaskDetailDrawerProps = {
  claiming: boolean;
  onClaim: (taskId: number) => void;
  onClose: () => void;
  task: LabelerTaskDetailDrawerTask | null;
};

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(
        new Date(value),
      )
    : '未设置';
}

function displayText(value: string | undefined, fallback: string) {
  const normalized = value?.trim();
  return normalized && normalized.length > 0 ? normalized : fallback;
}

export function LabelerTaskDetailDrawer({ claiming, onClaim, onClose, task }: LabelerTaskDetailDrawerProps) {
  if (!task) {
    return null;
  }

  const remainingQuota = task ? Math.max(task.quotaTotal - task.quotaClaimed, 0) : 0;

  return (
    <div className="labeler-task-detail-drawer" role="presentation">
      <button
        aria-label="关闭任务详情"
        className="labeler-task-detail-drawer__backdrop"
        onClick={onClose}
        type="button"
      />
      <aside
        aria-labelledby="labeler-task-detail-drawer-title"
        aria-modal="true"
        className="labeler-task-detail-drawer__panel"
        role="dialog"
      >
        <div className="labeler-task-detail-drawer__titlebar">
          <Typography.Title heading={4} id="labeler-task-detail-drawer-title">
            任务详情
          </Typography.Title>
          <Button onClick={onClose}>关闭</Button>
        </div>
        <div className="labeler-task-detail-drawer__body">
          <header className="labeler-task-detail-drawer__header">
            <Typography.Title heading={4}>{task.title}</Typography.Title>
            <Typography.Paragraph type="tertiary">{displayText(task.description, '暂无描述')}</Typography.Paragraph>
            {task.tags && task.tags.length > 0 ? (
              <Space wrap spacing={4}>
                {task.tags.map((item) => (
                  <Tag key={item} className="semantic-tag semantic-tag--accent">
                    {item}
                  </Tag>
                ))}
              </Space>
            ) : null}
          </header>

          <section className="labeler-task-detail-drawer__section" aria-label="任务说明">
            <Typography.Text strong>任务说明</Typography.Text>
            <Typography.Paragraph className="labeler-task-detail-drawer__long-text" type="tertiary">
              {displayText(task.instructionRichText, displayText(task.description, '暂无任务说明'))}
            </Typography.Paragraph>
          </section>

          <section className="labeler-task-detail-drawer__stats" aria-label="任务领取信息">
            <div>
              <span>剩余配额</span>
              <strong>{remainingQuota}</strong>
              <small>
                已领 {task.quotaClaimed} / 总量 {task.quotaTotal}
              </small>
            </div>
            <div>
              <span>可领取数据量</span>
              <strong>{task.availableItemCount} 个</strong>
              <small>领取后进入作答页</small>
            </div>
            <div>
              <span>截止时间</span>
              <strong>{formatDateTime(task.deadlineAt)}</strong>
              <small>请在截止前完成作答</small>
            </div>
          </section>

          <section className="labeler-task-detail-drawer__section" aria-label="奖励规则">
            <Typography.Text strong>奖励规则</Typography.Text>
            <Typography.Paragraph className="labeler-task-detail-drawer__long-text" type="tertiary">
              {displayText(task.rewardRule, '未配置奖励规则')}
            </Typography.Paragraph>
          </section>
        </div>
        <div className="labeler-task-detail-drawer__footer">
          <Button onClick={onClose}>关闭</Button>
          <Button
            className="labeler-task-detail-drawer__claim"
            disabled={task.availableItemCount <= 0}
            icon={<IconPlay />}
            loading={claiming}
            onClick={() => onClaim(task.id)}
            theme="solid"
            type="primary"
          >
            领取任务
          </Button>
        </div>
      </aside>
    </div>
  );
}
