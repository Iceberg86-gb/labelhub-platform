# Task State Machine

| State | Can Move To | Guard | Actor |
|------|-------------|-------|-------|
| `draft` | `published` | schema version exists, dataset imported, quota > 0 | Owner |
| `published` | `paused` | task is claimable | Owner |
| `published` | `ended` | deadline reached or Owner closes | Owner/System |
| `paused` | `published` | quota remains and deadline not reached | Owner |
| `paused` | `ended` | Owner closes | Owner |
| `ended` | none | terminal | none |

Every transition writes `task_transitions` and `audit_logs`.
