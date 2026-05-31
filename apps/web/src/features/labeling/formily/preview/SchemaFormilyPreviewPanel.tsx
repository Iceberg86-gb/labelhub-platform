import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { SchemaFormilyRenderer } from '../SchemaFormilyRenderer';

type SchemaFormilyPreviewPanelProps = {
  schemaFields: SchemaField[];
};

export function createEmptyPreviewPayload(): AnswerPayload {
  return {};
}

/**
 * One-way preview: this component READS designer.schemaFields and RENDERS
 * via SchemaFormilyRenderer. The preview's internal AnswerPayload state is
 * local to this component and is NOT propagated back to the designer. The
 * Reset button clears local state only. Designer is the single source of
 * truth for schemaFields editing.
 */
export function SchemaFormilyPreviewPanel({ schemaFields }: SchemaFormilyPreviewPanelProps) {
  const [previewValue, setPreviewValue] = useState<AnswerPayload>(() => createEmptyPreviewPayload());
  const previewValueRef = useRef(previewValue);
  const pendingPreviewValueRef = useRef<AnswerPayload | null>(null);
  const previewUpdateTimerRef = useRef<number | null>(null);
  const fieldCount = schemaFields.length;
  const resetPreview = () => {
    const emptyPayload = createEmptyPreviewPayload();
    pendingPreviewValueRef.current = null;
    if (previewUpdateTimerRef.current != null) {
      window.clearTimeout(previewUpdateTimerRef.current);
      previewUpdateTimerRef.current = null;
    }
    previewValueRef.current = emptyPayload;
    setPreviewValue(emptyPayload);
  };
  const subtitle = useMemo(() => (fieldCount === 0 ? '添加字段后这里会展示运行时表单。' : `${fieldCount} 个字段 · Formily 运行时预览`), [fieldCount]);

  useEffect(() => {
    previewValueRef.current = previewValue;
  }, [previewValue]);

  useEffect(() => () => {
    if (previewUpdateTimerRef.current != null) {
      window.clearTimeout(previewUpdateTimerRef.current);
    }
  }, []);

  const handlePreviewChange = useCallback((nextValue: AnswerPayload) => {
    if (samePreviewPayload(previewValueRef.current, nextValue)) {
      return;
    }

    pendingPreviewValueRef.current = nextValue;
    if (previewUpdateTimerRef.current != null) {
      return;
    }

    previewUpdateTimerRef.current = window.setTimeout(() => {
      previewUpdateTimerRef.current = null;
      const pendingValue = pendingPreviewValueRef.current;
      pendingPreviewValueRef.current = null;
      if (!pendingValue || samePreviewPayload(previewValueRef.current, pendingValue)) {
        return;
      }
      previewValueRef.current = pendingValue;
      setPreviewValue(pendingValue);
    }, 0);
  }, []);

  return (
    <aside className="designer-preview-panel" aria-label="Formily schema preview">
      <div className="designer-preview-panel__header">
        <div>
          <h3>Schema 预览</h3>
          <p>{subtitle}</p>
        </div>
        <button type="button" className="designer-preview-panel__reset" onClick={resetPreview}>
          重置预览
        </button>
      </div>
      <div className="designer-preview-panel__body">
        <SchemaFormilyRenderer
          schemaFields={schemaFields}
          value={previewValue}
          readOnly={false}
          onChange={handlePreviewChange}
        />
      </div>
    </aside>
  );
}

function samePreviewPayload(left: AnswerPayload, right: AnswerPayload): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}
