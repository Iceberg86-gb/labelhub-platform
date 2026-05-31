import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@douyinfe/semi-ui', () => ({
  Empty: ({ title, description }: { title?: ReactNode; description?: ReactNode }) => (
    <div>
      {title}
      {description}
    </div>
  ),
  Tag: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <span className={className}>{children}</span>
  ),
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h1 className={className}>{children}</h1>
    ),
  },
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, className, to }: { children?: ReactNode; className?: string; to: string }) => (
    <a className={className} href={to}>{children}</a>
  ),
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: () => ({
    displayName: 'Multi Role Demo',
    roles: ['OWNER', 'LABELER'],
  }),
}));

import { HomePage } from './HomePage';

describe('HomePage', () => {
  it('renders a large product preview and union role entry cards', () => {
    const html = renderToString(<HomePage />);

    expect(html).toContain('home-page');
    expect(html).toContain('home-product-preview');
    expect(html).toContain('OWNER');
    expect(html).toContain('LABELER');
    expect(html).toContain('任务管理');
    expect(html).toContain('Designer 画布');
    expect(html).toContain('标注工作台');
    expect(html).toContain('任务广场');
    expect(html).not.toContain('审核队列');
    expect(html).not.toContain('复核队列');
  });
});
