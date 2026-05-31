import type { Session } from '../../entities/submission/submissionTypes';

const NAVIGABLE_STATUSES: ReadonlySet<Session['status']> = new Set(['claimed', 'returned_for_revision', 'submitted']);

export type SessionNavigationItem = Pick<Session, 'id' | 'status' | 'datasetItemId'> & {
  ordinal: number;
};

export type SessionNavigation = {
  items: SessionNavigationItem[];
  position: number;
  total: number;
  previousSessionId: number | null;
  nextSessionId: number | null;
};

export function buildSessionNavigation({
  currentSessionId,
  currentTaskId,
  sessions,
}: {
  currentSessionId: number;
  currentTaskId: number;
  sessions: Session[];
}): SessionNavigation {
  const items = sessions
    .filter((session) => session.taskId === currentTaskId && NAVIGABLE_STATUSES.has(session.status))
    .map((session) => ({
      id: session.id,
      status: session.status,
      datasetItemId: session.datasetItemId,
      ordinal: datasetItemOrdinal(session),
    }))
    .sort((left, right) => left.ordinal - right.ordinal || left.id - right.id);

  const index = items.findIndex((session) => session.id === currentSessionId);
  const previousSessionId = index > 0 ? items[index - 1].id : null;
  const nextSessionId = index >= 0 && index < items.length - 1 ? items[index + 1].id : null;

  return {
    items,
    position: index >= 0 ? index + 1 : 0,
    total: items.length,
    previousSessionId,
    nextSessionId,
  };
}

function datasetItemOrdinal(session: Session) {
  const ordinal = session.claimSnapshot?.datasetItemOrdinal;
  return typeof ordinal === 'number' && Number.isFinite(ordinal) ? ordinal : session.datasetItemId;
}
