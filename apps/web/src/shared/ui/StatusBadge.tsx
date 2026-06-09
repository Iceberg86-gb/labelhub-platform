import { Tag } from '@douyinfe/semi-ui';
import type { ComponentProps, ReactNode } from 'react';

export type BadgeTone = 'accent' | 'info' | 'success' | 'warning' | 'danger' | 'neutral';

type StatusBadgeProps = {
  /** Semantic tone — maps to the canonical `semantic-tag--{tone}` token palette. */
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
  size?: ComponentProps<typeof Tag>['size'];
};

/**
 * Canonical status/semantic badge. Replaces ad-hoc `<Tag className="semantic-tag semantic-tag--X">`
 * usages so every status pill shares one token-driven palette.
 */
export function StatusBadge({ tone = 'neutral', children, className, size }: StatusBadgeProps) {
  return (
    <Tag size={size} className={['semantic-tag', `semantic-tag--${tone}`, className].filter(Boolean).join(' ')}>
      {children}
    </Tag>
  );
}
