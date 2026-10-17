# 社区老年助餐智能膳食管理系统 · AI 编码文档包 V1.2

本包在 V1.1 基础上更新开发环境，不改变系统四类角色 33 项功能。

## 本机开发环境

- Ubuntu 26.04 LTS / OpenJDK 17.0.20.1 / Maven 3.9.11
- MySQL 8.4.11 / Node.js 22.22.1 / npm 10.9.4
- Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Vue 3.5.22 等项目依赖版本保持 V1.1 不变。
- **数据库名称** `elder_meal`；本地数据库账号已配置为 `root`，密码保存在 `templates/.env`（不要提交 Git 或分享）。

## 从文档开始开发

1. 阅读 `docs/00-README-实施总览.md`、`docs/11-Ubuntu环境与版本锁定.md`。
2. 根据 `docs/04-数据库设计.sql` 创建数据库。
3. 在项目根目录执行 `cp templates/.env .env`、`set -a; source .env; set +a`，供后端读取连接信息；**Spring Boot 不会自动读取 `.env`**。
4. 后端使用 `templates/pom.xml` 和 `templates/application-dev.yml` 初始化；前端使用 `templates/package.json`、`.nvmrc`、`.npmrc`、`vite.config.ts` 初始化。
5. 按照 `docs/08-AI编码任务清单.md` 的 T01–T11 实施，用 `docs/10-需求追踪矩阵.md` 核查 33 项功能。

`templates/` 为初始化模板，并非完整可运行应用。依赖下载、构建和数据库脚本需要在用户 Ubuntu 环境验证。不要增加 Python、模型训练、微服务等设计。

**安全提示：** 这个压缩包包含仅供本地开发的数据库密码；不要推送到公开仓库或转发给他人。真实部署必须改为最小权限账号并替换密码。
