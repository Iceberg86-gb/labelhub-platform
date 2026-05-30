import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { SchemaField } from '../../../../entities/schema/schemaTypes';

type SchemaTab = NonNullable<SchemaField['tabs']>[number];

const ActiveTabContext = createContext<string | null>(null);

export function LabelHubTabsContainer({ field, children }: { field?: SchemaField; children?: ReactNode }) {
  const tabs = useMemo(() => field?.tabs ?? [], [field?.tabs]);
  const [activeTabId, setActiveTabId] = useState(() => tabs[0]?.stableId ?? null);
  const currentTabId = tabs.some((tab) => tab.stableId === activeTabId) ? activeTabId : tabs[0]?.stableId ?? null;

  if (!tabs.length) {
    return null;
  }

  return (
    <section className="labelhub-tabs-container" data-labeling-field-id={field?.stableId}>
      <div className="labelhub-tabs-container__header">
        <strong>{field?.label || '多 Tab'}</strong>
        {field?.help ? <span>{field.help}</span> : null}
      </div>
      <div className="labelhub-tabs-container__tabs" role="tablist" aria-label={field?.label || '多 Tab'}>
        {tabs.map((tab) => (
          <button
            key={tab.stableId}
            type="button"
            role="tab"
            aria-selected={tab.stableId === currentTabId}
            className={tab.stableId === currentTabId ? 'labelhub-tabs-container__tab--active' : undefined}
            onClick={() => setActiveTabId(tab.stableId)}
          >
            {tab.label || '未命名 Tab'}
          </button>
        ))}
      </div>
      <ActiveTabContext.Provider value={currentTabId}>{children}</ActiveTabContext.Provider>
    </section>
  );
}

export function LabelHubTabPane({ tab, children }: { tab?: SchemaTab; children?: ReactNode }) {
  const activeTabId = useContext(ActiveTabContext);
  if (!tab || activeTabId !== tab.stableId) {
    return null;
  }

  return (
    <div className="labelhub-tabs-container__pane" role="tabpanel">
      {children}
    </div>
  );
}
