# 05 REST API 接口契约（V1）

## 1. 约定

前缀 `/api/v1`。登录外除公开展示接口均携带 `Authorization: Bearer <token>`。错误码：`VALIDATION_ERROR`(400)、`UNAUTHENTICATED`(401)、`FORBIDDEN`(403)、`NOT_FOUND`(404)、`CONFLICT`(409)、`BUSINESS_RULE_VIOLATION`(422)、`INTERNAL_ERROR`(500)。所有 `/sites/{siteId}` 路径必须校验授权网点。分页 `page,size`。日期 `YYYY-MM-DD`，金额字符串，枚举见 02 文档。下面列出首版全部业务接口，允许内部服务组合实现，但不得遗漏行为。

### 典型请求响应

`POST /orders`：
```json
{"elderId":101,"siteId":1,"mealDate":"2026-10-20","mealType":"LUNCH","deliveryType":"DINE_IN","items":[{"menuItemId":11,"quantity":1}],"recommendationEventId":null}
```
响应（字段示例）：
```json
{"code":"OK","message":"success","data":{"orderId":9001,"orderNo":"MEAL9001","status":"PENDING_PAYMENT","originalAmount":"18.00","subsidyAmount":"5.00","payableAmount":"13.00"},"traceId":"x"}
```

`GET /elders/101/recommendations?siteId=1&date=2026-10-20&mealType=LUNCH` 响应：
```json
{"code":"OK","data":{"items":[{"menuItemId":11,"score":86.5,"reasons":["偏好分类匹配"]}],"excluded":[{"menuItemId":12,"reason":"匹配过敏原"}],"warning":null}}
```

`GET /sites/1/forecasts?startDate=2026-10-20&days=7&mealType=LUNCH` 响应：
```json
{"code":"OK","data":{"algorithm":"weekday_average_v1","points":[{"date":"2026-10-20","count":42,"sampleCount":6,"insufficientData":false,"closed":false}]}}
```

## 2. 完整接口清单

| 模块 | 方法和路径 | 角色 | 输入/结果（关键字段） |
|---|---|---|---|
| 登录 | POST `/auth/login` | 公开 | 账号密码→token/roles |
| 会话 | GET `/auth/me` | 全部 | 个人信息/角色/可访问网点 |
| 账户 | PUT `/auth/profile`, PUT `/auth/password` | 全部 | 个人信息/密码变更 |
| 用户 | GET/POST `/users`; GET/PUT `/users/{id}`; PATCH `/users/{id}/status` | ADMIN | 账号及启停 |
| 权限 | GET `/roles`; PUT `/users/{id}/roles`; PUT `/users/{id}/sites` | ADMIN | 角色/网点授权 |
| 网点 | GET/POST `/sites`; GET/PUT `/sites/{id}`; PATCH `/sites/{id}/status` | ADMIN | 网点、负责人、范围、营业状态 |
| 停餐 | GET/PUT `/sites/{id}/closed-dates` | ADMIN | 指定日期/餐次停餐 |
| 菜品 | GET/POST `/dishes`; GET/PUT `/dishes/{id}`; PATCH `/dishes/{id}/status` | ADMIN(写)、其他授权角色(读) | 菜品/食材/价格/营养/过敏原 |
| 老人档案 | GET/POST `/elders`; GET/PUT `/elders/{id}` | COMMUNITY、受权 FAMILY(受限读/更新) | 基础档案 |
| 健康档案 | GET/PUT `/elders/{id}/health` | COMMUNITY、关联 FAMILY | 疾病/过敏原/禁忌/偏好 |
| 绑定 | POST `/family/bindings`; GET `/family/bindings`; GET `/bindings/pending`; POST `/bindings/{id}/approve`; POST `/bindings/{id}/reject` | FAMILY(申请/自查)、COMMUNITY(审核) | 关联证明/处理结果 |
| 补贴申请 | POST `/subsidy-applications`; GET `/subsidy-applications`; GET `/subsidy-applications/{id}` | FAMILY/COMMUNITY | 申请/查询 |
| 补贴审批 | POST `/subsidy-applications/{id}/approve`; POST `/subsidy-applications/{id}/reject` | COMMUNITY | 审批意见/状态 |
| 菜谱 | GET `/sites/{siteId}/menus?date=...`; POST `/sites/{siteId}/menus`; PUT `/sites/{siteId}/menus/{id}`; POST `/sites/{siteId}/menus/{id}/submit` | CANTEEN | 菜谱、份量、可售数量 |
| 菜谱确认 | POST `/sites/{siteId}/menus/{id}/publish`; PATCH `/sites/{siteId}/menu-items/{id}/availability` | COMMUNITY | 上架/临时下架 |
| 推荐 | GET `/elders/{id}/recommendations?siteId&date&mealType`; GET `/recommendation-config`; PUT `/recommendation-config` | FAMILY/COMMUNITY(前者); ADMIN(配置) | 候选及过滤原因、权重、疾病规则 |
| 算法规则 | GET/POST/PUT/DELETE `/diet-rules[/{id}]`; GET/POST/PUT/DELETE `/allergens[/{id}]` | ADMIN | 禁忌规则/过敏原字典 |
| 订单 | POST `/orders`; GET `/orders`; GET `/orders/{id}` | FAMILY/COMMUNITY/网站授权工作人员(只读) | 订单创建/详情/列表；操作员下单带 `proxyOperatorId` 由后端记录 |
| 周期代订 | POST `/proxy-orders/recurring` | COMMUNITY | elderId、dateFrom、dateTo、mealType、item 规则；逐日订单结果 |
| 取消 | POST `/orders/{id}/cancel` | FAMILY/COMMUNITY | 未支付取消或已支付取消申请 |
| 出餐 | POST `/orders/{id}/start-preparing`; POST `/orders/{id}/ready` | CANTEEN | 更新状态 |
| 凭证 | GET `/orders/{id}/pickup-voucher` | FAMILY/COMMUNITY(代办授权) | 电子核销凭证 |
| 支付 | GET `/payment/channels`; POST `/orders/{id}/payments`; GET `/orders/{id}/payments` | FAMILY/COMMUNITY | 支付渠道/支付状态及账单 |
| 模拟支付 | POST `/dev/payments/{paymentId}/simulate-success` | 仅 dev/test | 后端模拟回调，生产禁用 |
| 退款申请 | GET `/refunds`; GET `/refunds/{id}`; POST `/refunds/{id}/approve`; POST `/refunds/{id}/reject`; POST `/refunds/{id}/retry` | COMMUNITY | 申请列表/审核/重试 |
| 核销 | POST `/redemptions/verify`; POST `/redemptions/confirm` | CANTEEN/COMMUNITY | voucher 或订单号、老人核验信息；完成状态 |
| 配送 | GET `/sites/{siteId}/delivery-list`; PATCH `/orders/{id}/delivery-status` | CANTEEN/COMMUNITY | 列表/交付状态（必要禁忌脱敏） |
| 备餐 | GET `/sites/{siteId}/preparation-list`; PUT `/sites/{siteId}/preparation-list` | CANTEEN | 汇总订单+预测及人工调整；可打印页面 |
| 预测 | GET `/sites/{siteId}/forecasts?startDate&days&mealType`; GET `/sites/{siteId}/attendance-history` | CANTEEN/ADMIN | 人数曲线/历史、生成时间 |
| 菜品评价 | POST `/reviews`; GET `/reviews`; GET `/sites/{siteId}/reviews` | FAMILY(写/本人查询)、CANTEEN(网点读) | 评分文字图片 |
| 工单 | POST `/tickets`; GET `/tickets`; GET `/tickets/{id}`; POST `/tickets/{id}/reply`; PATCH `/tickets/{id}/status` | FAMILY/COMMUNITY/ADMIN(升级工单) | 工单/进展/回复 |
| 公告 | GET `/notices`; GET `/notices/{id}`; POST `/notices`; PUT `/notices/{id}`; POST `/notices/{id}/publish`; POST `/notices/{id}/revoke` | 全部可读授权公告; ADMIN(写) | 公告/接收范围 |
| 膳食报告 | GET `/elders/{id}/nutrition-report?period=week&startDate=...`; GET `/elders/{id}/meal-history` | FAMILY/COMMUNITY | 统计营养和核销餐食 |
| 仪表盘 | GET `/dashboard/overview`; GET `/dashboard/orders-trend`; GET `/dashboard/revenue-subsidy`; GET `/dashboard/attendance-forecast`; GET `/dashboard/nutrition-stats`; GET `/dashboard/recommendation-stats` | ADMIN | 日期/网点筛选、聚合指标 |
| 审计 | GET `/audit-logs` | ADMIN | 关键审计记录分页 |
| 文件 | POST `/files/upload?purpose=...`; GET `/files/{id}` | 按业务授权 | 申请材料/菜品图/评价图/工单图；鉴权下载 |

## 3. DTO 字段说明

- Elder: `id,name,birthDate,phoneMasked,siteId,status`；Health: `diseaseCodes[],allergenIds[],avoidIngredientNames[],preferredCategoryIds[],dislikedCategoryIds[]`。
- Dish: `id,name,categoryId,description,ingredients[],weightGrams,originalPrice,elderPrice,caloriesKcal,proteinGrams,fatGrams,carbGrams,sodiumMg,allergenIds[],imageFileId,status`。
- DailyMenu: `id,siteId,serviceDate,mealType,status,items[{id,dishId,servingWeightGrams,nutritionOverride,plannedQty,availability}]`。
- Order: `id,orderNo,elderId,siteId,mealDate,mealType,deliveryType,deliveryAddress,contactName,contactPhone,operatorId,status,originalAmount,subsidyAmount,payableAmount,items[{dishId,nameSnapshot,qty,unitPrice,unitSubsidy,nutritionSnapshot}]`。
- Refund: `id,orderId,reason,status,reviewedBy,reviewedAt,amount,transactionNo`。
- Notice: `id,title,type,content,status,receiveRoles[],siteIds[],publishAt,expireAt`。
- Ticket: `id,createdBy,siteId,orderId,type,content,status,priority,firstReplyAt,updatedAt,replies[]`。

## 4. 接口测试最低要求

每个接口必须至少测试：正常请求、未认证、错误角色、跨网点/跨老人、无效字段、无效状态；订单/核销/退款额外测试并发与重复提交。接口文档可以用 Springdoc OpenAPI 生成，但以上是业务契约，不得被生成文档替代。
