import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { getAccessToken, getUser } from '../api/auth-storage';

type RequireAuthProps = {
  children: ReactNode;
};

export function RequireAuth({ children }: RequireAuthProps) {
  const accessToken = getAccessToken();
  const user = getUser();

  if (!accessToken || !user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
