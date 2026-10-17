# 第 1 批接口

统一前缀 `/api/v1`。除登录外携带 `Authorization: Bearer <token>`。通用响应：`{code,message,data,traceId}`，错误不回显密码、手机号和原始请求。

| 方法 | 路径 | 行为/权限 |
|---|---|---|
| POST | `/auth/login` | `{username,password}` → `{token,expiresAt,user}`；错误账号/密码/停用账号统一 401 |
| GET | `/auth/me` | 本人 `id,username,realName,phoneMasked,roles[],sites[]`；家属不返回员工网点授权 |
| POST | `/auth/logout` | 撤销本次会话 |
| PUT | `/auth/profile` | `{realName,phone}`，`phone:null` 保留原号码；不能传 id、role 等其他字段 |
| PUT | `/auth/password` | `{oldPassword,newPassword}`；新密码 12–72 字符且不超过 72 UTF-8 字节；撤销全部会话 |
| GET | `/roles` | 管理员读取四角色列表 |
| GET | `/sites?page=1&size=20` | 管理员全局；食堂/社区仅授权网点；家属 403；返回 `{records,total}` |
| GET | `/sites/{id}` | 资源级网点鉴权；家属不得通过员工授权表绕过角色限制 |
| GET | `/openapi.yaml` | 已登录用户读取本批 OpenAPI 文档 |

分页要求 `page>=1`、`1<=size<=100`。账号无角色、会话过期、账号停用均返回 401。网点越权先返回 403，不向员工泄露未授权网点是否存在。管理员读取不存在网点返回 404。前端角色切换只改变界面，不能改变服务端权限。

已登录用户对已存在路径使用不支持的 HTTP 方法时返回 `405/METHOD_NOT_ALLOWED`，不会错误地返回内部异常。

本地演示例外（用户要求）：只有 `dev` 且无 `prod` 配置中的 `admin`、`canteen`、`family`、`community` 可将新密码设为自己的账号名。其他短密码仍拒绝，普通账号及生产配置继续要求至少 12 字符。

本批按原契约补充了 `POST /auth/logout`，用于实现原 T01 已要求但接口表未列出的退出行为。`/sites` 与 `/roles` 仅提供权限基础所需的读取能力，其管理功能属于第 2 批。
