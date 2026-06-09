import type { ReactNode } from 'react';

type SectionCardProps = {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
};

/**
 * Canonical bordered surface with an optional header (title + subtitle + actions).
 * Replaces the many per-feature `*-panel` / `*-card` wrappers.
 */
export function SectionCard({ title, subtitle, actions, children, className }: SectionCardProps) {
  const hasHeader = Boolean(title || subtitle || actions);
  return (
    <section className={['ds-section-card', className].filter(Boolean).join(' ')}>
      {hasHeader ? (
        <header className="ds-section-card__header">
          <div className="ds-section-card__heading">
            {title ? <span className="ds-section-card__title">{title}</span> : null}
            {subtitle ? <span className="ds-section-card__subtitle">{subtitle}</span> : null}
          </div>
          {actions ? <div className="ds-section-card__actions">{actions}</div> : null}
        </header>
      ) : null}
      {children}
    </section>
  );
}
