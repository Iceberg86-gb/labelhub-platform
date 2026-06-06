# LabelHub Deployment

## 取证结论

- 当前生产目标是单台阿里云 ECS，部署根目录 `/opt/labelhub`，由 `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build` 启动。
- `scripts/deploy-web.sh` 在开发机执行 Web build，然后用 `rsync` 同步 `apps/web/dist/` 到 `/opt/labelhub/infra/web-dist/`，并同步源码到 `/opt/labelhub/`。
- 公开访问路径仍是 `http://120.26.182.61:8443/`，`nginx` 只做 `8443:80` 映射；ICP备案与 HTTPS 证书切换尚未完成。
- 生产 compose 实际包含 `mysql`、`redis`、`minio`、`minio-init`、`api`、`agent`、`nginx`；公网只暴露 `nginx`，其他端口在 compose 网络内使用。
- Redis 补查使用 `grep -rn -i "redis" services/ apps/ packages/ infra/ --include="*.java" --include="*.ts" --include="*.tsx" --include="*.yml" --include="*.yaml" --include="*.conf"`；命中仅限 `infra/docker-compose.yml` 与 `infra/docker-compose.prod.yml` 的服务定义/healthcheck，未发现业务消费者，属于候选裁剪项。

```mermaid
flowchart LR
    dev["开发机(Developer Machine)<br/>scripts/deploy-web.sh<br/>pnpm --filter @labelhub/web build"]
    dist_sync["Web 产物同步(Web Dist Sync)<br/>rsync --delete<br/>apps/web/dist/ -> root@120.26.182.61:/opt/labelhub/infra/web-dist/"]
    source_sync["源码同步(Source Tree Sync)<br/>repo -> root@120.26.182.61:/opt/labelhub/<br/>排除(excludes) node_modules, .git, dist, .env.prod, web-dist, screenshots, design-assets"]
    browser["浏览器(Browser)<br/>HTTP + IP 直连(HTTP + IP direct)<br/>http://120.26.182.61:8443/"]

    dev -->|"构建后同步(sync after build)"| dist_sync
    dev -->|"同步源码(sync source)"| source_sync

    subgraph ecs["阿里云 ECS(Alibaba Cloud ECS) / Ubuntu 24.04<br/>/opt/labelhub/infra<br/>docker compose prod<br/>备案/HTTPS 未完成(ICP/HTTPS pending)"]
        nginx["公开入口(Public Entry)<br/>nginx<br/>image nginx:1.27-alpine<br/>ports: 8443:80<br/>服务 ./web-dist(serves ./web-dist)<br/>/api/ proxy"]
        api["业务 API(Business API)<br/>api<br/>build services/api/Dockerfile<br/>container port 8080<br/>context path /api<br/>health /api/actuator/health"]
        agent["异步工作容器(Async Worker Container)<br/>agent<br/>build services/agent/Dockerfile<br/>container port 8081<br/>health /actuator/health<br/>AI review + export outbox workers"]
        mysql["业务数据库(Business Database)<br/>mysql<br/>image mysql:8.0<br/>container port 3306<br/>volume labelhub-mysql-data"]
        redis["Redis 缓存服务(Redis Cache Service)<br/>redis<br/>image redis:7-alpine<br/>container port 6379<br/>已部署, 当前无业务引用(deployed, no business consumer)<br/>healthcheck redis-cli ping"]
        minio["对象存储(Object Storage)<br/>minio<br/>image minio/minio:latest<br/>container ports 9000 API / 9001 console<br/>volume labelhub-minio-data"]
        minio_init["对象存储初始化(Object Storage Init)<br/>minio-init<br/>image minio/mc:latest<br/>创建 OBJECT_STORAGE_BUCKET(creates OBJECT_STORAGE_BUCKET) via http://minio:9000"]
    end

    dist_sync -->|"提供静态资源(provide static assets)"| nginx
    source_sync -->|"提供构建上下文(provide build context)"| ecs
    browser -->|"GET / 通过 HTTP(over HTTP)"| nginx
    browser -->|"GET/POST /api/* 通过 HTTP(over HTTP)"| nginx

    nginx -->|"SPA 回退(SPA fallback) try_files -> index.html"| nginx
    nginx -->|"反向代理(reverse proxy) proxy_pass http://api:8080"| api

    api -->|"启动依赖(start dependency) depends_on mysql: service_healthy"| mysql
    api -->|"启动依赖(start dependency) depends_on minio-init: service_started"| minio_init
    api -->|"数据库连接(database connection) DATABASE_URL jdbc:mysql://mysql:3306/labelhub"| mysql
    api -->|"对象存储连接(object storage connection) OBJECT_STORAGE_ENDPOINT http://minio:9000"| minio

    minio_init -->|"启动依赖(start dependency) depends_on minio: service_started"| minio
    agent -->|"启动依赖(start dependency) depends_on api: service_healthy"| api
    agent -->|"内部 API 地址(internal API base URL) LABELHUB_API_BASE_URL http://api:8080/api"| api
    agent -->|"数据库连接(database connection) SPRING_DATASOURCE_URL from .env.prod"| mysql
    nginx -->|"启动依赖(start dependency) depends_on api: service_healthy"| api
```

## 实证来源

- 生产 compose 容器、端口、依赖和环境变量：`infra/docker-compose.prod.yml`。
- Nginx 静态资源根目录与 `/api/` 反向代理：`infra/nginx/labelhub.conf`。
- 单 ECS、8443 公网入口、HTTP IP 访问与 TLS cutover 说明：`infra/deploy/README.md`。
- Web build 与 rsync 部署路径：`scripts/deploy-web.sh`、`docs/dev-environment.md`。
- API context path、默认端口、对象存储和数据库配置：`services/api/src/main/resources/application.yml`。
- Agent 端口、API base URL、outbox worker 配置和 LLM env 配置：`services/agent/src/main/resources/application.yml`。
- Agent 同一进程内包含 AI review 与 export outbox worker：`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java`、`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxExportWorker.java`。
- Redis 补查命中清单：`infra/docker-compose.yml`、`infra/docker-compose.prod.yml`；未命中 `services/`、`apps/`、`packages/` 业务代码。
