# 产品完善第一批

已完成搜索、数组字典分页、旧查询竞争保护、关联对象分页搜索、完整上下文选择项加载、预测/推荐计算故障降级、首页公告失败重试、保存后定位及老人饮食偏好归属说明。

算法降级只捕获计算错误：安全校验、资源权限、存储错误继续传播。推荐插件返回非法候选仍被拒绝；计算不可用时只展示已经通过安全核对的候选，标记暂未评分，不伪造推荐曝光。

测试先确认失败后实现，记录：

- [前端复现](enhancement-01-red-web.txt)、[关联选择复现](enhancement-01-red-options.txt)、[后端搜索复现](enhancement-01-red-server.txt)、[预测降级复现](enhancement-01-red-degradation.txt)、[排序降级复现](enhancement-01-red-ranking.txt)。
- Java 全量：算法 5 项 + 后端 91 项，共 96 项通过，见 [结果](enhancement-01-full-server.txt)。
- 前端全量 29 项通过，[结果](enhancement-01-web-tests.txt)；[类型检查](enhancement-01-typecheck.txt)、[构建](enhancement-01-build.txt)通过。
- 桌面/手机浏览器原 8 项中，6 项通过；分页修正后发现新增分类落在后页，补保存后定位，失败的 2 项均通过复测。[首轮](enhancement-01-browser.txt)、[复测](enhancement-01-browser-retest.txt)。

统一脚本整体重启后已生效。历史完整性审查与首次失败记录保留，不将后续钱包等未完成内容计入本批。
