import { Button, Input, Select, Switch, Toast, Typography } from '@douyinfe/semi-ui';
import { IconSave } from '@douyinfe/semi-icons';
import { useState } from 'react';
import { useGrantUserRoleMutation, type UserRoleProfile } from '../../features/user-roles/useGrantUserRoleMutation';
import { getUser } from '../../shared/api/auth-storage';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const roleOptions = [
  { label: 'LABELER', value: 'LABELER' },
  { label: 'REVIEWER', value: 'REVIEWER' },
  { label: 'SENIOR_REVIEWER', value: 'SENIOR_REVIEWER' },
];

export function UserRoleGrantPage() {
  const currentUser = getUser();
  const grantRole = useGrantUserRoleMutation();
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState('REVIEWER');
  const [enabled, setEnabled] = useState(true);
  const [updatedUser, setUpdatedUser] = useState<UserRoleProfile | null>(null);

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
    </section>
  );
}
