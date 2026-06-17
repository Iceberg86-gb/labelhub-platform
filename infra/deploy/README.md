# LabelHub Production Deploy

Target: single Alibaba Cloud ECS, Ubuntu 24.04, Docker Compose, public entry on 8443 before ICP/certificate cutover.

## Steps

1. Install Docker Engine and the compose plugin on the ECS host.

2. Create the deployment root:

   ```bash
   sudo mkdir -p /opt/labelhub
   sudo chown "$USER":"$USER" /opt/labelhub
   ```

3. Copy this repository to `/opt/labelhub` or sync at least `infra/`, `services/`, `packages/contracts/`, root `pom.xml`, and web build output.

4. Create production secrets:

   ```bash
   cd /opt/labelhub/infra
   cp .env.prod.example .env.prod
   chmod 600 .env.prod
   $EDITOR .env.prod
   ```

   Generate `JWT_SECRET`, `LABELHUB_INTERNAL_TOKEN`, and `LABELHUB_LLM_PROVIDER_MASTER_KEY` with a secure local command such as `openssl rand -base64 32`. Keep the master key backed up offline; losing it makes saved provider secrets unreadable.

5. Build the web UI locally and sync it to the server:

   ```bash
   pnpm --filter @labelhub/web build
   rsync -az --delete apps/web/dist/ root@120.26.182.61:/opt/labelhub/infra/web-dist/
   ```

6. Validate compose on the server:

   ```bash
   cd /opt/labelhub/infra
   docker compose --env-file .env.prod -f docker-compose.prod.yml config
   ```

7. Start production services:

   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
   docker compose --env-file .env.prod -f docker-compose.prod.yml ps
   ```

   Flyway runs in the API container on startup and applies any pending migrations.

8. Smoke test:

   ```bash
   curl -f http://127.0.0.1:8443/api/actuator/health
   curl -I http://127.0.0.1:8443/
   ```

   Open `http://120.26.182.61:8443/` while the public entry is still 8443.

9. Install backups:

   ```bash
   sudo mkdir -p /opt/labelhub/backups
   sudo chmod +x /opt/labelhub/infra/deploy/backup.sh /opt/labelhub/infra/deploy/restore.sh
   (crontab -l; echo "0 4 * * * /opt/labelhub/infra/deploy/backup.sh >> /var/log/labelhub-backup.log 2>&1") | crontab -
   ```

   Run a drill after the first backup:

   ```bash
   /opt/labelhub/infra/deploy/restore.sh /opt/labelhub/backups/<backup-dir>
   ```

## ICP And TLS Cutover

After ICP and certificate issuance, update `infra/nginx/labelhub.conf` with `server_name` and SSL certificate paths, then change the nginx mapping in `docker-compose.prod.yml` from `8443:80` to HTTPS `443:443` plus `80:80` redirect handling.
