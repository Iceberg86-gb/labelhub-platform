import type { ReactNode } from 'react';

type StatTileProps = {
  icon?: ReactNode;
  value: ReactNode;
  label?: ReactNode;
  tone?: 'accent' | 'neutral' | 'success';
  className?: string;
};

/**
 * Compact stat / summary tile (icon + value + caption). Consolidates the per-feature
 * "status pill" tiles (export console hero, dashboards) into one token-driven component.
 */
export function StatTile({ icon, value, label, tone = 'accent', className }: StatTileProps) {
  return (
    <div className={['ds-stat-tile', `ds-stat-tile--${tone}`, className].filter(Boolean).join(' ')}>
      {icon ? (
        <span className="ds-stat-tile__icon" aria-hidden="true">
          {icon}
        </span>
      ) : null}
      <span className="ds-stat-tile__body">
        <span className="ds-stat-tile__value">{value}</span>
        {label ? <span className="ds-stat-tile__label">{label}</span> : null}
      </span>
    </div>
  );
}
