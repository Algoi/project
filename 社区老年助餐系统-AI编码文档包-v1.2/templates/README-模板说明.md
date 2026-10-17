# 模板用途
这些仅是开发**初始化模板**，不是已经可运行的完整工程：缺少 Spring Boot main、Vue main、tsconfig、index.html、lockfile、Maven Wrapper、数据库凭据、业务代码。AI 编码任务 T01 必须从模板生成完整工程，再实际执行构建与测试，禁止宣称模板本身已验证通过。

- `pom.xml` → `server/pom.xml`；禁止同时引入 MyBatis Starter 和 MyBatis-Plus Starter，避免自动配置冲突。
- `package.json`、`.nvmrc`、`.npmrc`、`vite.config.ts` → `web/`；运行 `npm install` 生成并提交 lockfile，然后使用 `npm ci`。
- `application-dev.yml` → `server/src/main/resources/`；环境变量从 shell 注入。
- `.gitignore` → 根目录；`.env.example` 是示例，`DB_PASSWORD` 实值不进仓库。

- 本次额外提供 `templates/.env`（包含本地开发数据库凭据）；`cp templates/.env .env` 后用 `set -a; source .env; set +a` 加载。此文件受 `.gitignore` 忽略，不要提交/转发。
