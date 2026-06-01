import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '../shared/ui/AppLayout';
import { PublicLayout } from '../shared/ui/PublicLayout';
import { AuthRedirectBridge } from '../shared/auth/AuthRedirectBridge';
import { RequireAuth } from '../shared/auth/RequireAuth';
import { RequireRole } from '../shared/auth/RequireRole';
import { getAccessToken, getUser } from '../shared/api/auth-storage';
import { defaultPathForRoles } from '../shared/auth/roleRoutes';
import { LoginPage } from '../pages/login/LoginPage';
import { RegisterPage } from '../pages/register/RegisterPage';
import { HomePage } from '../pages/home/HomePage';
import { UserRoleGrantPage } from '../pages/admin/UserRoleGrantPage';
import { OwnerTasksListPage } from '../pages/owner/OwnerTasksListPage';
import { OwnerTaskDetailPage } from '../pages/owner/OwnerTaskDetailPage';
import { OwnerSubmissionPage } from '../pages/owner/OwnerSubmissionPage';
import { OwnerSchemasListPage } from '../pages/owner/OwnerSchemasListPage';
import { OwnerSchemaDesignerPage } from '../pages/owner/OwnerSchemaDesignerPage';
import { OwnerAuditLogsPage } from '../pages/owner/OwnerAuditLogsPage';
import { OwnerLlmSettingsPage } from '../pages/owner/OwnerLlmSettingsPage';
import { LabelerMarketplacePage } from '../pages/labeler/LabelerMarketplacePage';
import { LabelerSessionPage } from '../pages/labeler/LabelerSessionPage';
import { LabelerMySessionsPage } from '../pages/labeler/LabelerMySessionsPage';
import { LabelerSubmissionPage } from '../pages/labeler/LabelerSubmissionPage';
import { LabelerPlaceholderPage } from '../pages/labeler/LabelerPlaceholderPage';
import { ReviewerPlaceholderPage } from '../pages/reviewer/ReviewerPlaceholderPage';
import { ReviewerQueuePage } from '../pages/reviewer/ReviewerQueuePage';
import { ReviewerSubmissionPage } from '../pages/reviewer/ReviewerSubmissionPage';
import { ForbiddenPage } from '../pages/forbidden/ForbiddenPage';

function RootRedirect() {
  const user = getUser();

  return getAccessToken() && user ? (
    <Navigate to={defaultPathForRoles(user.roles)} replace />
  ) : (
    <Navigate to="/login" replace />
  );
}

export const router = createBrowserRouter(
  [
    {
      path: '/login',
      element: <PublicLayout />,
      children: [
        {
          index: true,
          element: <LoginPage />,
        },
      ],
    },
    {
      path: '/register',
      element: <PublicLayout />,
      children: [
        {
          index: true,
          element: <RegisterPage />,
        },
      ],
    },
    {
      path: '/forbidden',
      element: <PublicLayout />,
      children: [
        {
          index: true,
          element: <ForbiddenPage />,
        },
      ],
    },
    {
      path: '/',
      element: (
        <AuthRedirectBridge>
          <AppLayout />
        </AuthRedirectBridge>
      ),
      children: [
        {
          index: true,
          element: <RootRedirect />,
        },
        {
          path: 'home',
          element: (
            <RequireAuth>
              <HomePage />
            </RequireAuth>
          ),
        },
        {
          path: 'owner/tasks',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerTasksListPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/tasks/:taskId',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerTaskDetailPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/tasks/:taskId/submissions/:submissionId',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerSubmissionPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/schemas',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerSchemasListPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/schemas/:schemaId/design',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerSchemaDesignerPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/llm',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerLlmSettingsPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'owner/audit-logs',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER']}>
                <OwnerAuditLogsPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'admin/user-roles',
          element: (
            <RequireAuth>
              <RequireRole roles={['OWNER', 'SENIOR_REVIEWER']}>
                <UserRoleGrantPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'labeler/marketplace',
          element: (
            <RequireAuth>
              <RequireRole roles={['LABELER']}>
                <LabelerMarketplacePage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'labeler/sessions/:sessionId',
          element: (
            <RequireAuth>
              <RequireRole roles={['LABELER']}>
                <LabelerSessionPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'labeler/my',
          element: (
            <RequireAuth>
              <RequireRole roles={['LABELER']}>
                <LabelerMySessionsPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'labeler/submissions/:submissionId',
          element: (
            <RequireAuth>
              <RequireRole roles={['LABELER']}>
                <LabelerSubmissionPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'labeler/placeholder',
          element: (
            <RequireAuth>
              <RequireRole roles={['LABELER']}>
                <LabelerPlaceholderPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'reviewer/submissions',
          element: (
            <RequireAuth>
              <RequireRole roles={['REVIEWER', 'SENIOR_REVIEWER']}>
                <ReviewerQueuePage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'reviewer/submissions/:submissionId',
          element: (
            <RequireAuth>
              <RequireRole roles={['REVIEWER', 'SENIOR_REVIEWER']}>
                <ReviewerSubmissionPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
        {
          path: 'reviewer/placeholder',
          element: (
            <RequireAuth>
              <RequireRole roles={['REVIEWER', 'SENIOR_REVIEWER']}>
                <ReviewerPlaceholderPage />
              </RequireRole>
            </RequireAuth>
          ),
        },
      ],
    },
  ],
  {
    future: {
      v7_relativeSplatPath: true,
    },
  },
);
