import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui';
import { Link } from 'react-router-dom';

const m4Features = [
  ['审核队列', '按任务、状态与审核层级查看待处理提交。'],
  ['批量动作', '批量通过、打回或指派，动作写入审计日志。'],
  ['二次评审', '支持初审、复审与终审的人工流转。'],
  ['Quality Ledger', 'AI 审与人审证据进入 append-only ledger。'],
  ['Verdict 派生', '基于规则版本从 ledger 重新派生当前结论。'],
];

export function ReviewerPlaceholderPage() {
  return (
    <section className="placeholder-page" aria-label="Reviewer placeholder">
      <div className="placeholder-hero">
        <Tag className="semantic-tag semantic-tag--warning">REVIEWER</Tag>
        <Typography.Title heading={3} className="page-title">
          审核工作台正在开发中
        </Typography.Title>
        <Typography.Text type="tertiary">
          M4 将接入审核队列、批量动作、多级人工复审与 Quality Ledger 派生。本页只展示路线图，不模拟审核数据。
        </Typography.Text>
      </div>

      <div className="preview-card-grid">
        {m4Features.map(([title, description]) => (
          <Card key={title} className="preview-card">
            <Typography.Title heading={6}>{title}</Typography.Title>
            <Typography.Text type="tertiary">{description}</Typography.Text>
          </Card>
        ))}
      </div>

      <Link to="/login">
        <Button theme="borderless" type="tertiary">
          返回登录
        </Button>
      </Link>
    </section>
  );
}
