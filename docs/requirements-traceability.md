# 10 原始需求逐项追踪矩阵

本文件为开发验收副本，原始文档保持不动。接口均以前缀 `/api/v1` 提供，页面连接真实后端和 MySQL。实施检查点见 [实施计划](implementation-plan.md)，执行结果见 [整体验收报告](reviews/system-acceptance.md)。

**这是验收时防遗漏的基线**。原始需求共有四类角色、33项功能（7+7+9+10）。每条必须实现并验收。

2026-10-17 产品完整性复查补充说明：下表保留既有实现与历史验收记录；本次新增发现部分列表搜索未接通、预测故障影响备餐加载、首页公告失败误显示为空等未覆盖问题，需要补充修复和回归。缺口、证据和新增功能建议见 [产品完整性复查](reviews/product-completeness.md)，不能仅由历史“已验收”状态推断全部异常路径均已覆盖。

| ID | 原始功能（保持原义） | 对应页面 | 主要接口 | 实现任务 | 验收状态 | 主要测试证据 |
|---|---|---|---|---|---|---|
| A01 | 用户权限管理 | `/admin/users` | `/users、/roles` | T02 | ✅已验收 | AdminBusinessTest、AuthConcurrencyIntegrationTest、auth.spec、导航遍历 |
| A02 | 菜品价格与菜品库维护 | `/admin/dishes` | `/dishes` | T02 | ✅已验收 | AdminBusinessTest、FileBusinessTest、admin.spec |
| A03 | 公告发布管理 | `/admin/notices` | `/notices` | T09 | ✅已验收 | CommunicationBusinessTest、workflows 公告发布/撤回 |
| A04 | 数据可视化看板 | `/admin/dashboard` | `/dashboard/*` | T10 | ✅已验收 | DashboardBusinessTest、AlgorithmEdgeTest、看板页面遍历 |
| A05 | 服务网点管理 | `/admin/sites` | `/sites` | T02 | ✅已验收 | AdminBusinessTest、跨网点权限测试、admin.spec |
| A06 | 操作日志审计 | `/admin/audit` | `/audit-logs` | T02 | ✅已验收 | AdminBusinessTest、各业务审计断言、网页审计查询 |
| A07 | 膳食推荐算法参数配置 | `/admin/recommendation-settings` | `/recommendation-config、/diet-rules` | T05 | ✅已验收 | MenuForecastRecommendationTest、AlgorithmEdgeTest、网页参数生效 |
| C01 | 菜谱提交上报 | `/canteen/menus` | `/sites/{id}/menus` | T04 | ✅已验收 | MenuForecastRecommendationTest、workflows 菜谱上报/发布 |
| C02 | 备餐出餐清单 | `/canteen/preparation` | `/sites/{id}/preparation-list` | T08 | ✅已验收 | AlgorithmEdgeTest、workflows 备餐计划/打印入口 |
| C03 | 现场就餐核销 | `/canteen/redemptions` | `/redemptions/*` | T07 | ✅已验收 | OrderLifecycleTest、meal-loop 浏览器核销 |
| C04 | 配送清单生成 | `/canteen/delivery` | `/sites/{id}/delivery-list` | T07 | ✅已验收 | OrderLifecycleTest、workflows 配送出发/送达 |
| C05 | 菜品评价查看 | `/canteen/reviews` | `/sites/{id}/reviews` | T09 | ✅已验收 | CommunicationBusinessTest、评价页面/图片鉴权、导航遍历 |
| C06 | 公告查看 | `/canteen/notices` | `/notices` | T09 | ✅已验收 | CommunicationBusinessTest、workflows 公告范围 |
| C07 | 备餐预测查询 | `/canteen/forecasts` | `/sites/{id}/forecasts` | T08 | ✅已验收 | MenuForecastRecommendationTest、AlgorithmEdgeTest、预测页面遍历 |
| F01 | 老人信息绑定与补贴申请 | `/family/elders` | `/family/bindings、/subsidy-applications` | T03 | ✅已验收 | ElderBusinessTest、FileBusinessTest、meal-loop 绑定/补贴/附件 |
| F02 | 家属代订餐 | `/family/order/new` | `/elders/{id}/recommendations、/orders` | T05/T06 | ✅已验收 | OrderConcurrencyBusinessTest、MenuForecastRecommendationTest、meal-loop |
| F03 | 在线支付 | `/family/payments` | `/orders/{id}/payments` | T06 | ✅已验收 | PaymentNoticeTest、OrderLifecycleTest、ProductionBoundaryTest、网页模拟支付 |
| F04 | 订单管理 | `/family/orders` | `/orders、/orders/{id}/cancel、/pickup-voucher` | T06/T07 | ✅已验收 | OrderLifecycleTest、meal-loop 凭证/状态、workflows 退款 |
| F05 | 菜品评价 | `/family/reviews` | `/reviews` | T09 | ✅已验收 | CommunicationBusinessTest、meal-loop 评分/文字/图片 |
| F06 | 意见反馈 | `/family/tickets` | `/tickets` | T09 | ✅已验收 | CommunicationBusinessTest、meal-loop 投诉轨迹 |
| F07 | 公告查看 | `/family/notices` | `/notices` | T09 | ✅已验收 | CommunicationBusinessTest、workflows 定向重要公告 |
| F08 | 个人中心 | `/account`（`/family/profile` 跳转） | `/auth/profile、/family/bindings` | T06 | ✅已验收 | AuthIntegrationTest、AlgorithmEdgeTest、auth.spec、个人偏好/绑定/账单页面 |
| F09 | 膳食报告查看 | `/family/nutrition` | `/elders/{id}/nutrition-report` | T10 | ✅已验收 | OrderLifecycleTest、meal-loop 1 餐 300 kcal 报告 |
| O01 | 老人档案管理 | `/community/elders` | `/elders` | T03 | ✅已验收 | ElderBusinessTest、meal-loop 网页建档及健康核对 |
| O02 | 老人补贴资格审核 | `/community/subsidies` | `/subsidy-applications/{id}/approve` | T03 | ✅已验收 | ElderBusinessTest、meal-loop 补贴审核/生效 |
| O03 | 老人代订餐服务 | `/community/proxy-orders` | `/proxy-orders/recurring、/orders` | T05/T06 | ✅已验收 | OrderLifecycleTest、workflows 单日/周期独立订餐 |
| O04 | 每日餐品维护 | `/community/menus` | `/sites/{siteId}/menu-items/{id}/availability` | T04 | ✅已验收 | MenuForecastRecommendationTest、workflows 发布/下架/恢复 |
| O05 | 网点订单处理 | `/community/orders` | `/orders` | T07 | ✅已验收 | OrderLifecycleTest、导航遍历、订单筛选/异常记录接口 |
| O06 | 退单退款审核 | `/community/refunds` | `/refunds` | T07 | ✅已验收 | OrderLifecycleTest 退款失败/重试/防重、workflows 全额退款 |
| O07 | 取餐核销核验 | `/community/redemptions` | `/redemptions/*` | T07 | ✅已验收 | OrderLifecycleTest 共用状态机、导航遍历、meal-loop 核销 |
| O08 | 投诉工单处理 | `/community/tickets` | `/tickets` | T09 | ✅已验收 | CommunicationBusinessTest、meal-loop 社区回复/重大升级/办结 |
| O09 | 公告查看 | `/community/notices` | `/notices` | T09 | ✅已验收 | CommunicationBusinessTest、workflows 公告范围、导航遍历 |
| O10 | 老人膳食分析 | `/community/nutrition` | `/elders/{id}/nutrition-report` | T10 | ✅已验收 | ElderBusinessTest 资源隔离、OrderLifecycleTest 快照营养、报告页面遍历 |

验收包括代码、真实数据库行为和桌面/手机网页验证。F03 状态表示渠道接口与模拟联调通过；真实生产支付需商户资料，不表示已接通真实资金。打印入口使用浏览器打印，报告与清单均为实际接口数据。

检查数字：管理员7项、食堂7项、家属9项、社区10项，总计33项。

本轮功能模块拆分、算法替换和最新回归结果另见 [模块化重构审核](reviews/modularization.md)，原需求编号与上述历史验证记录保留。


原 33 项之外的 E01–E12 补充：钱包/账单（WalletBusinessTest、WalletConcurrencyTest、wallet 浏览器）；待办/消息/地址/进度（WorkplaceBusinessTest、workplace）；绑定/补贴生命周期与模板（LifecycleBusinessTest、lifecycle）；CSV（export.test、lifecycle 下载）；算法解释/回测（ForecastBacktestTest、BacktestBusinessTest、backtest）。原 P0 搜索/降级/反馈与超 100 条选择由 ProductSearchTest、PreparationDegradationTest、AlgorithmReplacementIntegrationTest、RecordPage.test、resilience 覆盖。分批证据见 [产品完善记录](enhancement-plan.md)。
