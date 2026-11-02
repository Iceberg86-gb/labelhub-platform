# LabelHub Icons

These icons are hand-authored SVG assets for the LabelHub React frontend.

## Contract

- `viewBox="0 0 24 24"`
- `fill="none"`
- `stroke="currentColor"`
- `stroke-width="1.5"`
- `stroke-linecap="round"`
- `stroke-linejoin="round"`
- Accent details use `.icon-accent` and also keep `stroke="currentColor"`.
- No icon hard-codes the LabelHub blue. Selected and active states should be styled by CSS or component props.

## Files

- `icon-task.svg`
- `icon-dataset.svg`
- `icon-annotation-workbench.svg`
- `icon-drag-drop.svg`
- `icon-ai-assist.svg`
- `icon-import.svg`
- `icon-export.svg`
- `icon-field-text.svg`
- `icon-field-single-select.svg`
- `icon-field-image.svg`
- `icon-field-llm.svg`
- `icon-config.svg`
- `icon-version-history.svg`
- `icon-archive.svg`
- `icon-status-flow.svg`
- `icon-designer-block.svg`
- `icon-review-flow.svg`

## SVGR Usage

```tsx
import TaskIcon from "./icons/icon-task.svg?react";
import AiAssistIcon from "./icons/icon-ai-assist.svg?react";

export function NavItem({ active }: { active?: boolean }) {
  return (
    <button className={active ? "nav-item is-active" : "nav-item"}>
      <TaskIcon className="lh-icon" aria-hidden />
      <span>Tasks</span>
    </button>
  );
}

export function AiEvidenceBadge() {
  return <AiAssistIcon className="lh-icon lh-icon--muted" aria-hidden />;
}
```

```css
.lh-icon {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary, #4f575e);
  flex: none;
}

.nav-item.is-active .lh-icon {
  color: var(--color-text-primary, #171a1d);
}

.nav-item.is-active .lh-icon .icon-accent {
  color: var(--color-accent-blue, #2563eb);
}

.lh-icon--muted {
  color: var(--color-text-tertiary, #8a9299);
}

.lh-icon--muted .icon-accent {
  color: currentColor;
}
```

## Inline Component Pattern

```tsx
type IconProps = React.SVGProps<SVGSVGElement> & {
  size?: number;
  accentColor?: string;
};

export function IconTask({ size = 18, accentColor, style, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      role="img"
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      style={style}
      {...props}
    >
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <path className="icon-accent" stroke="currentColor" d="m8 9 1.4 1.4L12 7.8" style={{ color: accentColor }} />
      <path d="M14 9h3" />
      <path d="M8 14h.01" />
      <path d="M11 14h6" />
      <path d="M8 17h.01" />
      <path d="M11 17h5" />
    </svg>
  );
}
```

