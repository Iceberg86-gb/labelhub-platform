import type { ReactNode } from 'react';

export function LabelHubNestedObjectField({ children }: { children?: ReactNode }) {
  return <div className="nested-renderer">{children}</div>;
}

