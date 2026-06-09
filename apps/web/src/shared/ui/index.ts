/**
 * LabelHub Design System — single entry point.
 *
 * Import shared UI from here, never from individual files:
 *   import { StatusBadge, EmptyState, FlowStrip } from '../../shared/ui';
 *
 * See ./README.md for usage rules and the component catalogue.
 */

// Foundation
export * as tokens from './tokens';

// Base components
export { StatusBadge, type BadgeTone } from './StatusBadge';
export { TruncatedHash } from './TruncatedHash';
export { RoleBadge } from './RoleBadge';

// Composite components
export { EmptyState } from './EmptyState';
export { StatTile } from './StatTile';
export { SectionCard } from './SectionCard';
export { FlowStrip, type FlowStep, type FlowStepState, type FlowStepTone } from './FlowStrip';

// Icon set (pure SVG)
export * as LabelHubIcons from './LabelHubIcons';

// Note: AppLayout / PublicLayout are app-shell components — import them directly from
// './AppLayout' / './PublicLayout' (they pull in routing + auth infra and are kept out of
// this lightweight primitive barrel so unit tests can import UI primitives cheaply).
