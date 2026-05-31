import { Typography } from '@douyinfe/semi-ui';
import { Link, Outlet } from 'react-router-dom';

export function PublicLayout() {
  return (
    <div className="public-shell public-shell--minimal">
      <header className="public-brand">
        <Link to="/" className="brand-link" aria-label="LabelHub home">
          <span className="brand-mark">LH</span>
          <Typography.Title heading={4} className="brand-title">
            LabelHub
          </Typography.Title>
        </Link>
      </header>

      <main className="public-content" aria-label="Public content">
        <Outlet />
      </main>
    </div>
  );
}
