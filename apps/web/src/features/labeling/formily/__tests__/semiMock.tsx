import type { ChangeEvent, ReactNode } from 'react';

export function Input({
  value,
  onChange,
  type,
}: {
  value?: string;
  onChange?: (value: string) => void;
  type?: string;
}) {
  return <input type={type ?? 'text'} value={value ?? ''} onChange={(event) => onChange?.(event.target.value)} readOnly />;
}

export function InputNumber({ value, onChange }: { value?: number; onChange?: (value: number) => void }) {
  return (
    <input
      type="number"
      value={value ?? ''}
      onChange={(event: ChangeEvent<HTMLInputElement>) => onChange?.(Number(event.target.value))}
      readOnly
    />
  );
}

export function DatePicker({ value, onChange }: { value?: string; onChange?: (value: string) => void }) {
  return <input type="date" value={value ?? ''} onChange={(event) => onChange?.(event.target.value)} readOnly />;
}

export function Select({ value, children, multiple }: { value?: string | string[]; children?: ReactNode; multiple?: boolean }) {
  return (
    <select multiple={multiple} value={value} disabled>
      {children}
    </select>
  );
}

Select.Option = function Option({ value, children }: { value: string; children?: ReactNode }) {
  return <option value={value}>{children}</option>;
};

export function Tag({ children }: { children?: ReactNode }) {
  return <span>{children}</span>;
}

export const Typography = {
  Text({ children }: { children?: ReactNode }) {
    return <span>{children}</span>;
  },
};
