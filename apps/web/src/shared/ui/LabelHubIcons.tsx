import type { SVGProps } from 'react';

type LabelHubIconProps = SVGProps<SVGSVGElement> & {
  size?: number;
};

function IconBase({ size = 18, className, children, ...props }: LabelHubIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...props}
    >
      {children}
    </svg>
  );
}

export function IconTask(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <path className="icon-accent" stroke="currentColor" d="m8 9 1.4 1.4L12 7.8" />
      <path d="M14 9h3" />
      <path d="M8 14h.01" />
      <path d="M11 14h6" />
      <path d="M8 17h.01" />
      <path d="M11 17h5" />
    </IconBase>
  );
}

export function IconDesignerBlock(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <rect x="7" y="8" width="10" height="3.5" rx="1" />
      <rect x="7" y="14" width="6" height="2.5" rx="1" />
      <path className="icon-accent" stroke="currentColor" d="M16 14h1" />
      <path className="icon-accent" stroke="currentColor" d="M16 16.5h1" />
    </IconBase>
  );
}

export function IconAiAssist(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="5" width="16" height="14" rx="3" />
      <path d="M8 10h5" />
      <path d="M8 14h3" />
      <circle className="icon-accent" stroke="currentColor" cx="16" cy="10" r="1.5" />
      <path className="icon-accent" stroke="currentColor" d="M16 13v2" />
      <path className="icon-accent" stroke="currentColor" d="M14.5 14h3" />
    </IconBase>
  );
}

export function IconVersionHistory(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <path d="M5 8a7 7 0 1 1 .8 8.2" />
      <path className="icon-accent" stroke="currentColor" d="M5 4v4h4" />
      <path d="M12 8v4l2.5 1.5" />
    </IconBase>
  );
}

export function IconAnnotationWorkbench(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="5" width="16" height="14" rx="3" />
      <path d="M8 9h5" />
      <path d="M8 12h8" />
      <path d="M8 15h4" />
      <path className="icon-accent" stroke="currentColor" d="M15 14.5 19 17l-2.2.6-.7 2.1-1.1-5.2Z" />
    </IconBase>
  );
}

export function IconDataset(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <ellipse cx="12" cy="6" rx="7" ry="3" />
      <path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
      <path d="M5 12v4c0 1.7 3.1 3 7 3s7-1.3 7-3v-4" />
      <path className="icon-accent" stroke="currentColor" d="M9 10.5c.9.3 1.9.5 3 .5s2.1-.2 3-.5" />
    </IconBase>
  );
}

export function IconReviewFlow(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <rect x="4" y="5" width="16" height="14" rx="3" />
      <path d="M8 10h5" />
      <path d="M8 14h4" />
      <circle className="icon-accent" stroke="currentColor" cx="16" cy="11" r="2" />
      <path className="icon-accent" stroke="currentColor" d="m14.8 11 1 1 1.7-2" />
    </IconBase>
  );
}

export function IconStatusFlow(props: LabelHubIconProps) {
  return (
    <IconBase {...props}>
      <circle cx="6" cy="12" r="2" />
      <circle className="icon-accent" stroke="currentColor" cx="12" cy="12" r="2" />
      <circle cx="18" cy="12" r="2" />
      <path d="M8 12h2" />
      <path d="M14 12h2" />
      <path className="icon-accent" stroke="currentColor" d="m16.5 9.5 2 2.5-2 2.5" />
    </IconBase>
  );
}
