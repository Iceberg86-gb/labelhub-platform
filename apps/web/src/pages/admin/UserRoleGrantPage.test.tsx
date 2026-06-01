import type { ReactNode } from 'react';
import { renderToString } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';

const userRolesMocks = vi.hoisted(() => ({
  grantRole: {
    isPending: false,
    mutateAsync: vi.fn(),
  },
  usersQuery: {
    data: {
      items: [
        {
          id: 774670647,
          username: '774670647',
          displayName: '774670647',
          email: null,
          status: 'active',
          roles: ['LABELER'],
          createdAt: '2026-06-01T00:00:00Z',
        },
      ],
      page: 1,
      size: 10,
      total: 1,
    },
    isError: false,
    isLoading: false,
    refetch: vi.fn(),
  },
}));

vi.mock('@douyinfe/semi-ui', () => ({
  Button: ({ children }: { children?: ReactNode }) => <button type="button">{children}</button>,
  Empty: ({ description, title }: { description?: ReactNode; title?: ReactNode }) => (
    <div>{title}{description}</div>
  ),
  Input: ({ placeholder, value }: { placeholder?: string; value?: string }) => (
    <input placeholder={placeholder} value={value} readOnly />
  ),
  Pagination: () => <nav>pagination</nav>,
  Select: ({ value }: { value?: string }) => <select value={value} onChange={() => undefined} />,
  Space: ({ children }: { children?: ReactNode }) => <div>{children}</div>,
  Spin: () => <span>loading</span>,
  Switch: ({ checked }: { checked?: boolean }) => <input type="checkbox" checked={checked} readOnly />,
  Table: ({ dataSource }: { dataSource?: Array<Record<string, unknown>> }) => (
    <table>
      <tbody>
        {dataSource?.map((row) => (
          <tr key={String(row.id)}>
            <td>{String(row.id)}</td>
            <td>{String(row.displayName)}</td>
            <td>{Array.isArray(row.roles) ? row.roles.join(',') : ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  ),
  Toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
  Typography: {
    Text: ({ children, className }: { children?: ReactNode; className?: string }) => (
      <span className={className}>{children}</span>
    ),
    Title: ({ children }: { children?: ReactNode }) => <h1>{children}</h1>,
  },
}));

vi.mock('@douyinfe/semi-icons', () => ({
  IconRefresh: () => <span>refresh</span>,
  IconSave: () => <span>save</span>,
}));

vi.mock('../../features/user-roles/useGrantUserRoleMutation', () => ({
  useGrantUserRoleMutation: () => userRolesMocks.grantRole,
}));

vi.mock('../../features/user-roles/useUsersQuery', () => ({
  useUsersQuery: () => userRolesMocks.usersQuery,
}));

vi.mock('../../shared/api/auth-storage', () => ({
  getUser: () => ({ displayName: '演示 Owner', roles: ['OWNER'] }),
}));

import { UserRoleGrantPage } from './UserRoleGrantPage';

describe('UserRoleGrantPage', () => {
  it('shows registered users so owners can grant roles without guessing ids', () => {
    const html = renderToString(<UserRoleGrantPage />);

    expect(html).toContain('注册账号列表');
    expect(html).toContain('774670647');
    expect(html).toContain('LABELER');
  });
});
