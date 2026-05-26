import { ReactNode, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { UNAUTHORIZED_EVENT } from '../api/client';

type AuthRedirectBridgeProps = {
  children: ReactNode;
};

export function AuthRedirectBridge({ children }: AuthRedirectBridgeProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    const handleUnauthorized = () => {
      queryClient.clear();
      navigate('/login', { replace: true });
    };

    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate, queryClient]);

  return <>{children}</>;
}
