# 邻里膳 · 社区老年助餐系统

基于 Spring Boot、Vue 3 和真实 MySQL 的四角色系统：用户与网点、菜品、老人绑定与补贴、菜谱发布、规则推荐、单日/周期订餐、付款人钱包与消费账单、模拟多渠道支付、备餐预测/历史回测、核销配送、退款、评价投诉、公告、营养报告和运营看板。

原始设计包保留在 `社区老年助餐系统-AI编码文档包-v1.2/`。实施和验收材料：

- [33 项需求与实现/测试追踪](docs/requirements-traceability.md)
- [分批检查记录](docs/implementation-plan.md)
- [完整 API 与业务边界](docs/api/v1.md)
- [部署、备份与真实支付接入边界](docs/deployment.md)
- [原 33 项整体验收报告](docs/reviews/system-acceptance.md)
- [本轮产品完善整体验收](docs/reviews/enhancement-acceptance.md)
- [模块边界、算法替换与扩展](docs/architecture/modularization.md)
- [模块化重构审核与测试](docs/reviews/modularization.md)
- [统一项目启停与验证](docs/reviews/service-management.md)
- [浅绿色主题与图片素材](docs/visual-assets.md)
- [视觉完善与浏览器验证](docs/reviews/visual-refresh.md)
- [四角色菜单点击与局域网访问验证](docs/reviews/interaction-and-lan.md)
- [算法体验与测试操作说明（从网页操作开始）](docs/algorithm-testing-guide.md)
- [产品完善实施与验收进度](docs/enhancement-plan.md)
- [产品完整性复查与后续补充清单](docs/reviews/product-completeness.md)

## 项目结构

项目根目录为本 README 所在目录。整体采用**前后端分离、后端模块化单体**：Vue 前端通过 HTTP API 访问 Spring Boot 后端，后端按业务领域组织代码，共享一个 MySQL 数据库；公共契约和算法分别构建为独立 Java 库，随后端一起运行。

```text
project/
├── README.md                         项目说明、启动、测试及结构索引
├── pom.xml                           Maven 聚合构建入口
├── mvnw / .mvn/                      根目录 Maven Wrapper
├── contracts/                        跨模块公共契约（meal-contracts）
│   └── src/main/java/com/community/meal/
│       ├── contracts/                业务接口、数据类型与业务事件
│       └── payment/                  支付渠道接口及通知类型
├── algorithms/                       纯 Java 算法库（meal-algorithms）
│   └── src/
│       ├── main/java/com/community/meal/algorithms/
│       │                             预测、推荐、注册选择及历史回测
│       └── test/java/                独立算法测试
├── server/                           Spring Boot 后端（elder-meal-server）
│   └── src/
│       ├── main/java/com/community/meal/
│       │                             启动入口、各业务领域及基础设施
│       ├── main/resources/
│       │   ├── application*.yml      公共、开发及生产环境配置
│       │   ├── db/migration/         Flyway 数据库增量迁移 V1–V11
│       │   └── static/api/v1/openapi.yaml
│       │                             HTTP API 规范
│       └── test/                     单元、模块边界及 MySQL 集成测试
├── web/                              Vue 3 + TypeScript 前端
│   ├── src/
│   │   ├── features/                 按功能划分的页面、路由及测试
│   │   ├── api/                      HTTP 客户端、认证及选项查询
│   │   ├── stores/                   Pinia 登录及身份状态
│   │   ├── layouts/                  应用布局
│   │   ├── components/               共享表单、上传、图片和图表组件
│   │   ├── shared/                   共享类型、角色及状态页
│   │   ├── utils/                    日期、导出和请求防重等工具
│   │   ├── assets/imagery/           本地图片素材
│   │   ├── router.ts                 路由组合及统一权限守卫
│   │   └── main.ts / App.vue         前端入口及根组件
│   ├── e2e/                          Playwright 真实浏览器业务测试
│   ├── package.json / package-lock.json
│   │                                 前端命令及依赖锁定
│   └── vite.config.ts                开发服务、API 代理及构建配置
├── scripts/                          项目启停、测试、备份和恢复演练
├── deploy/                           Nginx 配置片段及 systemd 服务模板
├── docs/                             需求追踪、架构、接口、部署及操作文档
│   └── reviews/                      分阶段审核、测试报告和截图
├── 社区老年助餐系统-AI编码文档包-v1.2/  原始需求与设计资料
├── .env.example                      环境变量配置示例
├── .env                              本机私有配置，按部署环境创建
├── .run/                             启停脚本生成的进程记录和日志
├── uploads/private/                  默认私有附件目录，可通过配置调整
└── backups/                          备份脚本生成的私有归档
```

`.run/`、`uploads/private/`、`backups/` 按运行需要生成；Maven 构建产物位于各模块的 `target/`，前端依赖和构建产物位于 `web/node_modules/`、`web/dist/`。数据库数据由 MySQL 管理，项目中的迁移脚本用于创建和升级表结构。私有配置、附件和备份不应打包进程序镜像。

### 后端业务目录

以下目录均位于 `server/src/main/java/com/community/meal/`：

| 目录 | 职责 |
|---|---|
| `auth`、`site`、`catalog`、`audit` | 账号与权限、网点、菜品与价格、审计 |
| `elder`、`address` | 老人及健康资料、家属绑定、补贴资格与历史、常用地址 |
| `menu` | 菜谱、发布审核、复制与周模板 |
| `forecast`、`preparation` | 加载预测历史、调用预测及回测算法、生成备餐计划 |
| `recommendation` | 饮食安全过滤、推荐编排、权重配置及曝光记录 |
| `order` | 单日和周期订餐、支付、退款、核销、配送及订单时间线 |
| `payment` | 模拟及外部支付渠道适配 |
| `wallet`、`billing` | 付款人钱包、充值与资金流水、消费账单及导出 |
| `messages`、`workbench` | 站内消息、未读状态及各角色待办 |
| `notice`、`review`、`ticket` | 公告、评价和投诉工单 |
| `nutrition`、`dashboard` | 营养报告及运营统计 |
| `integration` | 跨领域查询适配、私有附件授权及业务通知路由 |
| `common`、`config`、`dev` | 通用基础设施、应用组装及开发演示数据初始化 |

### 前端功能目录

`web/src/features/` 下的每个功能通过 `index.ts` 导出公开入口，由 `routes.ts` 定义所属路由；`router.ts` 统一组合四种角色可用的页面。

| 功能目录 | 页面范围 |
|---|---|
| `home`、`identity` | 首页、角色工作台、登录、账号设置与用户管理 |
| `sites`、`catalog`、`audit` | 网点、菜品与价格、审计记录 |
| `elders`、`addresses` | 老人、绑定、补贴及地址管理 |
| `planning`、`recommendation` | 菜谱、周模板、预测回测及推荐规则 |
| `ordering`、`finance` | 订餐、订单处理、钱包及账单 |
| `messages`、`notices`、`reviews`、`tickets` | 消息、公告、评价及投诉 |
| `reporting` | 营养报告和运营看板 |

### 模块依赖与算法替换

根 Maven 工程依次构建 `contracts`、`algorithms`、`server`。`server` 依赖前两个库；`contracts` 与 `algorithms` 互不依赖，也不依赖 Spring 或数据库。业务通过公共契约交互，后端保留订单、支付、退款与钱包的本地事务；这些业务领域目前运行在同一个后端进程中。

`algorithms` 只负责计算：`ForecastAlgorithm` 定义时序预测接口，提供同星期均值和近期加权均值两种统计实现；`RecommendationAlgorithm` 定义推荐接口，提供加权评分实现；`ForecastBacktest` 负责历史滚动回测。后端负责权限、历史数据读取、安全过滤及结果保存。

已注册算法通过 `APP_FORECAST_ALGORITHM` / `APP_RECOMMENDATION_ALGORITHM` 配置选择，整体重启后生效。新增实现需补充算法测试，在后端 `AlgorithmConfiguration` 中注册并重新构建。详细边界和替换步骤见 [模块架构文档](docs/architecture/modularization.md)，网页测试方法见 [算法测试指南](docs/algorithm-testing-guide.md)。

目前 `deploy/` 提供 Nginx 和 systemd 部署参考，统一启停脚本面向 Linux；Dockerfile 和 Docker Compose 配置尚未实现。

## Git 版本管理

项目使用根目录的 Git 仓库统一管理前端、后端、算法、公共契约、脚本、原始设计资料及验收文档，主分支为 `master`。`.gitignore` 排除私有环境配置、运行日志、附件、备份、依赖和构建产物；`.env.example` 与依赖锁文件保留在版本管理中。`.gitattributes` 统一源码换行，Windows 命令脚本使用 CRLF。

```bash
git status                     # 查看当前修改
git diff                       # 查看未暂存的具体改动
git switch -c feature/功能名称  # 创建并切换到开发分支
git add 需要提交的文件路径       # 暂存本次改动
git diff --cached              # 提交前检查暂存内容
git commit -m "描述本次改动"     # 创建本地提交
git log --oneline -5           # 查看最近提交
```

当前仅管理本地版本。远程代码仓库需要另行配置；Docker Hub 用于存放构建后的镜像，不作为 Git 源码仓库。Git 记录源码与文档，业务数据库和私有附件仍通过备份流程保护。

## 启动

环境：Java 17、Maven 3.9.11、MySQL 8.4、Node 22.22.1、npm 10.9.4。依赖和锁文件按原设计包第 11 章固定；后端通过根目录 `./mvnw` 组合构建 contracts、algorithms、server 三个模块。

1. 参考 `.env.example` 设置根目录 `.env`，保存数据库凭据和稳定的 `APP_ENCRYPTION_KEY`（`openssl rand -hex 32` 生成，勿提交）。本机已有配置可直接使用。确认 MySQL 已运行；新环境首次安装前端依赖：`npm --prefix web ci`。
2. 在项目根目录使用统一脚本管理整个项目。下面每行是独立命令，按需执行：

   ```bash
   ./scripts/service.sh start    # 后台启动前端和后端，等待整个项目就绪
   ./scripts/service.sh status   # 查看项目整体状态和各组件状态
   ./scripts/service.sh restart  # 整体停止后重新启动
   ./scripts/service.sh stop     # 关闭本项目的前后端及已运行的预览服务
   ./scripts/service.sh logs     # 查看项目各组件最近的日志
   ```

   不带参数执行 `./scripts/service.sh` 等同于 `status`。启动后可以关闭终端；重复 `start` 会继续使用已有项目进程，缺少的组件会补启。脚本会接管从此项目目录手工启动的服务，并核对进程归属；端口被其他程序占用时会明确报错，不会关闭其他项目。`status` 整体未就绪时退出码为 3。

   PID、启动标识和日志保存在 `.run/`：`services.json`、`backend.log`、`web.log`。已有手工启动的服务继续使用原日志位置；通过整体 `restart` 启动后会写入统一日志目录。脚本使用 Linux `/proc`、Python 3.10+、`lsof`，MySQL 继续由系统服务管理。

3. 首次空库初始化演示账号与业务数据时，先确保项目已停止，再使用统一启动命令：

   ```bash
   APP_SEED_ENABLED=true APP_SEED_BUSINESS=true ./scripts/service.sh start
   ```

   初始化后日常使用普通 `start` / `restart` 即可；已有业务不会被重复初始化覆盖。底层 `dev-server.sh` 是管理脚本调用的后端开发入口，日常无需单独执行。

访问 **http://127.0.0.1:5173**，后端为 `http://127.0.0.1:8080`。默认等待启动 180 秒，首次下载依赖较慢时可用 `./scripts/service.sh start --timeout 600`。后端端口跟随 `.env` 的 `SERVER_PORT`，前端代理同步指向该端口。

前端监听 `0.0.0.0:5173`，同一局域网的设备可以使用 `http://服务器的局域网IP:5173` 访问（本机当前为 `http://192.168.0.128:5173`）。浏览器的 API 请求由前端代理至本机后端，无需修改接口地址。IP 可能随网络配置变化；查看当前地址可运行 `ip -brief address`。启停仍使用上述统一脚本。

需要构建预览时可执行 `npm --prefix web run build` 和 `npm --prefix web run preview -- --host 127.0.0.1`，地址为 http://127.0.0.1:4173 。统一 `stop` 会一并关闭从此项目启动的预览；普通 `start` 启动 5173 开发前端。

| 身份 | 账号 | 密码 |
|---|---|---|
| 系统管理员 | admin | admin |
| 食堂工作人员 | canteen | canteen |
| 老年人家属 | family | family |
| 社区操作员 | community | community |

账号均无 demo 后缀，密码与对应账号相同；仅开发演示配置使用这些短密码。普通账号要求至少 12 字符，密码重置会注销该账号已有会话。旧版账号迁移说明保留在 [账号调整记录](docs/reviews/demo-accounts.md)。

## 体验流程

演示数据包含“李阿姨（演示）”“王大爷（演示）”及“赵阿姨（演示）”、4 道菜和未来七天已发布午餐，历史数据均为虚构开发数据。家属可以选择老人、订餐并模拟支付；食堂将订单备餐、出餐；家属出示取餐凭证后，由食堂或社区核销。配送走独立的出发、送达流程。已支付未备餐订单可申请退款，由社区审核。

管理员维护价格、账号、网点、公告和推荐规则，并查看看板。食堂上报菜谱和查询预测，社区确认发布、处理绑定/补贴与投诉。所有页面连接后端持久化数据，未审核绑定无法读取健康资料或下单。

家属首页和账号设置显示本人钱包余额，“我的钱包”可演示充值并查看资金流水；订单支付时可选择钱包，退款退回实际付款账户。“支付与账单”单独统计成功支付、退款和净支出，可筛选并导出。新增消息中心、待办、常用地址、绑定与补贴历史，以及食堂菜谱复制/周模板。

整体数据备份：`./scripts/backup.sh create`；恢复演练：`./scripts/backup.sh verify backups/实际文件.tar.gz`。归档含敏感配置和私有附件，仅保存在受控目录。详见部署文档。

## 测试

```bash
# 根目录：无需数据库的模块边界、算法与存储适配器检查
./scripts/check-modules.sh

# 创建并清理独立临时 MySQL 库，不删除业务库
./scripts/test-server.sh clean verify

cd web
npm ci
npm run typecheck
npm test
npm run build

# 已启动上述演示后端与前端时，真实 Chrome 桌面和手机网页测试
npm run test:e2e
# 也可测试构建产物
WEB_BASE_URL=http://127.0.0.1:4173 npm run test:e2e
```

默认 Chrome 路径 `/usr/bin/google-chrome`，可用 `CHROME_PATH` 指定。浏览器测试实际操作表单和业务流程，会生成带“网页测试”“闭环老人”“分支流程老人”等标识的演示测试数据；不要对生产数据运行。改密测试在 finally 中恢复 `family/family`。截图和失败追踪位于 `web/test-results/`。

后端测试用真实 MySQL 和 Flyway，测试账号需要创建/删除临时库权限。直接运行 Maven 测试须提供专用 `TEST_DB_URL`；不要指向业务库。测试脚本通过文件锁避免并发构建互相干扰。

## 数据及约束

Flyway V1–V11 管理数据库结构及增量迁移，已有未纳管非空库默认拒绝迁移。时间戳 UTC、供餐日期按 Asia/Shanghai；金额 DECIMAL，不接受客户端定价。待支付占位 15 分钟，同老人同日同餐次仅一个有效订单。支付、退款、核销有状态、事务、防重与审计；标准菜品后续变更不修改历史订单快照。

无历史预测返回“数据不足”，无消费报告不编造营养值。营养报告仅统计平台已核销/送达餐食。上传文件私有鉴权、大小与格式检查；证件号和可恢复取餐码使用 AES-GCM；数据库保存取餐码哈希用于核验。完整接口索引位于 `server/src/main/resources/static/api/v1/openapi.yaml`，已登录后可请求 `/api/v1/openapi.yaml`。

在线支付已完成渠道抽象与本地模拟联调，**尚未接通真实资金渠道**。生产模式关闭模拟成功接口，真实支付需商户资料与渠道验收，详见部署文档。

后端业务按领域划分，跨模块使用 contracts 接口；预测与推荐计算位于独立 algorithms 库。可通过 `APP_FORECAST_ALGORITHM` / `APP_RECOMMENDATION_ALGORITHM` 选择已注册实现并重启。前端每个功能拥有公开路由入口。当前仍共享数据库，具体边界、剩余读取耦合和新增算法步骤见模块架构文档。
