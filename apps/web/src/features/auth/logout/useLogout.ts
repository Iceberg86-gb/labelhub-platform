import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { clearSession } from '../../../shared/api/auth-storage';

export function useLogout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  return () => {
    queryClient.clear();
    clearSession();
    navigate('/login', { replace: true });
  };
}
