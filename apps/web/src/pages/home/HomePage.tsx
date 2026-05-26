import { Empty, Typography } from '@douyinfe/semi-ui';

export function HomePage() {
  return (
    <section className="workspace-empty" aria-label="Workspace placeholder">
      <Empty
        title="Owner 工作区骨架已就绪"
        description="登录、任务列表与详情将在后续阶段接入；当前页面用于验证路由、Layout 与 API 代理。"
      />
      <Typography.Text type="tertiary">
        使用左侧导航进入任务管理入口，顶部区域会根据登录状态显示用户信息。
      </Typography.Text>
    </section>
  );
}
