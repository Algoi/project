# 算法操作说明验证

日期：2026-10-17。用户操作步骤见 [算法体验与测试操作说明](../algorithm-testing-guide.md)。

## 已核对的行为

- 当前运行的人数预测为 `weekday_average_v1`，菜品推荐为 `weighted_score_v1`。
- 真实 Chrome 通过 `http://192.168.0.128:5173` 登录并点击菜单，七天预测逐一按返回样本独立复算，核对页面显示。
- 保留现有演示老人资料和推荐配置，切换李阿姨与王大爷，核对花生鸡丁的排除/可选、推荐分降序及页面顺序。
- 独立算法测试验证 20 人均值、23 人加权预测、76.50 分推荐、边界输入和实现选择。
- 后端测试使用随机命名的临时 MySQL 库，结束后清理；覆盖规则过滤、预测历史、算法替换及替换后的安全约束。

## 本轮修复

局域网登录测试原先未覆盖订餐。此次算法浏览器测试复现局域网 HTTP 下 `crypto.randomUUID is not a function`，导致家属订餐和社区代订页面初始化失败。

先补前端回归测试并确认原实现失败，再改为共享的防重键生成函数：可用时使用原接口，否则使用浏览器 `getRandomValues` 生成 UUID v4 格式的随机标识。测试验证缺少 `randomUUID` 时仍加载推荐、相同请求重试沿用防重键、切换日期生成新键。服务端幂等校验保持原规则。

首次浏览器失败记录保留于 [初次算法网页测试](algorithm-guide-browser-initial.txt)，前端复现见 [修复前](lan-order-regression-before.txt)，修复后的全量前端结果见下表。随后通过局域网 IP 扩大检查至四角色菜单点击、完整订餐核销评价与投诉流程。

## 执行结果

| 检查 | 结果/记录 |
|---|---|
| 独立算法 | 5 项通过，[输出](algorithm-guide-unit-results.txt) |
| 后端算法与业务联动 | 10 项通过，[输出](algorithm-guide-integration-results.txt) |
| 前端单元测试 | 24 项通过，[输出](algorithm-guide-web-unit-results.txt) |
| 完整流程后数据核对 | 6 项均为 0 异常，[输出](algorithm-guide-integrity-results.txt) |
| 类型检查 | 通过，[输出](algorithm-guide-typecheck-results.txt) |
| 生产构建 | 通过，[输出](algorithm-guide-build-results.txt) |
| 桌面/手机局域网浏览器回归 | 16 项全部通过，[输出](algorithm-guide-browser-results.txt) |

预测和推荐目前是有固定规则及可替换接口的统计/评分实现；这些结果证明给定输入的计算和业务行为，演示数据不构成真实场景准确率证明。

截图：[桌面预测](screenshots/algorithm-forecast-desktop.png)、[手机预测](screenshots/algorithm-forecast-mobile.png)、[桌面推荐过滤](screenshots/algorithm-recommendation-desktop.png)、[手机推荐过滤](screenshots/algorithm-recommendation-mobile.png)。
