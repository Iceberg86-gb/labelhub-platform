import { Button, Empty, Pagination, Popconfirm, Space, Spin, Table, Toast, Tooltip, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconRefresh } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useUsersQuery, type UserAccountSummary } from '../../features/user-roles/useUsersQuery';
import { useDeleteUserMutation } from '../../features/users/useDeleteUserMutation';
import { getUser } from '../../shared/api/auth-storage';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const PAGE_SIZE = 10;

function formatDateTime(value?: string) {
  return value
    ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
    : '-';
}

export function UserManagementPage() {
  const currentUser = getUser();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(PAGE_SIZE);
  const [deletingUserId, setDeletingUserId] = useState<number | null>(null);
  const usersQuery = useUsersQuery({ page, size });
  const deleteUser = useDeleteUserMutation();
  const users = usersQuery.data?.items ?? [];

  const handleDelete = async (user: UserAccountSummary) => {
    setDeletingUserId(user.id);
    try {
      await deleteUser.mutateAsync(user.id);
      Toast.success('账号已停用');
      await usersQuery.refetch();
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '账号停用失败');
    } finally {
      setDeletingUserId(null);
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
      title: '状态',
      dataIndex: 'status',
      width: 120,
      align: 'center' as const,
      render: (status: string) => <Typography.Text type="tertiary">{status}</Typography.Text>,
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      width: 150,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 150,
      align: 'center' as const,
      render: (_: unknown, user: UserAccountSummary) => {
        const isSelf = currentUser?.id === user.id;
        const isOwner = user.roles.includes('OWNER');
        const disabled = isSelf || isOwner || deleteUser.isPending;
        const disabledReason = isSelf ? '不能停用当前登录账号' : isOwner ? 'Owner 账号不可停用' : '';
        const button = (
          <Button
            className="account-admin-delete-action"
            icon={<IconDelete />}
            size="small"
            theme="borderless"
            disabled={disabled}
            loading={deletingUserId === user.id}
          >
            停用
          </Button>
        );

        if (disabled) {
          return <Tooltip content={disabledReason}>{button}</Tooltip>;
        }

        return (
          <Popconfirm
            title="停用账号"
            content={`确认停用 @${user.username}？该账号将不能再次登录，也不会出现在 active 用户列表中。`}
            okText="停用"
            cancelText="取消"
            onConfirm={() => handleDelete(user)}
          >
            {button}
          </Popconfirm>
        );
      },
    },
  ];

  return (
    <section className="account-admin-page" aria-label="User account management">
      <header className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">用户管理</Typography.Text>
          <Typography.Title heading={2}>账号管理</Typography.Title>
          <Typography.Text type="tertiary">
            仅 Owner 可停用账号。软删除只改变账号状态，不删除历史审核、ledger 或 submission 证据。
          </Typography.Text>
        </div>
      </header>

      <section className="account-admin-list" aria-label="Active registered users">
        <div className="task-toolbar">
          <div>
            <Typography.Title heading={4}>Active 账号</Typography.Title>
            <Typography.Text type="tertiary">停用后账号会从这里消失，删除时间通过审计日志追溯。</Typography.Text>
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
                title="账号列表加载失败"
                description={usersQuery.error instanceof Error ? usersQuery.error.message : '请稍后重试。'}
              />
              <Button onClick={() => usersQuery.refetch()}>重新加载</Button>
            </div>
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && users.length === 0 ? (
            <div className="task-state-panel">
              <Empty title="暂无 active 账号" description="新用户注册后会出现在这里。" />
            </div>
          ) : null}

          {!usersQuery.isLoading && !usersQuery.isError && users.length > 0 ? (
            <>
              <Table className="account-admin-table" columns={columns} dataSource={users} rowKey="id" pagination={false} />
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
