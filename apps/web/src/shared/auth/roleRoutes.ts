export const roleRoutePriority = [
  { role: 'OWNER', path: '/owner/tasks', label: '任务管理' },
  { role: 'OWNER', path: '/owner/schemas', label: 'Schema 管理' },
  { role: 'PLATFORM_ADMIN', path: '/platform/users', label: '用户管理' },
  { role: 'PLATFORM_ADMIN', path: '/platform/user-roles', label: '用户权限' },
  { role: 'PLATFORM_ADMIN', path: '/platform/llm', label: 'LLM 接入' },
  { role: 'PLATFORM_ADMIN', path: '/platform/audit-logs', label: '审计日志' },
  { role: 'LABELER', path: '/labeler/marketplace', label: '任务广场' },
  { role: 'LABELER', path: '/labeler/my', label: '我的数据' },
  { role: 'REVIEWER', path: '/reviewer/submissions', label: '审核队列' },
  { role: 'SENIOR_REVIEWER', path: '/reviewer/submissions?reviewLevel=senior_reviewer', label: '复核队列' },
] as const;

export function defaultPathForRoles(roles: string[]) {
  return roles.length ? '/home' : '/forbidden';
}
