import { Button, Typography } from '@douyinfe/semi-ui';
import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties } from 'react';
import type {
  LinkageAtomicCondition,
  LinkageCondition,
  LinkageConditionGroup,
  LinkageConditionOp,
  SchemaField,
} from '../../../entities/schema/schemaTypes';
import { EditorSection } from './editorUtils';

type LinkageKey = 'visibleWhen' | 'requiredWhen';

type LinkageConditionBuilderProps = {
  field: SchemaField;
  availableFields: SchemaField[];
  onChange: (field: SchemaField) => void;
};

const LINKAGE_KEYS: Array<{ label: string; value: LinkageKey }> = [
  { label: '显示条件 visibleWhen', value: 'visibleWhen' },
  { label: '必填条件 requiredWhen', value: 'requiredWhen' },
];

const LINKAGE_OPS: LinkageConditionOp[] = ['eq', 'neq', 'in', 'notIn', 'gt', 'gte', 'lt', 'lte', 'empty', 'notEmpty'];
const ARRAY_OPS = new Set<LinkageConditionOp>(['in', 'notIn']);
const EMPTY_OPS = new Set<LinkageConditionOp>(['empty', 'notEmpty']);
const NUMERIC_OPS = new Set<LinkageConditionOp>(['gt', 'gte', 'lt', 'lte']);
const controlStyle: CSSProperties = {
  width: '100%',
  minHeight: 32,
  border: '1px solid var(--semi-color-border)',
  borderRadius: 6,
  padding: '0 10px',
  background: 'var(--semi-color-bg-0)',
};
const actionsStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  justifyContent: 'flex-end',
  marginTop: 4,
};
const advancedStyle: CSSProperties = {
  display: 'grid',
  gap: 10,
};

export function LinkageConditionBuilder({ field, availableFields, onChange }: LinkageConditionBuilderProps) {
  const [linkageKey, setLinkageKey] = useState<LinkageKey>('visibleWhen');
  const condition = field[linkageKey];
  const isAdvancedGroup = isLinkageConditionGroup(condition);
  const atomicCondition = isLinkageAtomicCondition(condition) ? condition : undefined;
  const [draftField, setDraftField] = useState(atomicCondition?.field ?? '');
  const [draftOp, setDraftOp] = useState<LinkageConditionOp>(atomicCondition?.op ?? 'eq');
  const [draftValue, setDraftValue] = useState(formatDraftValue(atomicCondition));
  const candidates = useMemo(
    () => flattenLinkageCandidateFields(availableFields, field.stableId),
    [availableFields, field.stableId],
  );
  const filteredCandidates = useMemo(
    () => (NUMERIC_OPS.has(draftOp) ? candidates.filter((candidate) => candidate.type === 'number') : candidates),
    [candidates, draftOp],
  );

  useEffect(() => {
    const nextCondition = field[linkageKey];
    const nextAtomic = isLinkageAtomicCondition(nextCondition) ? nextCondition : undefined;
    setDraftField(nextAtomic?.field ?? '');
    setDraftOp(nextAtomic?.op ?? 'eq');
    setDraftValue(formatDraftValue(nextAtomic));
  }, [field, linkageKey]);

  const handleKeyChange = (nextKey: LinkageKey) => {
    setLinkageKey(nextKey);
  };

  const handleOpChange = (nextOp: LinkageConditionOp) => {
    setDraftOp(nextOp);
    setDraftValue('');
    const currentFieldIsNumber = candidates.some((candidate) => candidate.stableId === draftField && candidate.type === 'number');
    if (NUMERIC_OPS.has(nextOp) && draftField && !currentFieldIsNumber) {
      const firstNumberField = candidates.find((candidate) => candidate.type === 'number');
      setDraftField(firstNumberField?.stableId ?? '');
    }
  };

  const canApply = Boolean(draftField)
    && (EMPTY_OPS.has(draftOp)
      || (NUMERIC_OPS.has(draftOp) ? Number.isFinite(Number(draftValue)) && draftValue.trim() !== '' : draftValue.trim() !== ''));

  const handleApply = () => {
    if (!canApply) return;
    onChange({
      ...field,
      [linkageKey]: buildAtomicLinkageCondition(draftField, draftOp, draftValue),
    });
  };

  const handleClear = () => {
    onChange({
      ...field,
      [linkageKey]: undefined,
    });
  };

  return (
    <EditorSection title="可视化联动构造器">
      <label className="field-editor-row">
        <Typography.Text>联动目标</Typography.Text>
        <select
          aria-label="联动目标"
          className="field-linkage-builder-control"
          style={controlStyle}
          value={linkageKey}
          onChange={(event) => handleKeyChange(event.currentTarget.value as LinkageKey)}
        >
          {LINKAGE_KEYS.map((item) => (
            <option key={item.value} value={item.value}>{item.label}</option>
          ))}
        </select>
      </label>

      {isAdvancedGroup ? (
        <div className="field-linkage-builder-advanced" style={advancedStyle}>
          <Typography.Text type="tertiary">此条件为高级分组,请使用下方 JSON 编辑</Typography.Text>
          <DisabledAtomicForm linkageKey={linkageKey} />
        </div>
      ) : (
        <div className="field-linkage-builder">
          <label className="field-editor-row">
            <Typography.Text>当字段</Typography.Text>
            <select
              aria-label={`${linkageKey} 条件字段`}
              className="field-linkage-builder-control"
              style={controlStyle}
              value={draftField}
              onChange={(event) => setDraftField(event.currentTarget.value)}
            >
              <option value="">请选择字段</option>
              {filteredCandidates.map((candidate) => (
                <option key={candidate.stableId} value={candidate.stableId}>
                  {candidate.label || candidate.stableId}
                </option>
              ))}
            </select>
          </label>
          <label className="field-editor-row">
            <Typography.Text>操作符</Typography.Text>
            <select
              aria-label={`${linkageKey} 操作符`}
              className="field-linkage-builder-control"
              style={controlStyle}
              value={draftOp}
              onChange={(event) => handleOpChange(event.currentTarget.value as LinkageConditionOp)}
            >
              {LINKAGE_OPS.map((op) => (
                <option key={op} value={op}>{op}</option>
              ))}
            </select>
          </label>
          {EMPTY_OPS.has(draftOp) ? null : (
            <label className="field-editor-row">
              <Typography.Text>{ARRAY_OPS.has(draftOp) ? '值(逗号分隔)' : '值'}</Typography.Text>
              <input
                aria-label={`${linkageKey} 条件值`}
                className="field-linkage-builder-control"
                style={controlStyle}
                inputMode={NUMERIC_OPS.has(draftOp) ? 'decimal' : undefined}
                value={draftValue}
                onChange={(event) => setDraftValue(event.currentTarget.value)}
              />
            </label>
          )}
          <div className="field-linkage-builder-actions" style={actionsStyle}>
            <Button disabled={!canApply} onClick={handleApply}>应用条件</Button>
            <Button theme="borderless" onClick={handleClear}>清空条件</Button>
          </div>
        </div>
      )}
    </EditorSection>
  );
}

function DisabledAtomicForm({ linkageKey }: { linkageKey: LinkageKey }) {
  return (
    <div className="field-linkage-builder">
      <label className="field-editor-row">
        <Typography.Text>当字段</Typography.Text>
        <select aria-label={`${linkageKey} 条件字段`} className="field-linkage-builder-control" style={controlStyle} disabled />
      </label>
      <label className="field-editor-row">
        <Typography.Text>操作符</Typography.Text>
        <select aria-label={`${linkageKey} 操作符`} className="field-linkage-builder-control" style={controlStyle} disabled />
      </label>
    </div>
  );
}

export function buildAtomicLinkageCondition(
  field: string,
  op: LinkageConditionOp,
  rawValue?: string,
): LinkageAtomicCondition {
  if (EMPTY_OPS.has(op)) {
    return { field, op };
  }
  if (ARRAY_OPS.has(op)) {
    return {
      field,
      op,
      value: (rawValue ?? '').split(',').map((item) => item.trim()).filter(Boolean),
    };
  }
  if (NUMERIC_OPS.has(op)) {
    return { field, op, value: Number(rawValue) };
  }
  return { field, op, value: rawValue ?? '' };
}

export function flattenLinkageCandidateFields(fields: SchemaField[], currentStableId: string): SchemaField[] {
  const flattened: SchemaField[] = [];
  const visit = (items: SchemaField[] | undefined) => {
    (items ?? []).forEach((item) => {
      if (item.stableId !== currentStableId) {
        flattened.push(item);
      }
      if (item.type === 'nested_object') {
        visit(item.children);
      }
      if (item.type === 'tab_container') {
        item.tabs?.forEach((tab) => visit(tab.children));
      }
    });
  };

  visit(fields);
  return flattened;
}

export function isLinkageConditionGroup(condition: LinkageCondition | undefined): condition is LinkageConditionGroup {
  return Boolean(condition && ('allOf' in condition || 'anyOf' in condition));
}

function isLinkageAtomicCondition(condition: LinkageCondition | undefined): condition is LinkageAtomicCondition {
  return Boolean(condition && !isLinkageConditionGroup(condition) && ('field' in condition || 'op' in condition));
}

function formatDraftValue(condition: LinkageAtomicCondition | undefined): string {
  if (!condition || condition.value == null) return '';
  return Array.isArray(condition.value) ? condition.value.join(', ') : String(condition.value);
}
