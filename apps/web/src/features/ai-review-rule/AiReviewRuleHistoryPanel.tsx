import { Button, Empty, Space, Tag, Toast, Typography } from '@douyinfe/semi-ui';
import { IconTickCircle } from '@douyinfe/semi-icons';
import { useQueryClient } from '@tanstack/react-query';
import type { AiReviewRule } from './aiReviewRuleTypes';
import { type PublishAiReviewRuleVariables, usePublishAiReviewRuleMutation } from './usePublishAiReviewRuleMutation';
import { AiReviewRuleMutationFailure } from './useSaveAiReviewRuleMutation';
import { aiReviewRulesQueryKey, useListAiReviewRulesQuery } from './useListAiReviewRulesQuery';

type AiReviewRuleHistoryPanelProps = {
  taskId: number;
};

type PublishRuleAndRefreshArgs = {
  taskId: number;
  rule: AiReviewRule;
  publishRule: (variables: PublishAiReviewRuleVariables) => Promise<AiReviewRule>;
  invalidateQueries: (options: { queryKey: ReturnType<typeof aiReviewRulesQueryKey> }) => Promise<unknown> | unknown;
};

const AI_REVIEW_RULE_STATUS_LABEL: Record<AiReviewRule['status'], string> = {
  draft: '草稿',
  published: '已发布',
};

export async function publishRuleAndRefresh({
  taskId,
  rule,
  publishRule,
  invalidateQueries,
}: PublishRuleAndRefreshArgs): Promise<AiReviewRule> {
  const publishedRule = await publishRule({ ruleId: rule.id });
  await invalidateQueries({ queryKey: aiReviewRulesQueryKey(taskId) });
  return publishedRule;
}

export function AiReviewRuleHistoryPanel({ taskId }: AiReviewRuleHistoryPanelProps) {
  const rulesQuery = useListAiReviewRulesQuery(taskId);
  const publishRule = usePublishAiReviewRuleMutation();
  const queryClient = useQueryClient();

  const publish = async (rule: AiReviewRule) => {
    try {
      const publishedRule = await publishRuleAndRefresh({
        taskId,
        rule,
        publishRule: publishRule.mutateAsync,
        invalidateQueries: queryClient.invalidateQueries.bind(queryClient),
      });
      Toast.success(`已发布 AI 审核规则版本 v${publishedRule.versionNo}`);
    } catch (error) {
      const message = error instanceof AiReviewRuleMutationFailure
        ? error.userMessage
        : error instanceof Error
          ? error.message
          : 'AI 审核规则发布失败';
      Toast.error(message);
    }
  };

  return (
    <section className="ai-review-rule-history ai-review-rule-history--versioned" data-testid="ai-review-rule-history-section">
      <div className="ai-review-rule-history__head">
        <Typography.Title heading={5}>版本历史</Typography.Title>
        <Typography.Text type="tertiary">发布后会更新任务使用的规则版本。</Typography.Text>
      </div>

      {rulesQuery.isLoading ? <Typography.Text type="tertiary">正在加载规则版本...</Typography.Text> : null}
      {rulesQuery.isError ? <Typography.Text className="ai-review-rule-error">{rulesQuery.error.userMessage}</Typography.Text> : null}
      {!rulesQuery.isLoading && !rulesQuery.isError && !rulesQuery.data?.length ? (
        <Empty title="暂无规则版本" description="保存草稿后,规则版本会出现在这里。" />
      ) : null}

      <div className="ai-review-rule-history__list">
        {rulesQuery.data?.map((rule) => (
          <article className="ai-review-rule-history__item" key={rule.id}>
            <div className="ai-review-rule-history__meta">
              <Space>
                <Typography.Text strong>v{rule.versionNo}</Typography.Text>
                <Tag className={`ai-review-rule-status-tag ai-review-rule-status-tag--${rule.status}`}>{formatStatus(rule.status)}</Tag>
                {rule.isCurrent ? (
                  <Tag className="ai-review-rule-current-tag">
                    <IconTickCircle /> 当前生效
                  </Tag>
                ) : null}
              </Space>
              <Typography.Text type="tertiary">{formatDateTime(rule.createdAt)}</Typography.Text>
            </div>

            {rule.status === 'draft' && !rule.isCurrent ? (
              <Button
                loading={publishRule.isPending}
                size="small"
                theme="solid"
                type="primary"
                onClick={() => void publish(rule)}
              >
                发布
              </Button>
            ) : null}
          </article>
        ))}
      </div>
    </section>
  );
}

function formatStatus(status: AiReviewRule['status']): string {
  return AI_REVIEW_RULE_STATUS_LABEL[status] ?? status;
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').replace(/(?:\.\d+)?Z$/, '');
}
