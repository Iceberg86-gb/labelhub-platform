# Export Job State Machine

| State | Can Move To | Guard | Actor |
|------|-------------|-------|-------|
| `created` | `queued` | export parameters validated | Owner |
| `queued` | `running` | worker acquired outbox event | Export Worker |
| `running` | `completed` | file written and snapshot hash stored | Export Worker |
| `running` | `failed` | retry limit exceeded | Export Worker |
| `created` | `revoked` | user cancels before worker starts | Owner |
| `queued` | `revoked` | user cancels before worker starts | Owner |

Completed jobs create immutable `export_snapshots`.
