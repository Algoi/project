# 11 Ubuntu 开发环境与依赖版本锁定（V1.2）

## 1. 范围和优先级

本文件取代原 `09-配置与部署说明.md` 中宽泛的版本描述。**需求和 33 项功能不变**。所有新的 AI 编码成果必须遵守此处的版本约束；`templates` 下文件是初始化模板，而不是已生成并测试的完整项目。

## 2. 版本基线（2026-10-16）

| 项目 | 当前开发环境基线 | 说明 |
|---|---|---|
| 操作系统 | Ubuntu 26.04 LTS x86_64 | 主开发、测试部署目标；不依赖 Windows |
| JDK | Ubuntu OpenJDK 17.0.20.1 | Java 17，禁止隐式改为 21 |
| Maven | Apache Maven 3.9.11 | 项目使用 Maven Wrapper 固定版本 |
| Node.js | 22.22.1 | 通过 nvm 安装，并用 `.nvmrc` 固定 |
| npm | 10.9.4 | 统一 npm，提交 package-lock.json；CI 使用 npm ci |
| MySQL | MySQL Community Server 8.4.11 | 本机服务、utf8mb4，UTC 数据时间 |
| Spring Boot | 3.5.16 | 使用 starter parent BOM 锁定；3.5 开源维护已终止，生产上线前需做安全升级评审 |
| Spring Security | Spring Boot 3.5.16 BOM 管理 | 不单独指定版本 |
| MyBatis-Plus | 3.5.17 | 使用 mybatis-plus-spring-boot3-starter |
| MySQL Connector/J | Spring Boot BOM 管理 | 不单独指定版本 |
| Spring Validation | Spring Boot BOM 管理 | spring-boot-starter-validation |
| JUnit / Mockito | Spring Boot BOM 管理 | spring-boot-starter-test |
| Vue | 3.5.22 | 单页应用 SPA |
| Vite | 7.1.12 | 配置 Node 22，明确关闭版本漂移 |
| TypeScript | 5.9.3 | 前端统一 TS |
| Vue Router | 4.5.1 | Vue 3 路由 |
| Pinia | 3.0.3 | 登录态、当前网点等状态 |
| Element Plus | 2.14.7 | 管理端与家属端组件 |
| Axios | 1.12.2 | REST API |
| ECharts | 5.6.0 | 报表、时序预测曲线 |
| vue-tsc | 3.1.0 | 类型检查 |
| @vitejs/plugin-vue | 6.0.1 | Vue SFC 编译 |


版本锁定与安全维护是两件事：Spring Boot 3.5.16 为该分支最后 OSS 发布版本，不得将本文件视为长期安全支持承诺。上线前须复核安全漏洞与官方维护状态；升级 Spring Boot 4 属于单独评审的技术变更，不自动纳入本次需求。Ubuntu OpenJDK 及 MySQL 应按安全补丁维护，同时保持主次版本契约。

## 3. Ubuntu 环境前提

- Ubuntu 26.04 LTS、x86_64；建议 4 vCPU / 8GB RAM / 30GB 可用磁盘。
- 开发工具：Git、curl、unzip、build-essential；IDE 可选 IntelliJ IDEA / VS Code，不强制。
- JDK 安装Ubuntu 软件源的 OpenJDK 17；设置 `JAVA_HOME` 和 `PATH`，使用 `java -version` 检查。
- Maven Wrapper 必须生成并提交 `.mvn/wrapper/`、`mvnw`（采用 Maven 3.9.11），开发与 CI 一律 `./mvnw`；初始生成需要预装 Maven 或使用 Maven Wrapper 插件。
- 通过 `nvm` 安装 Node 22.22.1；`nvm use`；`npm -v` 应输出 10.9.4。安装项目依赖后提交 `package-lock.json`。
- MySQL 8.4.11 采用原生服务，监听 `127.0.0.1:3306`。如果 Ubuntu 默认仓库无该补丁版时优先保持 Ubuntu 安全更新的 MySQL 8.4 LTS，不强制降级，验证 `SELECT VERSION()`，不要用默认 MariaDB 替代。
- 数据库脚本执行：`mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS elder_meal CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"`，然后 `mysql -u root -p elder_meal < docs/04-数据库设计.sql`。

## 4. 环境变量与启动命令

`cp templates/.env .env` 可使用本次打包的本机开发凭据；若希望自行配置则用 `cp templates/.env.example .env`。**不要将 `.env` 加入 Git**。Spring Boot 并不会自动读取 `.env`，可在开发终端执行 `set -a; source .env; set +a` 后启动；变量值不要包含未正确转义的 shell 特殊字符。

后端：

```bash
cd server
./mvnw -v
./mvnw clean test
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

前端：

```bash
cd web
nvm use
npm ci
npm run typecheck
npm run dev -- --host 127.0.0.1
```

约定前端 `http://127.0.0.1:5173`，后端 `http://127.0.0.1:8080`，MySQL `127.0.0.1:3306`。开发代理把 `/api` 转发至后端，前端 `VITE_API_BASE_URL=/api/v1`；禁止同时在 Axios 中写死独立的外部后端域名。

## 5. 配置与目录

```text
repo/
  docs/          # 01~11 规范文档
  templates/     # 项目初始化模板（复制到真实工程后需验证）
  server/        # Java Spring Boot 源码，由 AI 编码建立
  web/           # Vue SPA，由 AI 编码建立
  .gitignore
```

- `server/pom.xml` 以 `templates/pom.xml` 为基准；使用 BOM，不允许 `LATEST`、`RELEASE`、`SNAPSHOT`。
- `web/package.json` 以 `templates/package.json` 为基准，依赖使用**精确版本**而不是 `^` 或 `~`；`npm install` 产生的 `package-lock.json` 必须提交。
- Java 打包：`./mvnw clean package`；前端打包：`npm ci && npm run build`。
- 通过 Spring Profiles 维护 dev/test/prod；敏感配置使用环境变量，示例见 `templates/application-dev.yml`。
- 附件私有存储于 `./uploads/private`，所有下载需校验权限，不能把健康信息目录映射为静态目录。

## 6. 范围与验收

- 继续使用**Java 统计式时序预测**：星期历史均值 / 近日期均值回退；不新增算法服务器、Python、模型训练。
- 推荐继续使用**Java 规则过滤 + 权重排序**；不引入新 AI/ML 基础设施。
- 开发验收：`java -version`、`./mvnw -v`、`node -v`、`npm -v`、`mysql --version` 实际一致；后端 `clean test`、前端 `npm ci && npm run build` 成功；四角色 33 项验收仍按 07/10 文档。
- 模拟支付仅限 dev/test；生产外部渠道缺少商户资料时保留接口与阻断说明，绝不使用模拟成功冒充真实支付。

## 7. 版本核验说明

官方/项目发布信息：Spring Boot 3.5.16（Spring 官方）、MyBatis-Plus 3.5.17（GitHub Releases）、Element Plus 2.14.7（官方 Changelog）；其他精确版本为**项目固定选择版本**，并非声称是截至今天的最新版本。模板依赖尚未在用户 Ubuntu 环境上运行验证。若版本仓库或镜像不可用，先记录原因并走显式版本变更，不允许 AI 静默替换。

## 8. 本机环境差异说明（V1.2）

- 当前用户已验证 Ubuntu 26.04 LTS、Git 2.53.0、OpenJDK 17.0.20.1、Maven 3.9.11、MySQL 8.4.11、Node.js 22.22.1；npm 已安装 10.9.4，请最终以 `npm -v` 验证。
- 本地数据库使用 `root@localhost` 和用户提供的开发密码，通过 `DB_USER`、`DB_PASSWORD` 注入；演示/生产必须改为最小权限数据库账号。
- `templates/.env` 含本地密码，仅供本机开发且被 `.gitignore` 排除，**严禁提交、公开上传或分享**；如压缩包要发给他人，请先删除此文件并更换密码。
- 仍使用数据库 `elder_meal`，与原数据库 SQL、应用配置保持一致。
