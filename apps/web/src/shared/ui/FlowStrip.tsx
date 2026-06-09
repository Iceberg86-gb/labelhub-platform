import { Fragment } from 'react';
import type { ReactNode } from 'react';

export type FlowStepState = 'done' | 'active' | 'pending';
export type FlowStepTone = 'success' | 'danger';

export type FlowStep = {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  state?: FlowStepState;
  tone?: FlowStepTone;
  note?: ReactNode;
};

type FlowStripProps = {
  steps: FlowStep[];
  className?: string;
  ariaLabel?: string;
};

/**
 * Canonical horizontal flow/steps strip. Consolidates the per-feature hand-written strips
 * (review flow, export workflow) into one token-driven component.
 */
export function FlowStrip({ steps, className, ariaLabel }: FlowStripProps) {
  return (
    <div className={['ds-flow-strip', className].filter(Boolean).join(' ')} aria-label={ariaLabel}>
      {steps.map((step, index) => (
        <Fragment key={step.key}>
          {index > 0 ? <span className="ds-flow-connector" aria-hidden="true" /> : null}
          <span className={stepClassName(step)}>
            {step.icon ? <span className="ds-flow-step__icon" aria-hidden="true">{step.icon}</span> : null}
            <span>{step.label}</span>
            {step.note ? <small>{step.note}</small> : null}
          </span>
        </Fragment>
      ))}
    </div>
  );
}

function stepClassName(step: FlowStep) {
  return [
    'ds-flow-step',
    step.state ? `ds-flow-step--${step.state}` : '',
    step.tone ? `ds-flow-step--${step.tone}` : '',
  ]
    .filter(Boolean)
    .join(' ');
}
