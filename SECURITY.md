# 安全策略

## 报告漏洞

请**不要**在公开 issue 里报告安全问题。请使用 GitHub 的 [Private vulnerability reporting](https://github.com/Iceberg86-gb/labelhub-platform/security/advisories/new) 私下提交，并附上：

- 受影响的组件（`services/api`、`services/agent`、`apps/web`、`evals/`）和版本或 commit；
- 复现步骤或概念验证；
- 你判断的影响范围。

我们会尽快确认收到，修复后在 release notes 中致谢（除非你希望匿名）。

## 范围内的重点

- 鉴权和权限：JWT、角色守卫、跨 Owner 的数据访问、`/internal/**` 的内部令牌。
- MCP 令牌：令牌只存 SHA-256、按任务限定范围、可撤销；轨迹摘要中不应出现令牌明文。
- append-only 事实被篡改的可能，例如绕过 Mapper 约束更新或删除账本。
- LLM Provider 密钥的加密存储与日志脱敏。
- 提示词注入：提交内容逃逸出 prompt 中的数据块。

## 不在范围内

- 本地开发用的默认凭据（`demo1234`、`dev-internal-token` 等），只用于本地和测试环境。
- 需要攻击者已经控制数据库或宿主机的场景。
