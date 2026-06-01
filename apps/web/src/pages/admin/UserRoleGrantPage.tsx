import { Button, Empty, Input, Pagination, Select, Space, Spin, Switch, Table, Toast, Typography } from '@douyinfe/semi-ui';
import { IconRefresh, IconSave } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { useGrantUserRoleMutation, type UserRoleProfile } from '../../features/user-roles/useGrantUserRoleMutation';
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
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState('REVIEWER');
  const [enabled, setEnabled] = useState(true);
  const [updatedUser, setUpdatedUser] = useState<UserRoleProfile | null>(null);
  const usersQuery = useUsersQuery({ page, size });
  const users = usersQuery.data?.items ?? [];

  const columns = useMemo(
    () => [
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
      },
      {
        title: '角色',
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
        title: '注册时间',
        dataIndex: 'createdAt',
        width: 150,
        render: (value?: string) => formatDateTime(value),
      },
      {
        title: '操作',
        width: 120,
        render: (_: unknown, user: UserAccountSummary) => (
          <Button size="small" theme="borderless" onClick={() => setUserId(String(user.id))}>
            填入授权
          </Button>
        ),
      },
    ],
    [],
  );

  const submit = async () => {
    const numericUserId = Number(userId);
    if (!Number.isInteger(numericUserId) || numericUserId <= 0) {
      Toast.error('请输入有效的用户 ID');
      return;
    }

    try {
      const result = await grantRole.mutateAsync({
        userId: numericUserId,
        body: { role, enabled },
      });
      setUpdatedUser(result);
      Toast.success(enabled ? '角色已授予' : '角色已撤销');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '用户角色调整失败');
    }
  };

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

      <div className="role-admin-panel">
        <label className="role-admin-field">
          <span>用户 ID</span>
          <Input value={userId} onChange={setUserId} placeholder="输入目标用户 ID" />
        </label>

        <label className="role-admin-field">
          <span>角色</span>
          <Select value={role} optionList={roleOptions} onChange={(value) => setRole(String(value))} />
        </label>

        <label className="role-admin-switch">
          <Switch checked={enabled} onChange={setEnabled} />
          <span>{enabled ? '授予角色' : '撤销角色'}</span>
        </label>

        <Button
          icon={<IconSave />}
          theme="solid"
          type="primary"
          className="primary-action-button"
          loading={grantRole.isPending}
          onClick={submit}
        >
          保存调整
        </Button>
      </div>

      {updatedUser ? (
        <section className="role-admin-result" aria-label="Updated user roles">
          <Typography.Text strong>{updatedUser.displayName}</Typography.Text>
          <Typography.Text type="tertiary">@{updatedUser.username}</Typography.Text>
          <div className="role-admin-result__roles">
            {updatedUser.roles.map((item) => (
              <RoleBadge key={item} role={item} />
            ))}
          </div>
        </section>
      ) : null}

      <section className="role-admin-list" aria-label="Registered users">
        <div className="task-toolbar">
          <div>
            <Typography.Title heading={4}>注册账号列表</Typography.Title>
            <Typography.Text type="tertiary">Owner 与 Senior Reviewer 可查看账号 ID，并据此授予审核角色。</Typography.Text>
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
              <Table columns={columns} dataSource={users} rowKey="id" pagination={false} />
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
