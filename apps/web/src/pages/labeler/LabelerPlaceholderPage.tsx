import { Button, Card, Tag, Typography } from '@douyinfe/semi-ui';
import { Link } from 'react-router-dom';

const m2Features = [
  ['任务广场', '浏览可领取任务与剩余配额。'],
  ['任务领取', '按先到先得策略认领作答配额。'],
  ['Schema 渲染', '根据 Owner 发布的 schema 动态表单作答。'],
  ['草稿自动保存', '字段变更后自动保存，刷新后可恢复。'],
  ['提交流转', '提交后进入 AI 预审与人工审核队列。'],
];

export function LabelerPlaceholderPage() {
  return (
    <section className="placeholder-page" aria-label="Labeler placeholder">
      <div className="placeholder-hero">
        <Tag className="semantic-tag semantic-tag--success">LABELER</Tag>
        <Typography.Title heading={3} className="page-title">
          标注员工作台正在开发中
        </Typography.Title>
        <Typography.Text type="tertiary">
          M2 将接入任务广场、动态表单作答、草稿自动保存与提交流转。本页只展示路线图，不模拟任何业务数据。
        </Typography.Text>
      </div>

      <div className="preview-card-grid">
        {m2Features.map(([title, description]) => (
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
