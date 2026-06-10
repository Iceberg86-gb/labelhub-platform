import { Button, InputNumber, Space, Typography } from '@douyinfe/semi-ui';
import { StatusBadge } from '../../shared/ui';
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
  claimLimit: number;
  claimSize: number;
  claiming: boolean;
  onClaim: (taskId: number, size: number) => void;
  onClaimSizeChange: (value: unknown) => void;
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

export function LabelerTaskDetailDrawer({
  claimLimit,
  claimSize,
  claiming,
  onClaim,
  onClaimSizeChange,
  onClose,
  task,
}: LabelerTaskDetailDrawerProps) {
  if (!task) {
    return null;
  }

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
                  <StatusBadge key={item} tone="accent">
                    {item}
                  </StatusBadge>
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
              <span>可领取题目</span>
              <strong>{task.availableItemCount}</strong>
              <small>
                已领取 {task.quotaClaimed} / 题量 {task.quotaTotal}
              </small>
            </div>
            <div>
              <span>本次领取上限</span>
              <strong>{claimLimit} 个</strong>
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
          <div className="labeler-task-detail-drawer__claim-row">
            <label className="marketplace-claim-control">
              <span>领取数量</span>
              <InputNumber
                aria-label={`领取${task.title}数量`}
                disabled={claimLimit <= 0}
                max={Math.max(1, claimLimit)}
                min={1}
                precision={0}
                value={claimSize}
                onChange={onClaimSizeChange}
              />
            </label>
            <Button
              className="labeler-task-detail-drawer__claim"
              disabled={claimLimit <= 0}
              icon={<IconPlay />}
              loading={claiming}
              onClick={() => onClaim(task.id, claimSize)}
              theme="solid"
              type="primary"
            >
              领取 {claimSize} 条
            </Button>
          </div>
        </div>
      </aside>
    </div>
  );
}
