import { Button, Empty, Pagination, Select, Space, Spin, Table, Toast, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconRefresh, IconSave } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useGrantUserRoleMutation } from '../../features/user-roles/useGrantUserRoleMutation';
import { useUsersQuery, type UserAccountSummary } from '../../features/user-roles/useUsersQuery';
import { getUser } from '../../shared/api/auth-storage';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const PAGE_SIZE = 10;
const roleOptions = [
  { label: 'LABELER', value: 'LABELER' },
  { label: 'REVIEWER', value: 'REVIEWER' },
  { label: 'SENIOR_REVIEWER', value: 'SENIOR_REVIEWER' },
];

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
}

export function UserRoleGrantPage() {
  const currentUser = getUser();
  const grantRole = useGrantUserRoleMutation();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [selectedRoles, setSelectedRoles] = useState<Record<number, string>>({});
  const usersQuery = useUsersQuery({ page, size });
  const users = usersQuery.data?.items ?? [];

  const selectedRoleFor = (user: UserAccountSummary) => selectedRoles[user.id] ?? 'REVIEWER';

  const updateSelectedRole = (userId: number, role: string) => {
    setSelectedRoles((old) => ({ ...old, [userId]: role }));
  };

  const adjustRole = async (user: UserAccountSummary, enabled: boolean) => {
    const targetRole = selectedRoleFor(user);
    if (!enabled && !user.roles.includes(targetRole)) {
      Toast.error('该账号当前没有这个角色');
      return;
    }
    try {
      await grantRole.mutateAsync({
        userId: user.id,
        body: { role: targetRole, enabled },
      });
      Toast.success(enabled ? '角色已授权' : '角色已删除');
      await usersQuery.refetch();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '用户角色调整失败');
    }
  };

  const columns = [
    {
      title: '注册账号',
      render: (_: unknown, user: UserAccountSummary) => (
        <div className="role-admin-user-cell">
          <Typography.Text strong>{user.displayName}</Typography.Text>
          <Typography.Text type="tertiary">@{user.username}</Typography.Text>
          {user.email ? <Typography.Text type="tertiary">{user.email}</Typography.Text> : null}
        </div>
      ),
    },
    {
      title: '用户 ID',
      dataIndex: 'id',
      width: 140,
      align: 'center' as const,
    },
    {
      title: '当前角色',
      width: 260,
      render: (_: unknown, user: UserAccountSummary) => (
        <div className="role-admin-result__roles">
          {user.roles.map((item) => (
            <RoleBadge key={item} role={item} />
          ))}
        </div>
      ),
    },
    {
      title: '授权角色',
      width: 190,
      align: 'center' as const,
      render: (_: unknown, user: UserAccountSummary) => (
        <div className="role-admin-role-control">
          <Select
            className="role-admin-role-select"
            value={selectedRoleFor(user)}
            optionList={roleOptions}
            onChange={(value) => updateSelectedRole(user.id, String(value))}
          />
        </div>
      ),
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      width: 150,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 180,
      align: 'center' as const,
      render: (_: unknown, user: UserAccountSummary) => {
        const selectedRole = selectedRoleFor(user);
        const alreadyHasRole = user.roles.includes(selectedRole);

        return (
          <div className="role-admin-actions">
            <Button
              icon={<IconSave />}
              size="small"
              theme="borderless"
              disabled={alreadyHasRole || grantRole.isPending}
              onClick={() => adjustRole(user, true)}
            >
              授权
            </Button>
            <Button
              icon={<IconDelete />}
              size="small"
              theme="borderless"
              type="danger"
              disabled={!alreadyHasRole || grantRole.isPending}
              onClick={() => adjustRole(user, false)}
            >
              删除
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <section className="role-admin-page" aria-label="User role management">
      <header className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">用户权限</Typography.Text>
          <Typography.Title heading={2}>授予审核角色</Typography.Title>
          <Typography.Text type="tertiary">
            当前操作者 {currentUser?.displayName ?? '-'}，只允许调整 LABELER、REVIEWER 与 SENIOR_REVIEWER。
          </Typography.Text>
        </div>
      </header>

      <section className="role-admin-list" aria-label="Registered users">
        <div className="task-toolbar">
          <div>
            <Typography.Title heading={4}>注册账号列表</Typography.Title>
            <Typography.Text type="tertiary">直接在账号行内授予或删除 LABELER、REVIEWER 与 SENIOR_REVIEWER。</Typography.Text>
          </div>
          <Space>
            <Typography.Text type="tertiary">共 {usersQuery.data?.total ?? 0} 个账号</Typography.Text>
            <Button icon={<IconRefresh />} onClick={() => usersQuery.refetch()} loading={usersQuery.isFetching}>
              刷新
            </Button>
          </Space>
        </div>

        <div className="task-table-surface">
          {usersQuery.isLoading ? (
            <div className="task-state-panel">
              <Spin size="large" />
            </div>
          ) : null}

          {usersQuery.isError ? (
            <div className="task-state-panel">
              <Empty
                title="注册账号加载失败"
                description={usersQuery.error instanceof Error ? usersQuery.error.message : '请稍后重试。'}
              />
              <Button onClick={() => usersQuery.refetch()}>重新加载</Button>
            </div>
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && users.length === 0 ? (
            <div className="task-state-panel">
              <Empty title="暂无注册账号" description="新用户注册后会出现在这里。" />
            </div>
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && users.length > 0 ? (
            <>
              <Table className="role-admin-table" columns={columns} dataSource={users} rowKey="id" pagination={false} />
              <div className="task-pagination">
                <Pagination
                  total={usersQuery.data?.total ?? 0}
                  currentPage={page}
                  pageSize={size}
                  showSizeChanger
                  onPageChange={setPage}
                  onPageSizeChange={(nextSize) => {
                    setPage(1);
                    setSize(nextSize);
                  }}
                />
              </div>
            </>
          ) : null}
        </div>
      </section>
    </section>
  );
}
