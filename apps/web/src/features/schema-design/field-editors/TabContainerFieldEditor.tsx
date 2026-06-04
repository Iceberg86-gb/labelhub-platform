import { Button, Input, Typography } from '@douyinfe/semi-ui';
import { IconDelete, IconPlus } from '@douyinfe/semi-icons';
import { useMemo, useState } from 'react';
import { createField, createTab } from '../../../entities/schema/fieldFactory';
import type { SchemaField, SchemaFieldType } from '../../../entities/schema/schemaTypes';
import { AddFieldButton } from '../AddFieldButton';
import { FieldList } from '../FieldList';
import type { FieldEditorProps } from './editorTypes';
import { EditorSection, FieldErrors } from './editorUtils';

export function TabContainerFieldEditor({
  field,
  onChange,
  errors,
  errorsByField,
  selectedStableId,
  onSelect,
  onDelete,
}: FieldEditorProps) {
  const tabs = field.tabs ?? [];
  const [activeTabId, setActiveTabId] = useState(() => tabs[0]?.stableId ?? null);
  const activeTab = useMemo(
    () => tabs.find((tab) => tab.stableId === activeTabId) ?? tabs[0] ?? null,
    [activeTabId, tabs],
  );

  const updateTabs = (nextTabs: typeof tabs) => {
    onChange({ ...field, tabs: nextTabs });
  };

  const handleAddTab = () => {
    const tab = createTab(`Tab ${tabs.length + 1}`);
    updateTabs([...tabs, tab]);
    setActiveTabId(tab.stableId);
  };

  const handleDeleteTab = (stableId: string) => {
    const nextTabs = tabs.filter((tab) => tab.stableId !== stableId);
    updateTabs(nextTabs);
    if (activeTabId === stableId) {
      setActiveTabId(nextTabs[0]?.stableId ?? null);
    }
  };

  const handleTabLabelChange = (stableId: string, label: string) => {
    updateTabs(tabs.map((tab) => (tab.stableId === stableId ? { ...tab, label } : tab)));
  };

  const handleAddChild = (type: SchemaFieldType) => {
    if (!activeTab) return;
    const child = createField(type);
    updateTabs(tabs.map((tab) => (
      tab.stableId === activeTab.stableId ? { ...tab, children: [...(tab.children ?? []), child] } : tab
    )));
    onSelect(child.stableId);
  };

  const handleChildrenChange = (children: SchemaField[]) => {
    if (!activeTab) return;
    updateTabs(tabs.map((tab) => (tab.stableId === activeTab.stableId ? { ...tab, children } : tab)));
  };

  return (
    <div className="field-editor">
      <FieldErrors errors={errors} />
      <EditorSection title="标签页组">
        <label className="field-editor-row">
          <Typography.Text>容器标题</Typography.Text>
          <Input
            value={field.label}
            validateStatus={errors.length ? 'error' : 'default'}
            placeholder="例如：分组标注信息"
            onChange={(label) => onChange({ ...field, label })}
          />
        </label>
        <Typography.Text type="tertiary">标签页组用于组织布局，不写入答案 payload；各 Tab 内字段会按 stableId 单独提交。</Typography.Text>
      </EditorSection>

      <EditorSection title="Tab 设置">
        <div className="tab-editor-list">
          {tabs.map((tab) => (
            <div key={tab.stableId} className="tab-editor-row">
              <Button
                theme={tab.stableId === activeTab?.stableId ? 'solid' : 'borderless'}
                type={tab.stableId === activeTab?.stableId ? 'primary' : undefined}
                onClick={() => setActiveTabId(tab.stableId)}
              >
                {tab.label || '未命名 Tab'}
              </Button>
              <Input value={tab.label} placeholder="Tab 名称" onChange={(label) => handleTabLabelChange(tab.stableId, label)} />
              <Button
                icon={<IconDelete />}
                type="danger"
                theme="borderless"
                disabled={tabs.length <= 1}
                aria-label="删除 Tab"
                onClick={() => handleDeleteTab(tab.stableId)}
              />
            </div>
          ))}
          <Button icon={<IconPlus />} onClick={handleAddTab}>
            添加 Tab
          </Button>
        </div>
      </EditorSection>

      <EditorSection title={activeTab ? `${activeTab.label || '未命名 Tab'} 字段` : 'Tab 字段'}>
        {activeTab ? (
          <div className="nested-children-panel">
            <AddFieldButton label="添加 Tab 字段" excludeTypes={['tab_container']} onPick={handleAddChild} />
            <FieldList
              fields={activeTab.children ?? []}
              onChange={handleChildrenChange}
              selectedStableId={selectedStableId}
              onSelect={onSelect}
              onDelete={onDelete}
              errors={errorsByField}
            />
          </div>
        ) : (
          <Typography.Text type="tertiary">请先添加 Tab。</Typography.Text>
        )}
      </EditorSection>
    </div>
  );
}
