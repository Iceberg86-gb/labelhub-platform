/**
 * Design-system token references. These mirror the CSS custom properties defined in
 * `docs/design-assets/tokens/tokens.css`. Use them instead of hardcoding px / hex values
 * when a token is unavoidable in inline styles (prefer className + styles.css where possible).
 */

export const fontSize = {
  display: 'var(--font-size-display)',
  pageTitle: 'var(--font-size-page-title)',
  sectionTitle: 'var(--font-size-section-title)',
  cardTitle: 'var(--font-size-card-title)',
  body: 'var(--font-size-body)',
  bodyStrong: 'var(--font-size-body-strong)',
  small: 'var(--font-size-small)',
  caption: 'var(--font-size-caption)',
  tableHeader: 'var(--font-size-table-header)',
  microLabel: 'var(--font-size-micro-label)',
} as const;

export const fontFamily = {
  sans: 'var(--font-family-sans)',
  mono: 'var(--font-family-mono)',
} as const;

export const color = {
  textPrimary: 'var(--color-text-primary)',
  textSecondary: 'var(--color-text-secondary)',
  textTertiary: 'var(--color-text-tertiary)',
  textInverse: 'var(--color-text-inverse)',
  appBackground: 'var(--color-app-background)',
  mainSurface: 'var(--color-main-surface)',
  surfaceSubtle: 'var(--color-surface-subtle)',
  panelSurface: 'var(--color-panel-surface)',
  borderSubtle: 'var(--color-border-subtle)',
  borderDefault: 'var(--color-border-default)',
  borderStrong: 'var(--color-border-strong)',
  accentBlue: 'var(--color-accent-blue)',
  accentBlueSoft: 'var(--color-accent-blue-soft)',
  success: 'var(--color-success)',
  successSoft: 'var(--color-success-soft)',
  warning: 'var(--color-warning)',
  warningSoft: 'var(--color-warning-soft)',
  danger: 'var(--color-danger)',
  dangerSoft: 'var(--color-danger-soft)',
  info: 'var(--color-info)',
  infoSoft: 'var(--color-info-soft)',
} as const;

export const space = {
  2: 'var(--space-2)',
  4: 'var(--space-4)',
  8: 'var(--space-8)',
  12: 'var(--space-12)',
  16: 'var(--space-16)',
  20: 'var(--space-20)',
  24: 'var(--space-24)',
  32: 'var(--space-32)',
} as const;

export const radius = {
  buttonSm: 'var(--radius-button-sm)',
  button: 'var(--radius-button-default)',
  input: 'var(--radius-input)',
  card: 'var(--radius-card)',
  panel: 'var(--radius-panel)',
  pill: 'var(--radius-pill)',
} as const;
