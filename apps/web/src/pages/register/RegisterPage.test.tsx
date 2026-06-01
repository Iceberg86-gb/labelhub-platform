import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const registerMocks = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
  normalizeError: vi.fn(),
}));

let latestSubmit: ((values: Record<string, unknown>) => Promise<void>) | undefined;

function MockForm({
  children,
  onSubmit,
}: {
  children?: ReactNode;
  onSubmit?: (values: Record<string, unknown>) => Promise<void>;
}) {
  latestSubmit = onSubmit;
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
    mutateAsync: registerMocks.mutateAsync,
    normalizeError: registerMocks.normalizeError,
  }),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, to }: { children?: ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

import { RegisterPage } from './RegisterPage';

describe('RegisterPage design shell', () => {
  beforeEach(() => {
    latestSubmit = undefined;
    registerMocks.mutateAsync.mockReset();
    registerMocks.normalizeError.mockReset();
  });

  it('renders the shared auth shell and labeler-only copy', () => {
    const html = renderToString(<RegisterPage />);

    expect(html).toContain('login-shell login-shell--codex-light');
    expect(html).toContain('register-form');
    expect(html).toContain('默认开通 LABELER 权限');
    expect(html).toContain('/login');
  });

  it('shows one account field instead of separate username and display name fields', () => {
    const html = renderToString(<RegisterPage />);

    expect(html).toContain('账号');
    expect(html).not.toContain('账户');
    expect(html).not.toContain('用户名');
    expect(html).not.toContain('显示名');
  });

  it('maps the account field to username and displayName when registering', async () => {
    renderToString(<RegisterPage />);

    await latestSubmit?.({
      account: 'new_labeler',
      email: 'new@example.test',
      password: 'demo1234',
    });

    expect(registerMocks.mutateAsync).toHaveBeenCalledWith({
      username: 'new_labeler',
      displayName: 'new_labeler',
      email: 'new@example.test',
      password: 'demo1234',
    });
  });
});
