import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

function MockForm({ children }: { children?: ReactNode }) {
  return <form className="login-form">{children}</form>;
}

MockForm.Input = ({ label }: { label?: ReactNode }) => (
  <label>
    {label}
    <input />
  </label>
);

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children, className }: { children?: ReactNode; className?: string }) => (
    <button className={className}>{children}</button>
  ),
  Form: MockForm,
  Toast: {
    error: vi.fn(),
  },
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <h1 className={className}>{children}</h1>
    ),
  },
}));

vi.mock('../../features/auth/login/useLogin', () => ({
  useLogin: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
    normalizeError: vi.fn(),
  }),
}));

vi.mock('../../shared/api/auth-storage', () => ({
  clearSession: vi.fn(),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, className, to }: { children?: ReactNode; className?: string; to: string }) => (
    <a className={className} href={to}>
      {children}
    </a>
  ),
}));

import { LoginPage } from './LoginPage';

describe('LoginPage design shell', () => {
  it('renders the split brand panel and workflow hooks', () => {
    const html = renderToString(<LoginPage />);

    expect(html).toContain('login-shell login-shell--split');
    expect(html).toContain('login-brand-panel');
    expect(html).toContain('login-brand-lockup');
    expect(html).toContain('login-workflow-strip');
    expect(html).toContain('AI 辅助，人工把关');
    expect(html).toContain('可信导出');
    expect(html).toContain('login-card');
  });

  it('links to account registration from the login card', () => {
    const html = renderToString(<LoginPage />);

    expect(html).toContain('href="/register"');
    expect(html).toContain('创建新账号');
  });

  it('uses account wording for the username field', () => {
    const html = renderToString(<LoginPage />);

    expect(html).toContain('账号');
    expect(html).not.toContain('用户名');
  });
});
