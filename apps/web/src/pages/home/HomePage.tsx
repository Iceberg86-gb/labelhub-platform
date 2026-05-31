import { Typography } from '@douyinfe/semi-ui';
import { Link } from 'react-router-dom';
import { getUser } from '../../shared/api/auth-storage';
import { roleRoutePriority } from '../../shared/auth/roleRoutes';
import {
  IconAnnotationWorkbench,
  IconDesignerBlock,
  IconReviewFlow,
  IconStatusFlow,
  IconTask,
  IconVersionHistory,
} from '../../shared/ui/LabelHubIcons';
import { RoleBadge } from '../../shared/ui/RoleBadge';

const entryMeta = {
  '/owner/tasks': {
    title: '任务管理',
    description: '创建、配置和发布标注任务',
    icon: IconTask,
    tone: 'primary',
  },
  '/owner/schemas': {
    title: 'Designer 画布',
    description: '搭建字段、容器和联动规则',
    icon: IconDesignerBlock,
    tone: 'accent',
  },
  '/owner/audit-logs': {
    title: '审计日志',
    description: '追踪操作留痕和版本记录',
    icon: IconVersionHistory,
    tone: 'muted',
  },
  '/labeler/marketplace': {
    title: '任务广场',
    description: '领取可作答任务',
    icon: IconTask,
    tone: 'info',
  },
  '/labeler/my': {
    title: '标注工作台',
    description: '继续未完成数据和提交记录',
    icon: IconAnnotationWorkbench,
    tone: 'info',
  },
  '/reviewer/submissions': {
    title: '审核队列',
    description: '处理人工初审、通过或打回',
    icon: IconReviewFlow,
    tone: 'warning',
  },
  '/reviewer/submissions?reviewLevel=senior_reviewer': {
    title: '复核队列',
    description: '高级审核终态裁决',
    icon: IconStatusFlow,
    tone: 'warning',
  },
} as const;

export function HomePage() {
  const user = getUser();
  const roles = user?.roles ?? [];
  const entries = roleRoutePriority
    .filter((item) => roles.includes(item.role))
    .map((item) => ({
      ...item,
      meta: entryMeta[item.path as keyof typeof entryMeta],
    }))
    .filter((entry) => entry.meta);

  return (
    <section className="home-page" aria-label="LabelHub welcome home">
      <header className="home-hero">
        <div className="home-hero__copy">
          <Typography.Text className="home-hero__eyebrow">欢迎回来</Typography.Text>
          <Typography.Title heading={1} className="home-hero__title">
            从一个清晰入口开始今天的标注流转
          </Typography.Title>
          <Typography.Text className="home-hero__subtitle">
            LabelHub 将任务配置、Designer 搭建、标注作答和人工审核收束在同一套克制的工具界面里。
          </Typography.Text>
        </div>

        <div className="home-role-tags" aria-label="当前角色权限">
          {roles.map((role) => (
            <RoleBadge key={role} role={role} />
          ))}
        </div>
      </header>

      <div className="home-product-preview" aria-hidden>
        <div className="home-preview-topbar">
          <span className="home-preview-menu" />
          <span className="home-preview-dot" />
          <span className="home-preview-line home-preview-line--short" />
          <span className="home-preview-line" />
          <span className="home-preview-line home-preview-line--muted" />
          <span className="home-preview-action" />
        </div>

        <div className="home-preview-grid">
          <div className="home-preview-rail">
            <span className="is-active" />
            <span />
            <span />
            <span />
            <span />
          </div>

          <div className="home-preview-materials">
            {Array.from({ length: 8 }).map((_, index) => (
              <span key={index} />
            ))}
          </div>

          <div className="home-preview-canvas">
            <div className="home-preview-tabs">
              <span className="is-active" />
              <span />
              <span />
            </div>
            <div className="home-preview-dropzone">+</div>
            <div className="home-preview-block">
              <span />
              <span />
              <span />
            </div>
            <div className="home-preview-block home-preview-block--tall">
              <span />
              <span />
            </div>
          </div>

          <div className="home-preview-evidence">
            <div className="home-preview-flow">
              <span className="is-done" />
              <span className="is-done" />
              <span />
            </div>
            <div className="home-preview-columns">
              <span />
              <span />
            </div>
            <div className="home-preview-ai">
              <span />
              <span />
              <span />
            </div>
          </div>
        </div>
      </div>

      <section className="home-entry-grid" aria-label="可用入口">
        {entries.map(({ path, meta }) => {
          const Icon = meta.icon;

          return (
            <Link className={`home-entry-card home-entry-card--${meta.tone}`} to={path} key={path}>
              <span className="home-entry-card__icon">
                <Icon />
              </span>
              <span>
                <strong>{meta.title}</strong>
                <span>{meta.description}</span>
              </span>
            </Link>
          );
        })}
      </section>
    </section>
  );
}
