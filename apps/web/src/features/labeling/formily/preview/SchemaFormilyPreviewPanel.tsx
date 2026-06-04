import type { Form } from '@formily/core';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createVisibleSchemaFieldsSelector } from '../../../../entities/labeling/visibleSchemaFields';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';
import type { AnswerPayload } from '../../../../entities/submission/answerPayload';
import { formilyValuesToAnswerPayload } from '../adapters/formilyValuesToAnswerPayload';
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
  const formPollTimerRef = useRef<number | null>(null);
  const formValuesSnapshotRef = useRef<string>('');
  const visibleFieldsSelector = useMemo(() => createVisibleSchemaFieldsSelector(), []);
  const fieldCount = schemaFields.length;
  const visibleSchemaFields = useMemo(
    () => visibleFieldsSelector(schemaFields, previewValue),
    [previewValue, schemaFields, visibleFieldsSelector],
  );
  const visibleSchemaSignature = useMemo(
    () => visibleSchemaFields.map((field) => field.stableId).join('\u001f'),
    [visibleSchemaFields],
  );
  const resetPreview = () => {
    const emptyPayload = createEmptyPreviewPayload();
    pendingPreviewValueRef.current = null;
    if (previewUpdateTimerRef.current != null) {
      window.clearTimeout(previewUpdateTimerRef.current);
      previewUpdateTimerRef.current = null;
    }
    if (formPollTimerRef.current != null) {
      window.clearInterval(formPollTimerRef.current);
      formPollTimerRef.current = null;
    }
    formValuesSnapshotRef.current = '';
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

  const commitPreviewValue = useCallback((nextValue: AnswerPayload) => {
    if (samePreviewPayload(previewValueRef.current, nextValue)) {
      return;
    }
    pendingPreviewValueRef.current = null;
    previewValueRef.current = nextValue;
    setPreviewValue(nextValue);
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
      commitPreviewValue(pendingValue);
    }, 0);
  }, [commitPreviewValue]);

  const syncPreviewFromForm = useCallback((form: Form<Record<string, unknown>>) => {
    if (previewUpdateTimerRef.current != null) {
      window.clearTimeout(previewUpdateTimerRef.current);
      previewUpdateTimerRef.current = null;
    }
    commitPreviewValue(formilyValuesToAnswerPayload(form.values, schemaFields));
  }, [commitPreviewValue, schemaFields]);

  const handleFormReady = useCallback((form: Form<Record<string, unknown>>) => {
    if (formPollTimerRef.current != null) {
      window.clearInterval(formPollTimerRef.current);
      formPollTimerRef.current = null;
    }

    const syncIfChanged = () => {
      const snapshot = JSON.stringify(form.values);
      if (snapshot === formValuesSnapshotRef.current) {
        return;
      }
      formValuesSnapshotRef.current = snapshot;
      syncPreviewFromForm(form);
    };

    syncIfChanged();
    formPollTimerRef.current = window.setInterval(syncIfChanged, 50);
  }, [syncPreviewFromForm]);

  useEffect(() => () => {
    if (formPollTimerRef.current != null) {
      window.clearInterval(formPollTimerRef.current);
      formPollTimerRef.current = null;
    }
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
          key={visibleSchemaSignature}
          schemaFields={visibleSchemaFields}
          value={previewValue}
          readOnly={false}
          onChange={handlePreviewChange}
          onFormReady={handleFormReady}
        />
      </div>
    </aside>
  );
}

function samePreviewPayload(left: AnswerPayload, right: AnswerPayload): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}
