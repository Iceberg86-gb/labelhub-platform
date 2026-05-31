import { Button, Typography } from '@douyinfe/semi-ui';
import { IconExit, IconUserCircle } from '@douyinfe/semi-icons';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useLogout } from '../../features/auth/logout/useLogout';
import { getUser, SESSION_CHANGED_EVENT, type UserProfile } from '../api/auth-storage';
import { UNAUTHORIZED_EVENT } from '../api/client';
import { roleRoutePriority } from '../auth/roleRoutes';
import {
  IconAiAssist,
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

const roleSectionMeta = {
  OWNER: {
    title: '任务负责人',
    hint: '任务 / Schema',
  },
  LABELER: {
    title: '标注员',
    hint: '领取 / 作答',
  },
  REVIEWER: {
    title: '审核',
    hint: '初审',
  },
  SENIOR_REVIEWER: {
    title: '高级审核',
    hint: '复核',
  },
} as const;

const navItemHints: Record<string, string> = {
  '/owner/tasks': '任务配置与发布',
  '/owner/schemas': '标注表单搭建',
  '/owner/llm': '模型与 API Key',
  '/owner/audit-logs': '操作留痕追踪',
  '/labeler/marketplace': '领取可作答任务',
  '/labeler/my': '继续未完成数据',
  '/reviewer/submissions': '人工初审队列',
  '/reviewer/submissions?reviewLevel=senior_reviewer': '高级复核队列',
};

function menuIconFor(path: string) {
  if (path === '/owner/schemas') return <IconDesignerBlock className={navIconClassName} />;
  if (path === '/owner/llm') return <IconAiAssist className={navIconClassName} />;
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
  const [isCompactViewport, setIsCompactViewport] = useState(false);
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const [isSidebarDrawerOpen, setIsSidebarDrawerOpen] = useState(false);
  const logout = useLogout();
  const visibleMenuItems = user
    ? roleRoutePriority.filter((item) => user.roles.includes(item.role))
    : [];
  const visibleMenuSections = user
    ? Object.entries(roleSectionMeta)
        .map(([role, meta]) => ({
          role,
          meta,
          items: visibleMenuItems.filter((item) => item.role === role),
        }))
        .filter((section) => section.items.length)
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

  useEffect(() => {
    const mediaQuery = window.matchMedia('(max-width: 800px)');
    const syncViewport = () => setIsCompactViewport(mediaQuery.matches);

    syncViewport();
    mediaQuery.addEventListener('change', syncViewport);
    return () => mediaQuery.removeEventListener('change', syncViewport);
  }, []);

  const handleSidebarToggle = () => {
    if (isCompactViewport) {
      setIsSidebarDrawerOpen((isOpen) => !isOpen);
      return;
    }

    setIsSidebarCollapsed((isCollapsed) => !isCollapsed);
  };

  const handleNavItemClick = () => {
    if (isCompactViewport) {
      setIsSidebarDrawerOpen(false);
    }
  };

  const sidebarExpanded = isCompactViewport ? isSidebarDrawerOpen : !isSidebarCollapsed;

  return (
    <div
      className={[
        'app-shell app-shell--private',
        isSidebarCollapsed ? 'app-shell--sidebar-collapsed' : '',
        isSidebarDrawerOpen ? 'app-shell--sidebar-open' : '',
      ].filter(Boolean).join(' ')}
    >
      <header className="app-topbar">
        <div className="app-topbar__brand">
          <button
            type="button"
            className="app-sidebar-toggle"
            aria-label={sidebarExpanded ? '收起侧边栏' : '展开侧边栏'}
            aria-controls="app-sidebar"
            aria-expanded={sidebarExpanded}
            onClick={handleSidebarToggle}
          >
            <span className="app-sidebar-toggle__icon" aria-hidden>
              <span />
              <span />
              <span />
            </span>
          </button>

          <Link to="/home" className="brand-link" aria-label="LabelHub home">
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
        </div>

        <div className="app-topbar__context" aria-label="Workspace context">
          <span className="app-topbar__eyebrow">当前工作区</span>
          <strong>数据标注运营台</strong>
        </div>

        <div className="app-topbar__actions header-user">
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
        {isSidebarDrawerOpen ? (
          <button
            type="button"
            className="app-sidebar-scrim"
            aria-label="关闭侧边栏"
            onClick={() => setIsSidebarDrawerOpen(false)}
          />
        ) : null}

        <aside className="app-sidebar" id="app-sidebar" aria-label="Primary navigation">
          <div className="app-sidebar__header">
            <Typography.Text className="app-sidebar__eyebrow">Workspace</Typography.Text>
            <Typography.Title heading={5} className="app-sidebar__title">
              工作台
            </Typography.Title>
          </div>

          <nav className="nav-stack">
            {visibleMenuSections.length ? (
              visibleMenuSections.map((section) => (
                <section className="nav-section" aria-label={section.meta.title} key={section.role}>
                  <div className="nav-section__header">
                    <span className="nav-section__title">{section.meta.title}</span>
                    <span className="nav-section__hint">{section.meta.hint}</span>
                  </div>

                  <div className="nav-section__items">
                    {section.items.map((item) => (
                      <NavLink
                        key={item.path}
                        to={item.path}
                        title={`${item.label} · ${navItemHints[item.path]}`}
                        onClick={handleNavItemClick}
                        className={({ isActive }) => (isActive ? 'nav-item is-active' : 'nav-item')}
                      >
                        {menuIconFor(item.path)}
                        <span className="nav-item__body">
                          <span className="nav-item__label">{item.label}</span>
                          <span className="nav-item__hint">{navItemHints[item.path]}</span>
                        </span>
                      </NavLink>
                    ))}
                  </div>
                </section>
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
          <div className="app-content-shell">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
