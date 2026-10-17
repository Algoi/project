# 运行与部署

当前交付为本地完整业务系统。开发与测试默认使用两个明确标识的模拟支付渠道；生产真实资金渠道必须另行提供商户签约、密钥、回调协议并完成渠道结算验收。`prod` 下模拟成功接口不存在、演示初始化不运行、未接通真实通道时不提供外部支付；家属已有余额仍可使用钱包支付。

## 本地

运行命令、固定账号和测试命令见根 README。新环境设置 `.env` 的数据库凭据，以及 `APP_ENCRYPTION_KEY`（32 字节、64 位十六进制，可用 `openssl rand -hex 32` 生成）。密钥须稳定保存，不能每次启动重新生成，否则已有证件与凭证无法解密。`APP_UPLOAD_PATH` 默认 `./uploads/private`；开发脚本将相对路径按项目根目录转换为绝对路径，避免 Maven 的 server 工作目录改变附件位置。直接运行 JAR 时请从根目录运行或指定绝对路径。

日常通过 `./scripts/service.sh start|stop|restart|status|logs` 管理整个项目的前后端。首次空库可在项目停止状态下显式同时启用两种演示初始化：

```bash
APP_SEED_ENABLED=true APP_SEED_BUSINESS=true ./scripts/service.sh start
```

账号初始化遇到非空用户表不会覆盖；业务初始化要求四个演示账号已存在，以 `app_seed_marker` 标识一次性完成，重复执行不重置业务数据。业务演示包含 3 位虚构老人、4 道演示菜、未来七天午餐、两个网点的历史就餐记录。名单、价格、健康标签均为开发虚构数据；页面中含“演示”字样。七天之后须按正常菜谱流程继续上报供餐，不自动伪造新的历史样本。

## 构建和生产运行模板

```bash
./mvnw -B -ntp clean package -DskipTests
cd web
npm ci
npm run build
```

发布前先执行 README 的完整测试；跳过测试的打包命令仅用于已经验收的构建输入。产物为 `server/target/elder-meal-server-1.0.0.jar` 和 `web/dist/`。

`deploy/elder-meal.service` 提供 systemd 模板，`deploy/nginx.conf` 提供 Nginx HTTPS server 块内的路由片段；需部署者设置域名、TLS、专用系统用户、目录及 `/etc/elder-meal/server.env`：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/elder_meal?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USER=elder_meal_app
DB_PASSWORD=部署环境设置
APP_ENCRYPTION_KEY=部署环境生成并安全保存的64位十六进制密钥
APP_UPLOAD_PATH=/var/lib/elder-meal/private
```

环境文件权限 600，运行账号能读；`/var/lib/elder-meal` 仅运行用户可写。数据库账号按迁移/运行需要授权，不使用演示 root 作为生产配置。Flyway V1–V11 自动迁移空库或已纳管版本；未纳管非空库默认拒绝启动，不自动 baseline。应用不是公开附件静态服务器；不要将私有上传目录放在 Nginx root 下。

生产管理员由部署者使用强密码初始化账号和管理员角色，不带开发同名短密码。密码使用 BCrypt；已有管理员可在用户页面创建业务人员并分配网点。前端经同源 `/api` 代理，无需开放任意跨域来源。

## 支付适配边界

`PaymentGateway` 定义渠道、模拟通知、通知验证及退款接口。`MockPaymentGateway` 仅 dev/test 生效，用进程内随机密钥签发和校验 HMAC-SHA256 模拟通知；金额、渠道、支付单与订单核对后，以订单锁和唯一账本防重。可通过 `app.payment.mock-channels=MOCK_A,MOCK_B` 配置启用的演示渠道（也可只启用其一）。外部接口不接受客户端自行填写“支付成功”。真实渠道实现需替换 `ExternalPaymentGateway`，按支付商协议加入预支付、验签入口、重试及对账，并确保原订单/退款幂等键稳定；当前该适配器明确返回“未配置”。

## 算法与模块配置

根目录 Maven Reactor 组合 contracts、algorithms 和 server，打包时将两个内部库包含在同一可执行 JAR 中。`APP_FORECAST_ALGORITHM` 默认 `weekday_average_v1`，可选 `recent_weighted_average_v1`；`APP_RECOMMENDATION_ALGORITHM` 默认 `weighted_score_v1`。切换已有实现后重启生效，新增实现需注册 Bean、测试、重新构建。标识重复或配置未知实现时拒绝启动。详见 [模块架构](architecture/modularization.md)。

## 备份和恢复

备份需同时包含 MySQL 数据库、私有附件和独立保存的 AES 密钥。订单、支付、退款及补贴账本不要手工删除或改状态。使用部署者管理的 MySQL 登录配置（例如 `mysql_config_editor` 的 `eldermeal-backup` 登录路径），避免密码出现在命令行：

```bash
mysqldump --login-path=eldermeal-backup --single-transaction --routines --triggers elder_meal > elder_meal-backup.sql
```

备份存储在受控位置，定期在独立数据库恢复并核对：Flyway 版本、订单数、成功支付与退款净额、补贴账本净额、附件文件及解密后的取餐凭证。演练通过后再安排正式迁移；迁移前先备份，失败时恢复同一时间点的数据库和附件。本次仅交付和验证本地环境，没有发布外网生产服务。


## 整体数据备份与恢复演练脚本

在根目录执行（MySQL 账号须有创建、删除临时数据库的权限）：

```bash
./scripts/backup.sh create
./scripts/backup.sh verify backups/实际生成的备份文件.tar.gz
```

默认写入 `backups/`，支持 `create /受控备份目录`。归档文件权限为 600，包含 `.env` 的数据库凭据和解密密钥，目录应仅授权运维人员读取；不要公开上传。脚本不会在日志打印配置内容。建议在暂停业务写入的维护窗口备份；脚本发现订单/支付等计数变化会中止，需重试。附件使用私有实际目录，不接受链接。

演练校验完整文件清单，恢复到新的 `elder_meal_restore_*` 数据库，检查业务记录数与钱包对账，再自动删除临时库。不会恢复覆盖当前运行项目。实际故障切换须在停服维护下，由运维把同一归档的数据库、private 附件、server.env 解密配置一起恢复并核对，再通过统一脚本启动。当前没有自动定时任务；可以由运维用系统定时器调用 create 并设置受控异地保留策略。

钱包充值为 dev/test 演示渠道，prod 不开放充值确认。新钱包从零开始；原模拟支付不生成余额。真实充值、商户结算和渠道对账仍需外部商户配置与专项联调。
