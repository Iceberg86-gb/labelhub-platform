import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit } from '@douyinfe/semi-icons';

type AiReviewRuleEntryCardProps = {
  taskId: number;
  disabled?: boolean;
  onOpenEditor: () => void;
};

export function AiReviewRuleEntryCard({ taskId, disabled = false, onOpenEditor }: AiReviewRuleEntryCardProps) {
  return (
    <Card className="ai-review-rule-entry-card" aria-label="AI review rule entry">
      <div className="task-setup-guidance">
        <div className="task-setup-guidance__header">
          <div>
            <Typography.Title heading={5}>AI 审核规则</Typography.Title>
            <Typography.Text type="tertiary">
              配置 Prompt、评分维度和阈值后,AI 检查会绑定到不可变规则版本。
            </Typography.Text>
          </div>
          <Tag color="blue">Task #{taskId}</Tag>
        </div>

        <div className="task-setup-step task-setup-step--pending">
          <div className="task-setup-step__head">
            <Typography.Text strong>规则配置入口</Typography.Text>
            <Tag color="blue">待配置</Tag>
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
