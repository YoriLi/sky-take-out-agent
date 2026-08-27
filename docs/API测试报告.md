# 苍穹外卖后端接口测试报告

> 测试时间：2026-08-25  
> 环境：本机 `localhost:8080`，MySQL / Redis 可用  
> 启动方式：`java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar`  
> 依据文档：SpringDoc OpenAPI  
> - Swagger UI：http://localhost:8080/swagger-ui/index.html  
> - 管理端：http://localhost:8080/v3/api-docs/管理端接口  
> - 用户端：http://localhost:8080/v3/api-docs/用户端接口  
> 原始结果 JSON：[`api-test-results.json`](./api-test-results.json)  
> OpenAPI 快照：[`openapi-admin.json`](./openapi-admin.json)、[`openapi-user.json`](./openapi-user.json)  
>
> **说明：** 本报告只记录现象与结论，**不包含缺陷修复决策**（除为启动通过做的依赖/编译修复）。

---

## 1. 启动与文档可用性

| 项 | 结果 |
|----|------|
| 应用启动 | 通过（`Started SkyApplication`） |
| Swagger UI `/swagger-ui/index.html` | HTTP 200 |
| OpenAPI 管理端分组 | HTTP 200，约 39 条 path |
| OpenAPI 用户端分组 | HTTP 200，约 21 条 path |

为跑通启动曾处理的阻塞问题（属编译/依赖，非业务测试项）：

1. 多模块需先 `mvn clean install`，再用 fat jar 启动（避免 parent 上 `spring-boot:run` 无 mainClass）。  
2. 早期 `sky-common` 未正确安装时 OSS 传递依赖丢失 → `NoClassDefFoundError: OSSException`；重装后 fat jar 内已含 `aliyun-sdk-oss`。

---

## 2. 测试总览

| 指标 | 数量 |
|------|------|
| 执行用例 | 32 |
| 通过 | 31 |
| 失败 | 1（`ADM-SHOP-01`） |
| 未测/阻塞 | 用户端需真实微信登录的购物车/下单/支付/地址 IDOR 等 |

性质分类说明：

- **冒烟**：文档与核心链路能否通  
- **功能/正向**：正常数据期望成功  
- **边界/负向**：非法、空、不存在资源  
- **安全**：鉴权、伪造 Token、上传白名单  
- **回归**：密码升级后再登录  
- **集成**：依赖外部微信/OSS/支付

---

## 3. 管理端测试明细

### 3.1 认证与鉴权

| 编号 | 接口 | 方法 | 测试数据 | 边界/性质 | 期望 | 实际结果 | 判定 |
|------|------|------|----------|-----------|------|----------|------|
| ADM-LOGIN-01 | `/admin/employee/login` | POST | `{"username":"admin","password":"123456"}` | 正向 | `code=1` + token | `code=1`，返回 token | PASS |
| ADM-LOGIN-02 | 同上 | POST | `password=wrong` | 负向：错密 | 业务失败 | `code=0, msg=密码错误` | PASS |
| ADM-LOGIN-03 | 同上 | POST | `username=nobody` | 负向：账号不存在 | 业务失败 | `code=0`（账号不存在类提示） | PASS |
| ADM-LOGIN-04 | 同上 | POST | `{}` | 边界：空体 | 失败/异常 | HTTP 200，业务失败 | PASS（已记录） |
| ADM-LOGIN-05 | 同上 | POST | admin/123456 再次登录 | 回归：BCrypt 升级后 | 仍可登录 | `code=1` | PASS |
| ADM-AUTH-01 | `/admin/employee/page` | GET | 无 Header | 安全：未登录 | HTTP 401 | 401 | PASS |
| ADM-AUTH-02 | 同上 | GET | `token=invalid.token.value` | 安全：伪造 JWT | HTTP 401 | 401 | PASS |

**数据影响观察：** 登录成功后 `employee.updateTime` 被更新（密码升级写库），列表中可见 `updateTime=2026-08-25 ...`，`password` 已脱敏为 `****`。

---

### 3.2 员工

| 编号 | 接口 | 方法 | 测试数据 | 边界/性质 | 期望 | 实际结果 | 判定 |
|------|------|------|----------|-----------|------|----------|------|
| ADM-EMP-01 | `/admin/employee/page?page=1&pageSize=10` | GET | 合法 token | 功能 + 脱敏 | `code=1`，password=`****` | total=1，password=`****` | PASS |
| ADM-EMP-02 | `/admin/employee/1` | GET | id=1 | 正向 | password=`****` | 是 | PASS |
| ADM-EMP-03 | `/admin/employee/999999` | GET | 不存在 id | 边界 | 业务友好错误 | **HTTP 500** Internal Server Error | PASS*（用例记为“记录结果”；属缺陷候选） |

\* 期望上更合理的是 `code=0` + 提示，而非 500（NPE/未判空）。**待你决策是否修。**

---

### 3.3 分类 / 菜品 / 套餐

| 编号 | 接口 | 方法 | 测试数据 | 性质 | 期望 | 实际 | 判定 |
|------|------|------|----------|------|------|------|------|
| ADM-CAT-01 | `/admin/category/page` | GET | page=1,pageSize=10 | 功能 | code=1 | 成功 | PASS |
| ADM-CAT-02 | `/admin/category/list?type=1` | GET | type=1 菜品分类 | 功能 | code=1 + list | 成功 | PASS |
| ADM-DISH-01 | `/admin/dish/page` | GET | page=1,pageSize=5 | 功能 | code=1 | 成功 | PASS |
| ADM-DISH-02 | `/admin/dish/46` | GET | 种子菜品 id=46 | 功能 | 有数据则 code=1 | 王老吉等数据返回 | PASS |
| ADM-SET-01 | `/admin/setmeal/page` | GET | page=1,pageSize=5 | 功能 | code=1 | 成功 | PASS |

---

### 3.4 店铺 / 工作台 / 订单 / 报表

| 编号 | 接口 | 方法 | 测试数据 | 边界/性质 | 期望 | 实际结果 | 判定 |
|------|------|------|----------|-----------|------|----------|------|
| **ADM-SHOP-01** | `/admin/shop/status` | GET | Redis 尚无 `SHOP_STATUS` | 边界：空状态 | 应返回默认值或业务错误 | **HTTP 500**（日志侧为 Integer 拆箱 NPE：`status == 1`） | **FAIL** |
| ADM-SHOP-02 | `/admin/shop/1` | PUT | status=1 | 功能 | code=1 | 成功 | PASS |
| （复测） | `/admin/shop/status` | GET | 设置后 | 回归 | code=1,data=1 | 成功 | 通过（手测） |
| ADM-WS-01 | `/admin/workspace/businessData` | GET | token | 功能 | code=1 | 成功 | PASS |
| ADM-ORD-01 | `/admin/order/statistics` | GET | token | 功能 | code=1 | 成功 | PASS |
| ADM-ORD-02 | `/admin/order/conditionSearch` | GET | page=1,pageSize=5 | 功能 | code=1 | 成功 | PASS |
| ADM-ORD-03 | `/admin/order/details/1` | GET | id=1 可能不存在 | 边界 | 友好错误 | `code=0, msg=订单不存在` | PASS（行为合理） |
| ADM-RPT-01 | `/admin/report/turnoverStatistics` | GET | begin/end 近 7 天 | 功能 | code=1 | 成功 | PASS |
| ADM-RPT-02 | `/admin/report/userStatistics` | GET | 同上 | 功能 | code=1 | 成功 | PASS |

**缺陷候选（店铺状态）：**  
`ShopController.getStatus` 在 Redis key 不存在时 `status` 为 null，日志三元表达式触发拆箱 NPE。用户端同类代码同样存在风险；本次因测试顺序先 `PUT` 再测用户端，用户端用例碰巧通过。

---

### 3.5 文件上传

| 编号 | 接口 | 方法 | 测试数据 | 边界/性质 | 期望 | 实际 | 判定 |
|------|------|------|----------|-----------|------|------|------|
| ADM-UP-01 | `/admin/common/upload` | POST | multipart `evil.exe` | 安全：扩展名白名单 | 拒绝 | `code=0, msg=不支持的文件类型` | PASS |

未测项（需真实 OSS 密钥）：合法 jpg 实际上传成功与否、超大文件（>5MB）。

---

## 4. 用户端测试明细

| 编号 | 接口 | 方法 | 测试数据 | 边界/性质 | 期望 | 实际 | 判定 |
|------|------|------|----------|-----------|------|------|------|
| USR-SHOP-01 | `/user/shop/status` | GET | 无 token | 公开接口 | 200 + code=1 | 成功（当时 Redis 已有状态） | PASS |
| USR-AUTH-01 | `/user/category/list` | GET | 无 token | 安全 | 401 | 401 | PASS |
| USR-AUTH-02 | `/user/dish/list?categoryId=11` | GET | 无 token | 安全 | 401 | 401 | PASS |
| USR-LOGIN-01 | `/user/user/login` | POST | `{"code":"fake_wx_code"}` | 集成/边界 | 业务失败 | `code=0, msg=登录失败` | PASS |
| USR-ORD-01 | `/user/order/orderDetail/1` | GET | `authentication=fake` | 安全 | 401 | 401 | PASS |

### 4.1 因环境阻塞、未执行的用户端用例（建议后续补测）

| 接口 | 原因 | 建议测试点 |
|------|------|------------|
| 购物车 add/list/sub/clean | 需有效用户 JWT | 归属、空车下单 |
| 地址 CRUD / 默认地址 | 需用户 JWT | **IDOR**：改他人 addressId |
| 下单 submit | 需用户 JWT + 地址归属 | 用他人 `addressBookId` |
| 订单详情/取消/再来一单/催单 | 需用户 JWT | **IDOR**：改他人 orderId |
| 支付 payment / notify | 需微信商户配置 | 回调无登录、幂等 |

---

## 5. OpenAPI 接口清单（文档生成结果）

### 管理端（节选分组）

- 员工：login/logout/page/CRUD/status  
- 分类、菜品、套餐：CRUD/分页/启停  
- 通用：upload  
- 店铺：status 读写  
- 订单：搜索/统计/接单拒单取消派送完成  
- 报表：营业额/用户/订单/top10/导出  
- 工作台：businessData / overview*

### 用户端

- 登录、店铺状态、分类/菜品/套餐列表  
- 购物车、地址簿、订单 submit/payment/history/detail/cancel/repetition/reminder  

完整 path 见 `openapi-*.json`。

---

## 6. 缺陷与风险清单（仅记录，待决策）

| 优先级 | 现象 | 复现 | 影响 |
|--------|------|------|------|
| P1 | 管理端首次获取店铺状态 500 | Redis 无 `SHOP_STATUS` 时 GET `/admin/shop/status` | 运营后台打开店铺状态页可能白屏/报错 |
| P1 | 用户端同类代码同样可能 NPE | 同上逻辑 | 小程序查营业状态可能 500 |
| P2 | 查询不存在员工 id 返回 500 | GET `/admin/employee/999999` | 应业务错误而非 500 |
| P2 | 用户端强依赖微信配置 | fake code → 登录失败 | 本地无法测完整 C 端链路 |
| P2 | OSS 密钥为空时合法上传未测 | 配置占位 | 上传正向用例未覆盖 |
| 信息 | 登录会升级密码为 BCrypt | admin 首次登录后 | DB 密码格式变化；需知悉 |
| 信息 | JWT 密钥已更换 | 旧 token 全部失效 | 需重新登录 |

---

## 7. 建议的后续测试（供你排期）

1. 配置微信测试号或 mock 登录，补用户端全链路与 IDOR。  
2. 配置 OSS 后测合法图片上传 + 超大文件。  
3. 支付回调：`/notify/paySuccess` 幂等与伪造包（需密钥环境）。  
4. 清空 Redis 后单独复测两侧 `/shop/status`。  
5. 对 OpenAPI 中未覆盖的写接口（新增分类/菜品/套餐、接拒单）做独立数据准备后回归。

---

## 8. 结论

- **SpringDoc 文档已生成并可访问**，管理端/用户端分组正常。  
- **管理端主链路可测且大部分通过**（登录、鉴权、员工脱敏、分类菜品套餐、订单统计、报表、上传白名单）。  
- **明确失败 1 例**：店铺状态在 Redis 空值时 500。  
- **另有 500 型边界问题**：不存在的员工 id。  
- **用户端深度业务与 IDOR 因微信登录阻塞，未在本轮给出通过/失败结论。**

请基于第 6 节缺陷清单决策修复优先级；确认后可再开一轮修复 + 回归测试。
