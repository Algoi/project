# 模块化重构审核与测试

日期：2026-10-17。目标是在现有功能上形成可维护的模块边界，支持独立替换预测、推荐和基础设施实现。采用模块化单体，保留共享 MySQL 与交易事务。完整结构、接口表和新增算法步骤见 [模块架构](../architecture/modularization.md)。

## 实施检查点

| 检查点 | 完成内容 | 验证 |
|---|---|---|
| 纯算法边界 | 新建 algorithms Maven 库、类型化输入输出、注册器、两种预测实现与推荐接口 | 先写契约测试确认缺少实现时失败，再实现；5 项独立算法测试通过 |
| 后端业务边界 | 新建 contracts 库；拆分综合管理、沟通和订单服务；跨模块使用接口；拆分报表读取和私有文件存储 | 第一轮原 80 项业务回归通过，再增加依赖、写表归属、存储和算法替换测试 |
| 前端功能边界 | 功能拥有页面与公开路由；拆分综合管理和沟通页；提取共享类型 | 17 项前端测试及类型检查通过，包含 3 项架构约束和原订餐异步竞争测试 |
| 完整回归 | 干净构建、HTTP 兼容检查、桌面/手机 Chrome 真实操作 | 下表记录最终结果 |

## 测试结果

- 干净构建 `./scripts/test-server.sh clean verify`：algorithms 5 项、server 88 项全部通过，构建成功；包括真实 MySQL 库中空库执行 V1–V8。
- 后补的 HTTP 兼容集成测试：1 项通过，比较静态 OpenAPI 和实际 Spring 映射，92 个路径、119 个操作全部匹配。
- 最新全套 Java 测试：algorithms 5 项、server 89 项全部通过，合计 94 项，零失败和跳过；含上述 HTTP 兼容检查。
- 无数据库检查 `./scripts/check-modules.sh`：算法 5 项、后端依赖/写表与文件适配器 6 项通过；SQL 写表检查要求实际扫描超过 30 条命令，避免空检查。
- 前端：类型检查、17 项测试、生产构建通过。
- 配置隔离复查：显式设置本地 `APP_FORECAST_ALGORITHM=recent_weighted_average_v1`，10 项预测、推荐及替换集成测试通过；test 配置固定基准实现，替换测试用例单独覆盖选择项。
- 浏览器：32 项全部通过（桌面 16、手机 16）。Chrome 真实访问构建预览，包含四角色全部导航、订餐闭环、退款、配送、周期代订、公告、工单、报告与新增用户/网点页面操作。
- 管理页重复运行复查：桌面/手机 4 项通过，测试通过网页分页定位新网点，已覆盖测试数据超过 20 条的情况。
- 业务数据核对：金额快照不一致、重复成功支付、重复有效餐次、重复核销、退款账本异常和完成未交付共 6 项异常数均为 0。

算法替换集成测试实际使用其他 Bean 与选择配置：最近加权预测返回预期 23，记录对应算法标识；替代排序器仅收到通过过滤的候选。伪造被排除餐品被拒绝，未产生曝光；直接下单仍执行禁忌校验。此测试故意触发一次算法契约错误，日志中的 IllegalStateException 属预期拒绝，测试本身成功。

存储测试覆盖写读删除、唯一存储键、路径穿越及符号链接拒绝；原 MySQL/HTTP 文件回归验证权限和附件格式。原订单并发测试继续核对有限库存及同老人同餐次并发规则；支付、退款重试和重复核销测试保留。

测试使用脚本创建并清理临时数据库。运行中的演示业务库只应用 V8 增量及明确网页测试数据，未重置原账号或业务表。

## 审核结论与实际边界

1. 算法与公共契约独立编译，纯算法没有 Spring、登录、数据库或文件依赖。业务模块之间通过构造函数注入公共接口；源码检查禁止引用其他业务实现，订单/渠道为同一交易上下文。
2. 写表归属已受静态检查约束。菜谱下架改为调用订单公开接口，不再越界更新订单。创建、支付、退款与账本仍共享本地事务和行锁。
3. 前端路由由各功能公开入口导出，应用只组合路由；共享类型不从 Vue 页面引入。账号与网点、公告与工单的修改已落在各自页面。
4. 算法选择以配置和 Bean 注册为准。新增实现需要开发、测试和构建，切换现有实现需要重启；当前不支持运行时上传或任意热替换。
5. 各业务仍共享同一个数据库、部署产物、鉴权基础设施和部分列表读取 SQL；多个公共业务接口仍使用 Map，后续可逐步收紧为 DTO。报表与附件访问等跨领域 SQL 集中在明确读取适配器中。未将本次实现描述为数据库隔离或完整微服务。

开发脚本已改为先安装内部库再启动 server，并按项目根目录规范化相对附件路径。生产构建从根 Maven Reactor 执行。README、部署文档及 `.env.example` 已同步。


## 验证材料

- [Java 测试汇总](modularization-java-results.txt)、[算法选择与配置隔离](modularization-selection-test-results.txt)
- [前端测试](modularization-web-test-results.txt)、[前端构建](modularization-web-build-results.txt)
- [完整浏览器记录](modularization-browser-results.txt)、[管理页分页复查](modularization-admin-recheck-results.txt)
- [数据核对 SQL](modularization-integrity.sql)、[核对结果](modularization-integrity-results.txt)
- [开发脚本实际启动](modularization-startup-results.txt)
- [桌面管理页](screenshots/modularization-modules-admin-desktop.png)、[手机管理页](screenshots/modularization-modules-admin-mobile.png)
- [桌面报告](screenshots/modularization-meal-report-desktop.png)、[手机报告](screenshots/modularization-meal-report-mobile.png)

本轮验证已完成；前端开发地址 http://127.0.0.1:5173，构建预览 http://127.0.0.1:4173，后端 8080。
