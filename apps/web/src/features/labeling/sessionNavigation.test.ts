import { describe, expect, it } from 'vitest';
import type { Session } from '../../entities/submission/submissionTypes';
import { buildSessionNavigation, nextEditableSessionId } from './sessionNavigation';

function session(id: number, taskId: number, status: Session['status'], ordinal: number): Session {
  return {
    id,
    taskId,
    datasetItemId: 1000 + id,
    labelerId: 7,
    schemaVersionId: 3,
    status,
    workStatus: status === 'claimed' ? 'in_progress' : status,
    claimSnapshot: { datasetItemOrdinal: ordinal },
  };
}

describe('buildSessionNavigation', () => {
  it('orders current task sessions by dataset item ordinal and returns previous/next targets', () => {
    const navigation = buildSessionNavigation({
      currentSessionId: 30,
      currentTaskId: 10,
      sessions: [
        session(50, 10, 'submitted', 3),
        session(10, 10, 'claimed', 1),
        session(90, 20, 'claimed', 1),
        session(30, 10, 'returned_for_revision', 2),
      ],
    });

    expect(navigation.position).toBe(2);
    expect(navigation.total).toBe(3);
    expect(navigation.previousSessionId).toBe(10);
    expect(navigation.nextSessionId).toBe(50);
    expect(navigation.items.map((item) => item.id)).toEqual([10, 30, 50]);
  });

  it('finds the next editable session after submit and skips submitted sessions', () => {
    const navigation = buildSessionNavigation({
      currentSessionId: 10,
      currentTaskId: 10,
      sessions: [
        session(10, 10, 'claimed', 1),
        session(20, 10, 'submitted', 2),
        session(30, 10, 'claimed', 3),
        session(40, 10, 'returned_for_revision', 4),
      ],
    });

    expect(nextEditableSessionId(navigation)).toBe(30);
  });

  it('returns null when no other editable session remains', () => {
    const navigation = buildSessionNavigation({
      currentSessionId: 10,
      currentTaskId: 10,
      sessions: [session(10, 10, 'claimed', 1), session(20, 10, 'submitted', 2)],
    });

    expect(nextEditableSessionId(navigation)).toBeNull();
  });
});
