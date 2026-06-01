export const roleRoutePriority = [
  { role: 'OWNER', path: '/owner/tasks', label: '任务管理' },
  { role: 'OWNER', path: '/owner/schemas', label: 'Schema 管理' },
  { role: 'OWNER', path: '/owner/llm', label: 'LLM 接入' },
  { role: 'OWNER', path: '/owner/audit-logs', label: '审计日志' },
  { role: 'OWNER', path: '/admin/users', label: '用户管理' },
  { role: 'OWNER', path: '/admin/user-roles', label: '用户权限' },
  { role: 'LABELER', path: '/labeler/marketplace', label: '任务广场' },
  { role: 'LABELER', path: '/labeler/my', label: '我的数据' },
  { role: 'REVIEWER', path: '/reviewer/submissions', label: '审核队列' },
  { role: 'SENIOR_REVIEWER', path: '/reviewer/submissions?reviewLevel=senior_reviewer', label: '复核队列' },
  { role: 'SENIOR_REVIEWER', path: '/admin/user-roles', label: '用户权限' },
] as const;

export function defaultPathForRoles(roles: string[]) {
  return roles.length ? '/home' : '/forbidden';
}
