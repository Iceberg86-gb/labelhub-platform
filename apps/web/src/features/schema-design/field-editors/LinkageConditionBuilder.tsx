import { Button, Typography } from '@douyinfe/semi-ui';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { StatusBadge } from '../../../shared/ui';
import type { CSSProperties } from 'react';
import type {
  LinkageAtomicCondition,
  LinkageCondition,
  LinkageConditionGroup,
  LinkageConditionOp,
  SchemaField,
} from '../../../entities/schema/schemaTypes';
import type { LinkageDirtyState } from './editorTypes';
import { EditorSection } from './editorUtils';

type LinkageKey = 'visibleWhen' | 'requiredWhen';

type LinkageConditionBuilderProps = {
  field: SchemaField;
  availableFields: SchemaField[];
  onChange: (field: SchemaField) => void;
  onDirtyStateChange?: (state: LinkageDirtyState | null) => void;
};

type LinkageMode = 'atomic' | 'group';
type LinkageGroupOperator = 'allOf' | 'anyOf';
type AtomicDraft = {
  field: string;
  op: LinkageConditionOp;
  value: string;
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
  alignItems: 'center',
  gap: 8,
  justifyContent: 'flex-end',
  marginTop: 4,
};
const groupStyle: CSSProperties = {
  display: 'grid',
  gap: 10,
};
const groupRowStyle: CSSProperties = {
  display: 'grid',
  gap: 8,
  gridTemplateColumns: 'minmax(0, 1fr) minmax(92px, 0.7fr) minmax(0, 1fr) auto',
  alignItems: 'end',
};

export function LinkageConditionBuilder({ field, availableFields, onChange, onDirtyStateChange }: LinkageConditionBuilderProps) {
  const [linkageKey, setLinkageKey] = useState<LinkageKey>('visibleWhen');
  const condition = field[linkageKey];
  const atomicCondition = isLinkageAtomicCondition(condition) ? condition : undefined;
  const groupCondition = isLinkageConditionGroup(condition) ? condition : undefined;
  const [mode, setMode] = useState<LinkageMode>(() => conditionToMode(condition));
  const [groupOperator, setGroupOperator] = useState<LinkageGroupOperator>(() => groupOperatorFromCondition(groupCondition));
  const [groupDrafts, setGroupDrafts] = useState<AtomicDraft[]>(() => groupDraftsFromCondition(groupCondition));
  const [draftField, setDraftField] = useState(atomicCondition?.field ?? '');
  const [draftOp, setDraftOp] = useState<LinkageConditionOp>(atomicCondition?.op ?? 'eq');
  const [draftValue, setDraftValue] = useState(formatDraftValue(atomicCondition));
  const appliedDraftSignature = useMemo(() => draftSignatureFromCondition(condition), [condition]);
  const candidates = useMemo(
    () => flattenLinkageCandidateFields(availableFields, field.stableId),
    [availableFields, field.stableId],
  );
  const filteredCandidates = useMemo(
    () => (NUMERIC_OPS.has(draftOp) ? candidates.filter((candidate) => candidate.type === 'number') : candidates),
    [candidates, draftOp],
  );

  useEffect(() => {
    resetDraftFromCondition(condition);
  }, [condition, field.stableId, linkageKey]);

  const draftSignature = useMemo(
    () => JSON.stringify({
      mode,
      groupOperator: mode === 'group' ? groupOperator : undefined,
      groupDrafts: mode === 'group' ? groupDrafts : undefined,
      draftField: mode === 'atomic' ? draftField : undefined,
      draftOp: mode === 'atomic' ? draftOp : undefined,
      draftValue: mode === 'atomic' ? draftValue : undefined,
    }),
    [draftField, draftOp, draftValue, groupDrafts, groupOperator, mode],
  );

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

  const handleGroupOpChange = (index: number, nextOp: LinkageConditionOp) => {
    setGroupDrafts((drafts) => drafts.map((draft, draftIndex) => {
      if (draftIndex !== index) return draft;
      const currentFieldIsNumber = candidates.some((candidate) => candidate.stableId === draft.field && candidate.type === 'number');
      const nextField = NUMERIC_OPS.has(nextOp) && draft.field && !currentFieldIsNumber
        ? candidates.find((candidate) => candidate.type === 'number')?.stableId ?? ''
        : draft.field;
      return { ...draft, field: nextField, op: nextOp, value: '' };
    }));
  };

  const canApply = Boolean(draftField)
    && (EMPTY_OPS.has(draftOp)
      || (NUMERIC_OPS.has(draftOp) ? Number.isFinite(Number(draftValue)) && draftValue.trim() !== '' : draftValue.trim() !== ''));
  const canApplyGroup = groupDrafts.length > 0 && groupDrafts.every(canBuildAtomicDraft);
  const isDirty = draftSignature !== appliedDraftSignature;
  const canApplyCurrentDraft = mode === 'atomic' ? canApply : canApplyGroup;

  const applyCurrentDraft = useCallback(() => {
    if (mode === 'atomic') {
      if (!canApply) return;
      onChange({
        ...field,
        [linkageKey]: buildAtomicLinkageCondition(draftField, draftOp, draftValue),
      });
      return;
    }

    if (!canApplyGroup) return;
    onChange({
      ...field,
      [linkageKey]: buildGroupLinkageCondition(groupOperator, groupDrafts),
    });
  }, [canApply, canApplyGroup, draftField, draftOp, draftValue, field, groupDrafts, groupOperator, linkageKey, mode, onChange]);

  const discardDraft = useCallback(() => {
    resetDraftFromCondition(condition);
  }, [condition]);

  useEffect(() => {
    if (!onDirtyStateChange) return;
    if (!isDirty) {
      onDirtyStateChange(null);
      return;
    }

    onDirtyStateChange({
      dirty: true,
      canApply: canApplyCurrentDraft,
      apply: applyCurrentDraft,
      discard: discardDraft,
    });
  }, [applyCurrentDraft, canApplyCurrentDraft, discardDraft, isDirty, onDirtyStateChange]);

  useEffect(() => () => onDirtyStateChange?.(null), [onDirtyStateChange]);

  const handleClear = () => {
    onChange({
      ...field,
      [linkageKey]: undefined,
    });
  };

  const promoteAtomicDraftToGroup = (withNewRow: boolean) => {
    setMode('group');
    setGroupOperator('allOf');
    setGroupDrafts([
      atomicDraftFromParts(draftField, draftOp, draftValue),
      ...(withNewRow ? [createEmptyAtomicDraft()] : []),
    ]);
  };

  const handleModeChange = (nextMode: LinkageMode) => {
    if (nextMode === mode) return;
    if (nextMode === 'group') {
      promoteAtomicDraftToGroup(false);
      return;
    }

    const firstDraft = groupDrafts[0] ?? createEmptyAtomicDraft();
    if (groupDrafts.length > 1) {
      const message = groupOperator === 'anyOf'
        ? '将从"任一满足"条件组切回单条件,并只保留第一条条件;其他可触发显示/必填的条件会被移除。'
        : '将从"全部满足"条件组切回单条件,并只保留第一条条件。';
      if (!window.confirm(message)) {
        return;
      }
    }
    setMode('atomic');
    setDraftField(firstDraft.field);
    setDraftOp(firstDraft.op);
    setDraftValue(firstDraft.value);
    if (canBuildAtomicDraft(firstDraft)) {
      onChange({
        ...field,
        [linkageKey]: buildAtomicLinkageCondition(firstDraft.field, firstDraft.op, firstDraft.value),
      });
    }
  };

  const handleAddGroupRow = () => {
    if (mode === 'atomic') {
      promoteAtomicDraftToGroup(true);
      return;
    }
    setGroupDrafts((drafts) => [...drafts, createEmptyAtomicDraft()]);
  };

  const handleRemoveGroupRow = (index: number) => {
    setGroupDrafts((drafts) => (drafts.length <= 1 ? drafts : drafts.filter((_, draftIndex) => draftIndex !== index)));
  };

  const updateGroupDraft = (index: number, patch: Partial<AtomicDraft>) => {
    setGroupDrafts((drafts) => drafts.map((draft, draftIndex) => (draftIndex === index ? { ...draft, ...patch } : draft)));
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

      <label className="field-editor-row">
        <Typography.Text>联动模式</Typography.Text>
        <select
          aria-label="联动模式"
          className="field-linkage-builder-control"
          style={controlStyle}
          value={mode}
          onChange={(event) => handleModeChange(event.currentTarget.value as LinkageMode)}
        >
          <option value="atomic">单条件</option>
          <option value="group">条件组</option>
        </select>
      </label>

      {mode === 'atomic' ? (
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
            {isDirty ? <StatusBadge tone="warning">有未应用的修改</StatusBadge> : null}
            <Button disabled={!canApply} onClick={applyCurrentDraft}>应用条件</Button>
            <Button theme="borderless" onClick={() => handleAddGroupRow()}>添加条件</Button>
            <Button theme="borderless" onClick={handleClear}>清空条件</Button>
          </div>
        </div>
      ) : (
        <div className="field-linkage-builder-group" style={groupStyle}>
          <label className="field-editor-row">
            <Typography.Text>条件组逻辑</Typography.Text>
            <select
              aria-label="条件组逻辑"
              className="field-linkage-builder-control"
              style={controlStyle}
              value={groupOperator}
              onChange={(event) => setGroupOperator(event.currentTarget.value as LinkageGroupOperator)}
            >
              <option value="allOf">全部满足 AND/allOf</option>
              <option value="anyOf">任一满足 OR/anyOf</option>
            </select>
          </label>
          {groupDrafts.map((draft, index) => (
            <div key={index} className="field-linkage-builder-group-row" style={groupRowStyle}>
              <label className="field-editor-row">
                <Typography.Text>当字段</Typography.Text>
                <select
                  aria-label={`${linkageKey} 第${index + 1}条条件字段`}
                  className="field-linkage-builder-control"
                  style={controlStyle}
                  value={draft.field}
                  onChange={(event) => updateGroupDraft(index, { field: event.currentTarget.value })}
                >
                  <option value="">请选择字段</option>
                  {(NUMERIC_OPS.has(draft.op) ? candidates.filter((candidate) => candidate.type === 'number') : candidates).map((candidate) => (
                    <option key={candidate.stableId} value={candidate.stableId}>
                      {candidate.label || candidate.stableId}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field-editor-row">
                <Typography.Text>操作符</Typography.Text>
                <select
                  aria-label={`${linkageKey} 第${index + 1}条操作符`}
                  className="field-linkage-builder-control"
                  style={controlStyle}
                  value={draft.op}
                  onChange={(event) => handleGroupOpChange(index, event.currentTarget.value as LinkageConditionOp)}
                >
                  {LINKAGE_OPS.map((op) => (
                    <option key={op} value={op}>{op}</option>
                  ))}
                </select>
              </label>
              {EMPTY_OPS.has(draft.op) ? (
                <span />
              ) : (
                <label className="field-editor-row">
                  <Typography.Text>{ARRAY_OPS.has(draft.op) ? '值(逗号分隔)' : '值'}</Typography.Text>
                  <input
                    aria-label={`${linkageKey} 第${index + 1}条条件值`}
                    className="field-linkage-builder-control"
                    style={controlStyle}
                    inputMode={NUMERIC_OPS.has(draft.op) ? 'decimal' : undefined}
                    value={draft.value}
                    onChange={(event) => updateGroupDraft(index, { value: event.currentTarget.value })}
                  />
                </label>
              )}
              <Button disabled={groupDrafts.length <= 1} theme="borderless" onClick={() => handleRemoveGroupRow(index)}>删除</Button>
            </div>
          ))}
          <div className="field-linkage-builder-actions" style={actionsStyle}>
            <Button theme="borderless" onClick={handleAddGroupRow}>添加条件</Button>
            {isDirty ? <StatusBadge tone="warning">有未应用的修改</StatusBadge> : null}
            <Button disabled={!canApplyGroup} onClick={applyCurrentDraft}>应用条件</Button>
            <Button theme="borderless" onClick={handleClear}>清空条件</Button>
          </div>
        </div>
      )}
    </EditorSection>
  );

  function resetDraftFromCondition(nextCondition: LinkageCondition | undefined) {
    const nextAtomic = isLinkageAtomicCondition(nextCondition) ? nextCondition : undefined;
    const nextGroup = isLinkageConditionGroup(nextCondition) ? nextCondition : undefined;
    setMode(conditionToMode(nextCondition));
    setGroupOperator(groupOperatorFromCondition(nextGroup));
    setGroupDrafts(groupDraftsFromCondition(nextGroup));
    setDraftField(nextAtomic?.field ?? '');
    setDraftOp(nextAtomic?.op ?? 'eq');
    setDraftValue(formatDraftValue(nextAtomic));
  }
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

export function buildGroupLinkageCondition(
  operator: LinkageGroupOperator,
  drafts: AtomicDraft[],
): LinkageConditionGroup {
  return {
    [operator]: drafts.filter(canBuildAtomicDraft).map((draft) => buildAtomicLinkageCondition(draft.field, draft.op, draft.value)),
  } as LinkageConditionGroup;
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

function conditionToMode(condition: LinkageCondition | undefined): LinkageMode {
  return isLinkageConditionGroup(condition) ? 'group' : 'atomic';
}

function groupOperatorFromCondition(condition: LinkageConditionGroup | undefined): LinkageGroupOperator {
  if (condition?.anyOf?.length && !condition.allOf?.length) return 'anyOf';
  return 'allOf';
}

function groupDraftsFromCondition(condition: LinkageConditionGroup | undefined): AtomicDraft[] {
  const atoms = condition?.allOf?.length ? condition.allOf : condition?.anyOf ?? [];
  return atoms.length > 0 ? atoms.map(atomicDraftFromCondition) : [createEmptyAtomicDraft()];
}

function atomicDraftFromCondition(condition: LinkageAtomicCondition): AtomicDraft {
  return atomicDraftFromParts(condition.field ?? '', condition.op ?? 'eq', formatDraftValue(condition));
}

function atomicDraftFromParts(field: string, op: LinkageConditionOp, value: string): AtomicDraft {
  return { field, op, value };
}

function createEmptyAtomicDraft(): AtomicDraft {
  return { field: '', op: 'eq', value: '' };
}

function canBuildAtomicDraft(draft: AtomicDraft): boolean {
  return Boolean(draft.field)
    && (EMPTY_OPS.has(draft.op)
      || (NUMERIC_OPS.has(draft.op) ? Number.isFinite(Number(draft.value)) && draft.value.trim() !== '' : draft.value.trim() !== ''));
}

function formatDraftValue(condition: LinkageAtomicCondition | undefined): string {
  if (!condition || condition.value == null) return '';
  return Array.isArray(condition.value) ? condition.value.join(', ') : String(condition.value);
}

function draftSignatureFromCondition(condition: LinkageCondition | undefined): string {
  const groupCondition = isLinkageConditionGroup(condition) ? condition : undefined;
  const atomicCondition = isLinkageAtomicCondition(condition) ? condition : undefined;
  const mode = conditionToMode(condition);
  return JSON.stringify({
    mode,
    groupOperator: mode === 'group' ? groupOperatorFromCondition(groupCondition) : undefined,
    groupDrafts: mode === 'group' ? groupDraftsFromCondition(groupCondition) : undefined,
    draftField: mode === 'atomic' ? atomicCondition?.field ?? '' : undefined,
    draftOp: mode === 'atomic' ? atomicCondition?.op ?? 'eq' : undefined,
    draftValue: mode === 'atomic' ? formatDraftValue(atomicCondition) : undefined,
  });
}
