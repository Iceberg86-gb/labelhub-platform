# ADR-016 移除未使用的 Redis

## Status

Accepted (2026-11)。本 ADR 覆盖设计基线中把 Redis 列为数据层组件的描述（§数据层、§部署），以及 conventions §6 中 Redis 用途的条款。

## Context

设计基线把 Redis 定位为"可用于轻量队列或锁"的预留组件，本地和生产的 compose 文件也一直在运行它（生产环境限额 192m）。但实际情况是：

- `services/`、`apps/`、`packages/` 里没有任何代码连接 Redis，也没有任何服务依赖它（compose 中没有 `depends_on`）。
- 主队列从一开始就是 MySQL outbox（ADR-008），并发控制由 MySQL 乐观锁负责（ADR-007）。
- 一个从来没被用过的组件，会持续带来成本：在 2C4G 单机上占用内存，多一个镜像需要升级、需要做安全补丁，文档还在描述一个并不存在的"缓存层"，误导读者。

## Decision

把 Redis 从 `infra/docker-compose.yml`、`infra/docker-compose.prod.yml`、`.env.example` 和相关文档里移除。

将来如果确实需要短期状态缓存或分布式锁，必须先写新的 ADR，说明需求和替代方案。即使重新引入，也继续遵守原来的禁令：不能用作权威队列或主存储，也不能缓存 append-only 事实。

## Consequences

- 生产环境节省 192m 内存，少维护一个镜像，架构文档和真实运行的系统保持一致。
- **生产环境需要手动清理一次。** 部署脚本只重建 `api`、`agent` 和 `nginx`，所以已经在跑的 redis 容器不会自动停掉。需要在 `/opt/labelhub/infra` 下执行一次：`docker compose --env-file .env.prod -f docker-compose.prod.yml rm -sf redis`。
- 验证方法：CI 的 compose-smoke 任务会解析生产 compose 文件；后端测试（包括 Testcontainers 集成测试）从来不依赖 Redis，删除后全部照常通过。
