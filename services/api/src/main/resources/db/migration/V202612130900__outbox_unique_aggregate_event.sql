-- P0-3: the outbox had no uniqueness guard, so a submission's ai_review event was enqueued by
-- several paths (submit, task-level prereview eligible/single) and produced duplicate AI calls and
-- ledger entries. Existing data already contains duplicates (observed: 3 ai_review rows per
-- submission), so a plain UNIQUE index would fail to create. Deduplicate first, then enforce one
-- event per (aggregate_type, aggregate_id, event_type).

-- Keep the most meaningful row per key: prefer a terminal 'processed' row, otherwise the newest by
-- id; delete the rest. Outbox is an operational queue (not an append-only fact table), so pruning
-- redundant duplicates is safe.
DELETE o FROM outbox o
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY aggregate_type, aggregate_id, event_type
               ORDER BY (status = 'processed') DESC, id DESC
           ) AS rn
    FROM outbox
) ranked ON ranked.id = o.id
WHERE ranked.rn > 1;

ALTER TABLE outbox
    ADD UNIQUE KEY uk_outbox_aggregate_event (aggregate_type, aggregate_id, event_type);
