# LabelHub Deployment

## 取证结论

- 当前生产目标是单台阿里云 ECS，部署根目录 `/opt/labelhub`，由 `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build` 启动。
- `scripts/deploy-web.sh` 在开发机执行 Web build，然后用 `rsync` 同步 `apps/web/dist/` 到 `/opt/labelhub/infra/web-dist/`，并同步源码到 `/opt/labelhub/`。
- 公开访问路径仍是 `http://120.26.182.61:8443/`，`nginx` 只做 `8443:80` 映射；ICP备案与 HTTPS 证书切换尚未完成。
- 生产 compose 实际包含 `mysql`、`redis`、`minio`、`minio-init`、`api`、`agent`、`nginx`；公网只暴露 `nginx`，其他端口在 compose 网络内使用。

```mermaid
flowchart LR
    dev["开发机<br/>scripts/deploy-web.sh<br/>pnpm --filter @labelhub/web build"]
    dist_sync["rsync --delete<br/>apps/web/dist/ -> root@120.26.182.61:/opt/labelhub/infra/web-dist/"]
    source_sync["rsync source tree<br/>repo -> root@120.26.182.61:/opt/labelhub/<br/>excludes node_modules, .git, dist, .env.prod, web-dist, screenshots, design-assets"]
    browser["浏览器<br/>HTTP + IP 直连<br/>http://120.26.182.61:8443/"]

    dev --> dist_sync
    dev --> source_sync

    subgraph ecs["阿里云 ECS / Ubuntu 24.04<br/>/opt/labelhub/infra<br/>docker compose prod<br/>备案/HTTPS 未完成"]
        nginx["nginx<br/>image nginx:1.27-alpine<br/>ports: 8443:80<br/>serves ./web-dist<br/>/api/ proxy"]
        api["api<br/>build services/api/Dockerfile<br/>container port 8080<br/>context path /api<br/>health /api/actuator/health"]
        agent["agent<br/>build services/agent/Dockerfile<br/>container port 8081<br/>health /actuator/health<br/>AI review + export outbox workers"]
        mysql["mysql<br/>image mysql:8.0<br/>container port 3306<br/>volume labelhub-mysql-data"]
        redis["redis<br/>image redis:7-alpine<br/>container port 6379<br/>healthcheck redis-cli ping"]
        minio["minio<br/>image minio/minio:latest<br/>container ports 9000 API / 9001 console<br/>volume labelhub-minio-data"]
        minio_init["minio-init<br/>image minio/mc:latest<br/>creates OBJECT_STORAGE_BUCKET via http://minio:9000"]
    end

    dist_sync --> nginx
    source_sync --> ecs
    browser -->|"GET / over HTTP"| nginx
    browser -->|"GET/POST /api/* over HTTP"| nginx

    nginx -->|"try_files -> index.html"| nginx
    nginx -->|"proxy_pass http://api:8080"| api

    api -->|"depends_on mysql: service_healthy"| mysql
    api -->|"depends_on minio-init: service_started"| minio_init
    api -->|"DATABASE_URL jdbc:mysql://mysql:3306/labelhub"| mysql
    api -->|"OBJECT_STORAGE_ENDPOINT http://minio:9000"| minio

    minio_init -->|"depends_on minio: service_started"| minio
    agent -->|"depends_on api: service_healthy"| api
    agent -->|"LABELHUB_API_BASE_URL http://api:8080/api"| api
    agent -->|"SPRING_DATASOURCE_URL from .env.prod"| mysql
    nginx -->|"depends_on api: service_healthy"| api
```

## 实证来源

- 生产 compose 容器、端口、依赖和环境变量：`infra/docker-compose.prod.yml`。
- Nginx 静态资源根目录与 `/api/` 反向代理：`infra/nginx/labelhub.conf`。
- 单 ECS、8443 公网入口、HTTP IP 访问与 TLS cutover 说明：`infra/deploy/README.md`。
- Web build 与 rsync 部署路径：`scripts/deploy-web.sh`、`docs/dev-environment.md`。
- API context path、默认端口、对象存储和数据库配置：`services/api/src/main/resources/application.yml`。
- Agent 端口、API base URL、outbox worker 配置和 LLM env 配置：`services/agent/src/main/resources/application.yml`。
- Agent 同一进程内包含 AI review 与 export outbox worker：`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java`、`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxExportWorker.java`。
