import { Button, Typography } from '@douyinfe/semi-ui';
import { IconExit, IconUserCircle } from '@douyinfe/semi-icons';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useLogout } from '../../features/auth/logout/useLogout';
import { getUser, SESSION_CHANGED_EVENT, type UserProfile } from '../api/auth-storage';
import { UNAUTHORIZED_EVENT } from '../api/client';
import { roleRoutePriority } from '../auth/roleRoutes';
import {
  IconAnnotationWorkbench,
  IconDataset,
  IconDesignerBlock,
  IconReviewFlow,
  IconStatusFlow,
  IconTask,
  IconVersionHistory,
} from './LabelHubIcons';
import { RoleBadge } from './RoleBadge';

const navIconClassName = 'nav-item__icon lh-icon';

function menuIconFor(path: string) {
  if (path === '/owner/schemas') return <IconDesignerBlock className={navIconClassName} />;
  if (path === '/owner/audit-logs') return <IconVersionHistory className={navIconClassName} />;
  if (path === '/labeler/marketplace') return <IconTask className={navIconClassName} />;
  if (path === '/labeler/my') return <IconAnnotationWorkbench className={navIconClassName} />;
  if (path.startsWith('/reviewer/submissions?reviewLevel=senior_reviewer')) {
    return <IconStatusFlow className={navIconClassName} />;
  }
  if (path === '/reviewer/submissions') return <IconReviewFlow className={navIconClassName} />;
  if (path.includes('dataset')) return <IconDataset className={navIconClassName} />;
  return <IconTask className={navIconClassName} />;
}

export function AppLayout() {
  const [user, setUser] = useState<UserProfile | null>(() => getUser());
  const logout = useLogout();
  const visibleMenuItems = user
    ? roleRoutePriority.filter((item) => user.roles.includes(item.role))
    : [];

  useEffect(() => {
    const syncUser = () => setUser(getUser());

    window.addEventListener('storage', syncUser);
    window.addEventListener(SESSION_CHANGED_EVENT, syncUser);
    window.addEventListener(UNAUTHORIZED_EVENT, syncUser);

    return () => {
      window.removeEventListener('storage', syncUser);
      window.removeEventListener(SESSION_CHANGED_EVENT, syncUser);
      window.removeEventListener(UNAUTHORIZED_EVENT, syncUser);
    };
  }, []);

  return (
    <div className="app-shell app-shell--private">
      <header className="app-topbar">
        <Link to="/" className="brand-link" aria-label="LabelHub home">
          <span className="brand-mark" aria-hidden>
            LH
          </span>
          <span>
            <Typography.Title heading={4} className="brand-title">
              LabelHub
            </Typography.Title>
            <Typography.Text className="brand-subtitle">AI 监督信号治理系统</Typography.Text>
          </span>
        </Link>

        <div className="header-user">
          {user ? (
            <>
              <IconUserCircle aria-hidden />
              <span className="user-name">{user.displayName}</span>
              {user.roles.map((role) => (
                <RoleBadge key={role} role={role} />
              ))}
              <Button
                size="small"
                theme="borderless"
                icon={<IconExit />}
                className="header-logout-button primary-action-link"
                onClick={logout}
              >
                登出
              </Button>
            </>
          ) : (
            <Link to="/login" className="login-link">
              请登录
            </Link>
          )}
        </div>
      </header>

      <div className="app-body">
        <aside className="app-sidebar" aria-label="Primary navigation">
          <nav className="nav-stack">
            {visibleMenuItems.length ? (
              visibleMenuItems.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) => (isActive ? 'nav-item is-active' : 'nav-item')}
                >
                  {menuIconFor(item.path)}
                  <span>{item.label}</span>
                </NavLink>
              ))
            ) : (
              <div className="nav-empty" role="note">
                <Typography.Text type="tertiary">
                  {user ? '暂无可用功能，请联系管理员' : '请先登录后查看功能入口'}
                </Typography.Text>
              </div>
            )}
          </nav>
        </aside>

        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
