import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

function MockForm({ children }: { children?: ReactNode }) {
  return <form className="register-form">{children}</form>;
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

vi.mock('../../features/auth/register/useRegister', () => ({
  useRegister: () => ({
    isPending: false,
    mutateAsync: vi.fn(),
    normalizeError: vi.fn(),
  }),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, to }: { children?: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

import { RegisterPage } from './RegisterPage';

describe('RegisterPage design shell', () => {
  it('renders the shared auth shell and labeler-only copy', () => {
    const html = renderToString(<RegisterPage />);

    expect(html).toContain('login-shell login-shell--codex-light');
    expect(html).toContain('register-form');
    expect(html).toContain('默认开通 LABELER 权限');
    expect(html).toContain('/login');
  });
});
