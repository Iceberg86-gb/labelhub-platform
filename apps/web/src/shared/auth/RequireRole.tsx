import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { getUser } from '../api/auth-storage';

type RequireRoleProps = {
  roles: string[];
  children: ReactNode;
};

export function RequireRole({ roles, children }: RequireRoleProps) {
  const user = getUser();
  const hasRole = user?.roles.some((role) => roles.includes(role)) ?? false;

  if (!hasRole) {
    return <Navigate to="/forbidden" replace />;
  }

  return <>{children}</>;
}
