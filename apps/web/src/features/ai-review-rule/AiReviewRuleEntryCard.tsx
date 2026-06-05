import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui';
import { IconEdit } from '@douyinfe/semi-icons';
import { useListAiReviewRulesQuery } from './useListAiReviewRulesQuery';

type AiReviewRuleEntryCardProps = {
  taskId: number;
  disabled?: boolean;
  onOpenEditor: () => void;
};

export function AiReviewRuleEntryCard({ taskId, disabled = false, onOpenEditor }: AiReviewRuleEntryCardProps) {
  const rulesQuery = useListAiReviewRulesQuery(taskId);
  const currentRule = !rulesQuery.isLoading && !rulesQuery.isError
    ? rulesQuery.data?.find((rule) => rule.isCurrent)
    : undefined;

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

        <div className={`task-setup-step ${currentRule ? 'task-setup-step--done' : 'task-setup-step--pending'} ai-review-rule-entry-step`}>
          <div className="task-setup-step__head">
            <Typography.Text strong>{currentRule ? '当前生效规则' : '规则配置入口'}</Typography.Text>
            <Tag className={`ai-review-rule-status-tag ai-review-rule-status-tag--${currentRule ? 'published' : 'draft'}`}>
              {currentRule ? `生效中 · v${currentRule.versionNo}` : '未配置 AI 预审'}
            </Tag>
          </div>
          {currentRule ? (
            <div className="ai-review-rule-entry-summary" aria-label="AI review current rule summary">
              <div className="ai-review-rule-entry-summary__item">
                <Typography.Text type="tertiary">Prompt 版本</Typography.Text>
                <Typography.Text strong>#{currentRule.promptVersionId}</Typography.Text>
              </div>
              <div className="ai-review-rule-entry-summary__item ai-review-rule-entry-summary__item--wide">
                <Typography.Text type="tertiary">评分维度</Typography.Text>
                <div className="ai-review-rule-entry-dimensions">
                  {currentRule.dimensions.map((dimension) => (
                    <Tag className="ai-review-rule-entry-dimension-tag" key={dimension}>{dimension}</Tag>
                  ))}
                </div>
              </div>
              <div className="ai-review-rule-entry-thresholds">
                <span>
                  <Typography.Text type="tertiary">通过阈值</Typography.Text>
                  <Typography.Text strong>{formatThreshold(currentRule.passThreshold)}</Typography.Text>
                </span>
                <span>
                  <Typography.Text type="tertiary">拒绝阈值</Typography.Text>
                  <Typography.Text strong>{formatThreshold(currentRule.rejectThreshold)}</Typography.Text>
                </span>
              </div>
              <Typography.Text className="task-setup-step__copy" type="tertiary">
                AI 只提供预审证据,人工审核仍是最终裁决。
              </Typography.Text>
            </div>
          ) : (
            <Typography.Text className="task-setup-step__copy" type="tertiary">
              保存会创建新的规则版本;发布后才会成为当前生效规则。
            </Typography.Text>
          )}
          <div className="task-setup-step__actions">
            <Button disabled={disabled} icon={<IconEdit />} size="small" theme="solid" type="primary" onClick={onOpenEditor}>
              {currentRule ? '查看 / 编辑规则' : '配置规则'}
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}

function formatThreshold(value: number | string | null | undefined): string {
  const normalized = typeof value === 'string' ? Number(value) : value;
  return Number.isFinite(normalized) ? String(normalized) : '-';
}
