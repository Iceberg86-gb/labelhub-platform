import { Typography } from '@douyinfe/semi-ui';
import { IconAlertTriangle } from '@douyinfe/semi-icons';
import { Link } from 'react-router-dom';

export function PlatformPasswordChangePage() {
  return (
    <section className="platform-password-page" aria-label="Platform admin password change required">
      <div className="page-heading">
        <div>
          <Typography.Text className="page-eyebrow">平台治理</Typography.Text>
          <Typography.Title heading={2}>需要更新初始密码</Typography.Title>
          <Typography.Text type="tertiary">
            当前 PA 账号仍使用初始化凭证。请完成密码更新流程后再继续管理账号、授权和模型 Key。
          </Typography.Text>
        </div>
      </div>

      <div className="task-state-panel">
        <IconAlertTriangle className="lh-icon" aria-hidden />
        <Typography.Title heading={4}>首登安全要求已生效</Typography.Title>
        <Typography.Text type="tertiary">
          本批只落地 mustChangePassword 标识和强制提示，实际改密提交端点将在独立会话治理批次实现。
        </Typography.Text>
        <Link className="primary-action-link" to="/home">
          返回工作台
        </Link>
      </div>
    </section>
  );
}
