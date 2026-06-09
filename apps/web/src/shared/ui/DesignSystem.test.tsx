import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { EmptyState } from './EmptyState';
import { FlowStrip } from './FlowStrip';
import { SectionCard } from './SectionCard';
import { StatTile } from './StatTile';
import { StatusBadge } from './StatusBadge';

vi.mock('@douyinfe/semi-ui', () => ({
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div className="semi-empty">
      <div>{title}</div>
      <div>{description}</div>
    </div>
  ),
}));

describe('Design System primitives', () => {
  it('StatusBadge maps tone to the canonical semantic-tag palette', () => {
    const html = renderToString(<StatusBadge tone="success">已通过</StatusBadge>);
    expect(html).toContain('semantic-tag');
    expect(html).toContain('semantic-tag--success');
    expect(html).toContain('已通过');
  });

  it('FlowStrip renders tokenized steps, states, and connectors', () => {
    const html = renderToString(
      <FlowStrip
        ariaLabel="flow"
        steps={[
          { key: 'a', label: '提交', state: 'done' },
          { key: 'b', label: '审核', state: 'active' },
          { key: 'c', label: '终审', state: 'pending', tone: 'danger', note: '跳过' },
        ]}
      />,
    );
    expect(html).toContain('ds-flow-strip');
    expect(html).toContain('ds-flow-step--done');
    expect(html).toContain('ds-flow-step--active');
    expect(html).toContain('ds-flow-step--danger');
    expect(html).toContain('ds-flow-connector');
    expect(html).toContain('跳过');
  });

  it('StatTile renders value, label and icon slot', () => {
    const html = renderToString(<StatTile value="4 个快照" label="活跃总数" icon={<span>i</span>} />);
    expect(html).toContain('ds-stat-tile');
    expect(html).toContain('ds-stat-tile__value');
    expect(html).toContain('4 个快照');
    expect(html).toContain('活跃总数');
  });

  it('EmptyState renders a flat surface with title/description', () => {
    const html = renderToString(<EmptyState title="尚未导出" description="点击导出创建快照" />);
    expect(html).toContain('ds-empty-state');
    expect(html).toContain('尚未导出');
    expect(html).toContain('点击导出创建快照');
  });

  it('SectionCard renders header with title, subtitle and actions', () => {
    const html = renderToString(
      <SectionCard title="标题" subtitle="副标题" actions={<button>操作</button>}>
        <p>内容</p>
      </SectionCard>,
    );
    expect(html).toContain('ds-section-card');
    expect(html).toContain('ds-section-card__title');
    expect(html).toContain('标题');
    expect(html).toContain('副标题');
    expect(html).toContain('操作');
    expect(html).toContain('内容');
  });
});
