import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <button className={className}>{children}</button>
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
  NavLink: ({
    children,
    className,
    to,
  }: {
    children?: ReactNode;
    className?: (state: { isActive: boolean }) => string;
    to: string;
  }) => (
    <a className={className?.({ isActive: to === '/owner/tasks' })} href={to}>{children}</a>
  ),
  Outlet: () => <div data-testid="outlet" />,
}));

vi.mock('../../features/auth/logout/useLogout', () => ({
  useLogout: () => vi.fn(),
}));

vi.mock('../api/auth-storage', () => ({
  SESSION_CHANGED_EVENT: 'labelhub-session-changed',
  getUser: () => ({
    displayName: 'Owner Demo',
    roles: ['OWNER'],
  }),
}));

vi.mock('../api/client', () => ({
  UNAUTHORIZED_EVENT: 'labelhub-unauthorized',
}));

import { AppLayout } from './AppLayout';

describe('AppLayout design shell', () => {
  it('renders the token-backed light shell with currentColor sidebar icons', () => {
    const html = renderToString(<AppLayout />);

    expect(html).toContain('app-shell app-shell--private');
    expect(html).toContain('app-sidebar');
    expect(html).toContain('nav-item__icon lh-icon');
    expect(html).toContain('class="icon-accent"');
    expect(html).toContain('is-active');
  });
});
