import { Tag } from '@douyinfe/semi-ui';

type RoleBadgeProps = {
  role: string;
};

const roleMeta: Record<string, { label: string; className: string }> = {
  OWNER: { label: 'OWNER', className: 'role-badge--owner' },
  LABELER: { label: 'LABELER', className: 'role-badge--labeler' },
  REVIEWER: { label: 'REVIEWER', className: 'role-badge--reviewer' },
};

export function RoleBadge({ role }: RoleBadgeProps) {
  const meta = roleMeta[role] ?? { label: role, className: 'role-badge--unknown' };

  return (
    <Tag className={`role-badge ${meta.className}`} size="large">
      {meta.label}
    </Tag>
  );
}
