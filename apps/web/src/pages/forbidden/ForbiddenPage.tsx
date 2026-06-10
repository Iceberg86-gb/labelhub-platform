import { Button } from '@douyinfe/semi-ui';
import { Link } from 'react-router-dom';
import { EmptyState } from '../../shared/ui';

export function ForbiddenPage() {
  return (
    <section className="workspace-empty" aria-label="Forbidden">
      <EmptyState variant="inline" title="403 · 无权访问" description="当前账号没有访问该工作区功能的权限。" />
      <Link to="/login">
        <Button theme="solid" type="primary">
          返回登录
        </Button>
      </Link>
    </section>
  );
}
