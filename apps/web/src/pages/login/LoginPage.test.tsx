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

import { LoginPage } from './LoginPage';

describe('LoginPage design shell', () => {
  it('renders the welcome hero and quiet login card hooks', () => {
    const html = renderToString(<LoginPage />);

    expect(html).toContain('login-shell login-shell--codex-light');
    expect(html).toContain('login-hero');
    expect(html).toContain('welcome-hero');
    expect(html).toContain('login-card');
  });
});

