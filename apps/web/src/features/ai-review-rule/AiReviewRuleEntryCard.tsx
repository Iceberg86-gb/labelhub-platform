import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit } from '@douyinfe/semi-icons';

type AiReviewRuleEntryCardProps = {
  taskId: number;
  disabled?: boolean;
  onOpenEditor: () => void;
};

export function AiReviewRuleEntryCard({ taskId, disabled = false, onOpenEditor }: AiReviewRuleEntryCardProps) {
  return (
    <Card className="ai-review-rule-entry-card ai-review-rule-entry-card--assistive" aria-label="AI review rule entry" bordered={false}>
      <div className="task-setup-guidance ai-review-rule-entry-hero">
        <div className="task-setup-guidance__header ai-review-rule-entry-hero__header">
          <div>
            <Typography.Title heading={5}>AI 预审辅助规则</Typography.Title>
            <Typography.Text type="tertiary">
              配置 Prompt、评分维度和阈值后,AI 只提供预审证据,人工审核仍是最终裁决。
            </Typography.Text>
          </div>
          <Tag className="ai-review-rule-task-tag">Task #{taskId}</Tag>
        </div>

        <div className="task-setup-step task-setup-step--pending ai-review-rule-entry-step">
          <div className="task-setup-step__head">
            <Typography.Text strong>规则配置入口</Typography.Text>
            <Tag className="ai-review-rule-status-tag ai-review-rule-status-tag--draft">辅助证据</Tag>
          </div>
          <Typography.Text className="task-setup-step__copy" type="tertiary">
            保存会创建新的规则版本;发布后才会成为当前生效规则。
          </Typography.Text>
          <div className="task-setup-step__actions">
            <Button disabled={disabled} icon={<IconEdit />} size="small" theme="solid" type="primary" onClick={onOpenEditor}>
              配置规则
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}
