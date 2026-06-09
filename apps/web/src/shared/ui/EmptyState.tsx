import { Empty } from '@douyinfe/semi-ui';
import type { ReactNode } from 'react';

type EmptyStateProps = {
  title?: ReactNode;
  description?: ReactNode;
  image?: ReactNode;
  action?: ReactNode;
  /** `block` = centered standalone surface; `inline` = compact, transparent. */
  variant?: 'block' | 'inline';
  className?: string;
};

/**
 * Canonical empty state. Wraps Semi `Empty` in a flat, token-driven surface — replacing the
 * per-feature decorative empty panels (glow orbs, fake-document mockups).
 */
export function EmptyState({ title, description, image, action, variant = 'block', className }: EmptyStateProps) {
  return (
    <div
      className={['ds-empty-state', variant === 'inline' ? 'ds-empty-state--inline' : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      <Empty image={image} title={title} description={description} />
      {action ? <div className="ds-empty-state__action">{action}</div> : null}
    </div>
  );
}
