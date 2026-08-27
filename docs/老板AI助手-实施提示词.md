# 任务提示词：苍穹外卖管理端「老板 AI 助手」

> 本文件是交付给实施 Agent 的**完整任务书**。仓库事实已按**当前工作区源码**核对。
> 实施时仍以源码方法签名为准；若与本文件冲突，以源码为准，并在最终报告写明「文档第 X 条与源码不符」。
> 与本文件冲突的任何先验知识（含你对 LangChain4j / Vue / Spring Boot 的记忆）以本文件为准（冲突源码时除外）。

---

## 0. 一句话目标与完成定义

**目标**：在现有苍穹外卖仓库上**增量**接入一个「老板 AI 助手」——管理员在管理端用中文对话，助手通过调用真实业务 Service 完成查订单、改订单状态、看报表、看/改营业状态，并以 SSE 流式展示推理过程中的**工具名、参数、结果**。

**完成定义（DoD，逐条自检，全绿才算完成）**：

| # | 验收项 | 判定方式 |
|---|--------|----------|
| D1 | 后端可编译、可启动 | `mvn -pl sky-server -am clean package -DskipTests` 成功；`java -jar` 启动无异常 |
| D2 | **未配 `SKY_AGENT_API_KEY` 时后端照常启动**，原有登录/菜品/订单接口行为不变 | 启动日志无 `BeanCreationException`；旧接口手测通过 |
| D3 | 未登录访问 `/admin/agent/stream` 返回 **401** | `curl -i` 无 token |
| D4 | 未配 key 时，带 token 请求返回一条 `{"type":"error"}` 中文事件后正常关闭，**不返回 500 堆栈页** | `curl -N` 观察 |
| D5 | 配好 key 后，「今天营业额怎么样」一轮至少产生 `tool_call` + `tool_result` + `text` + `end` 四类事件 | `curl -N` 观察 |
| D6 | 只说「接单」不给单号时，助手**追问**或**先查询**，绝不臆造单号调用 `confirmOrder` | 对话观察 |
| D7 | 侧边栏新增「AI 助手」菜单，`/#/agent` 可打开，发送后输入框禁用、`end` 后恢复；工具卡片由 loading 变完成 | 浏览器手测 |
| D8 | 未越界修改存量文件（见 §5 白名单） | `git diff --stat` 逐行核对 |

**非目标（明确不做）**：RAG/向量库、多轮工具并行编排、OSS/Excel 导出接入 LLM、微信支付相关能力、把项目升级到 Vue3 / Spring Boot 3 / MyBatis-Plus / Element Plus / Pinia。

---

## 1. 环境预检（**第一步，先做完再写代码**）

本仓库的 CI/沙箱环境**默认不具备**构建条件，先跑通预检，缺什么补什么：

```bash
java -version     # 需要 JDK（编译目标是 1.8，见 §2.1）
mvn -v            # 可能未安装 → 需自行安装 Maven 3.6+
node -v           # 见下方 Node 版本说明
ls project-rjwm-admin-vue-ts/node_modules   # 大概率不存在，需要先 npm install
```

**Node 版本（重要）**：前端是 `@vue/cli-service@3` + `webpack@4`，并且 `devDependencies` 里有原生模块 `fibers@^4.0.2`。

- **首选 Node 14 / 16**：可直接 `npm install` + `npm run serve`。
- 若只有 Node 17+：`webpack4` 会报 `ERR_OSSL_EVP_UNSUPPORTED`，需 `export NODE_OPTIONS=--openssl-legacy-provider`；且 `fibers` 原生编译大概率失败，可尝试 `npm install --ignore-scripts`。**这只是兜底，不要为此升级 Vue CLI / webpack**。
- 如果前端确实无法在当前环境跑起来：**照常把前端代码写完并保证 TS/ESLint 语法正确**，在最终报告里明确写出「前端未能运行时验证，原因：<具体报错>」，不要假装通过。

**退出标准**：能 `mvn` 编译后端；前端要么能 `npm run serve`，要么已书面记录阻塞原因。

---

## 2. 已核实的仓库事实（**权威，以源码签名为准**）

### 2.1 后端 `sky-take-out`

| 事实 | 值 | 证据 |
|------|-----|------|
| 模块 | `sky-common` / `sky-pojo` / `sky-server`，父 `com.sky:sky-take-out:1.0-SNAPSHOT` | `sky-take-out/pom.xml` |
| Spring Boot | **2.7.3** | 父 POM |
| **Java 编译级别** | **1.8**（全仓库未声明 `java.version`，继承 `spring-boot-starter-parent:2.7.3` 默认 `1.8`） | 无覆盖声明 |
| ORM | **MyBatis** XML + PageHelper，**不是** MyBatis-Plus | `sky-server/pom.xml`、`resources/mapper/*.xml` |
| 统一响应 | `com.sky.result.Result<T>`：`code=1` 成功，`code=0` 失败；字段 `code/msg/data` | `sky-common/.../result/Result.java` |
| 管理端前缀 | 全部 `/admin/xxx`（category/common/dish/employee/order/report/setmeal/shop/workspace） | Controller `@RequestMapping` |
| `server.port` | **8080**，**无 `context-path`** | `application.yml` |
| JWT 头名 | `sky.jwt.admin-token-name: token` → 请求头就叫 **`token`** | `application.yml` |
| 拦截器 | `JwtTokenAdminInterceptor` 解析 JWT 取 claim `empId`，`BaseContext.setCurrentId(empId)`；失败 `response.setStatus(401); return false`（**无响应体**） | `interceptor/JwtTokenAdminInterceptor.java` |
| 拦截范围 | `addPathPatterns("/admin/**").excludePathPatterns("/admin/employee/login")` | `config/WebMvcConfiguration.java` |
| `BaseContext` API | `setCurrentId(Long)` / `getCurrentId()` / `removeCurrentId()` | `sky-common/.../context/BaseContext.java` |
| 全局异常 | `GlobalExceptionHandler` 只处理 `BaseException` 和 `SQLIntegrityConstraintViolationException`，**没有兜底 `Exception` handler** | `handler/GlobalExceptionHandler.java` |
| 异常体系 | `com.sky.exception.BaseException extends RuntimeException`；子类含 `OrderBusinessException` 等 | `sky-common/.../exception/` |
| 订单状态常量 | `Orders.PENDING_PAYMENT=1, TO_BE_CONFIRMED=2, CONFIRMED=3, DELIVERY_IN_PROGRESS=4, COMPLETED=5, CANCELLED=6`；支付状态 `UN_PAID=0, PAID=1, REFUND=2` | `sky-pojo/.../entity/Orders.java` |
| 店铺营业状态 | **已有** `com.sky.service.ShopService` / `ShopServiceImpl`（Redis `SHOP_STATUS`，0 打烊 / 1 营业；未命中默认打烊并回写） | `service/ShopService.java`、`service/impl/ShopServiceImpl.java`；admin/user `ShopController` 均委托该 Service |
| 登录密码 | `sky.sql` 中 admin 为 **MD5(`123456`)**；`EmployeeServiceImpl` 收**明文**，兼容 BCrypt/MD5/明文，成功后可升级 BCrypt | `database/sky.sql`、`EmployeeServiceImpl.java` |
| 已有依赖 | web / mybatis-spring-boot-starter / druid / pagehelper / data-redis / cache / websocket / fastjson / lombok / springdoc-openapi-ui / poi | `sky-server/pom.xml` |
| 已有流式技术 | **只有 WebSocket**（`/ws/{sid}`），**没有任何 SSE / AI / LangChain 代码** | 全仓库检索 |

**`/admin/agent/**` 会被 `/admin/**` 自动拦截 → 不需要改 `WebMvcConfiguration`。**

### 2.2 可复用 Service 的**精确签名**（照抄，勿凭记忆）

```java
// com.sky.service.OrderService
PageResult          conditionSearch(OrdersPageQueryDTO dto);
OrderVO             details(Long id);
OrderStatisticsVO   statistics();
void                confirm(OrdersConfirmDTO dto);                 // ⚠ 是 ConfirmDTO，不是 CancelDTO；一般只塞 id
void                rejection(OrdersRejectionDTO dto) throws Exception;  // ⚠ 受检异常
void                cancel(OrdersCancelDTO dto)       throws Exception;  // ⚠ 受检异常
void                delivery(Long id);
void                complete(Long id);

// com.sky.service.ReportService
TurnoverReportVO    getTurnover(LocalDate begin, LocalDate end);
UserReportVO        getUserStatistics(LocalDate begin, LocalDate end);
OrderReportVO       getOrderStatistics(LocalDate begin, LocalDate end);
SalesTop10ReportVO  getSalesTop10(LocalDate begin, LocalDate end);
// void exportBusinessData(HttpServletResponse) —— ⛔ 禁止暴露给 LLM

// com.sky.service.WorkspaceService   ⚠ 注意是 Workspace（小写 s），Controller 才叫 WorkSpaceController
BusinessDataVO      getBusinessData(LocalDateTime begin, LocalDateTime end);
OrderOverViewVO     getOrderOverView();
DishOverViewVO      getDishOverView();
SetmealOverViewVO   getSetmealOverView();

// com.sky.service.ShopService   ✅ 当前工作区存在，ShopSkill 必须用它，不要直接 RedisTemplate
Integer             getStatus();
void                setStatus(Integer status);   // 仅允许 0 / 1
```

**DTO / VO 字段（拼摘要要用）**：

- `OrdersPageQueryDTO`：`int page, int pageSize, String number, String phone, Integer status, LocalDateTime beginTime, LocalDateTime endTime, Long userId`
- `PageResult`：`long total, List records`
- `OrdersConfirmDTO`：`Long id, Integer status`（接单通常只设 `id`）
- `OrdersCancelDTO`：`Long id, String cancelReason`
- `OrdersRejectionDTO`：`Long id, String rejectionReason`
- `OrderStatisticsVO`：`Integer toBeConfirmed, confirmed, deliveryInProgress`
- `OrderVO extends Orders`：额外 `String orderDishes, List<OrderDetail> orderDetailList`
- `TurnoverReportVO`：`String dateList, String turnoverList`（**逗号拼接的字符串，不是集合**）
- `UserReportVO`：`String dateList, totalUserList, newUserList`
- `OrderReportVO`：`String dateList, orderCountList, validOrderCountList; Integer totalOrderCount, validOrderCount; Double orderCompletionRate`
- `SalesTop10ReportVO`：`String nameList, numberList`
- `BusinessDataVO`：`Double turnover; Integer validOrderCount; Double orderCompletionRate; Double unitPrice; Integer newUsers`
- `OrderOverViewVO`：`Integer waitingOrders, deliveredOrders, completedOrders, cancelledOrders, allOrders`
- `DishOverViewVO` / `SetmealOverViewVO`：`Integer sold, discontinued`

### 2.3 前端 `project-rjwm-admin-vue-ts`

| 事实 | 值 | 证据 |
|------|-----|------|
| 技术栈 | Vue **2.6.10**、vue-router 3.x、Vuex 3.x、`vuex-module-decorators`、Element UI **2.x**（lock 常见 2.13.0）、`vue-property-decorator`、axios 0.19、TypeScript **3.6.2** | `package.json` / `package-lock.json` |
| dev 端口 | **8081** | `vue.config.js` |
| 代理 | `/api` → `process.env.VUE_APP_URL`，`pathRewrite: {'^/api': ''}` | `vue.config.js` |
| `VUE_APP_URL`(dev) | `http://localhost:8080/admin` | `.env.development` |
| `VUE_APP_BASE_API` | `/api` | `.env.development` |
| ⇒ 结论 | 前端 `POST /api/agent/stream` = 后端 `POST http://localhost:8080/admin/agent/stream` ✅ | — |
| 路由 | 单文件 `src/router.ts`，侧栏 = `path:"/"`(Layout) 的 `children` | `src/router.ts` |
| 侧栏渲染 | `SidebarItem.vue`：`<i class="iconfont" :class="meta.icon" />` | `layout/components/Sidebar/SidebarItem.vue` |
| **图标真相** | `meta.icon` 是 **iconfont CSS 类名**。工作台特例是 `dashboard`；其它菜单多为 `icon-order` / `icon-category` / `icon-inform` 等（带 `icon-` 前缀）。**不需要** `npm run svg` | `src/styles/icon/iconfont.css`、`router.ts` |
| 权限 | `permission.ts` 只校验 cookie `token` 是否存在，**无角色过滤、无 addRoutes** ⇒ 加一条 child route 即可出现在侧栏 | `src/permission.ts` |
| token 存储 | `js-cookie`，key = `'token'`，`getToken()` 在 `src/utils/cookies.ts` | — |
| axios 去重 | `requestOptimize.ts` 用 `md5(url+method+body)` 做 in-flight 去重，**会取消重复 POST** | `utils/requestOptimize.ts` |
| tsconfig | `strict: false`，`target: esnext`，**`exclude: ["src/**/*.vue"]`** | `tsconfig.json` |
| Element UI | `main.ts` 已 `Vue.use(ElementUI)`，`this.$message` 全局可用 | `src/main.ts` |
| 关键 lint 规则 | `vue/match-component-file-name: 'error'` ⇒ **组件 `name` 必须与文件名一致**；`eqeqeq`/`semi`/`indent` 已关闭 | `.eslintrc.js` |
| 现有 fetch/SSE | **无**。全部走 axios；仅 Navbar 用了 WebSocket | 全仓库检索 |

---

## 3. ⚠ 易错点（已核实，**按更正后的执行**）

实施时若你「记得」下面左列的说法，请一律以右列为准：

| # | 错误/过时说法 | 核实后的事实 | 影响 |
|---|---------------|-------------|------|
| E1 | 不存在 `ShopService`，只能注 `RedisTemplate` | **当前工作区已有** `ShopService` / `ShopServiceImpl`，Controller 已委托 | `ShopSkill` **注入 `ShopService`**，不要在 Skill 里直接写 Redis |
| E2 | Java 是 11/17，langchain4j 随便选 0.36+ | 编译级别是 **1.8**。langchain4j **0.36.0+ 最低 JDK 17** | 锁 **0.35.0**（Java 8 字节码上限）；编译若失败再按 §12 处理，**不要升 Boot** |
| E3 | `TokenStream` 有 `onToolExecuted` | 0.35.0 的 `TokenStream` **确定没有** `onToolExecuted`（仅有 `onRetrieved / onNext / onComplete / onError / ...`） | `tool_call` / `tool_result` **必须由 Skill 方法自己发**（§4.2），不是可选兜底 |
| E4 | 工具必须跑在 Tomcat 请求线程 | SSE + `TokenStream` 下工具常在 OkHttp 回调线程 | 请求线程捕获 `empId` → 传入 Skill → 工具入口 `BaseContext.setCurrentId(empId)`，`finally` `removeCurrentId()` |
| E5 | `confirm` 用 `OrdersCancelDTO` | **`confirm(OrdersConfirmDTO)`**；Javadoc 若仍写 CancelDTO 以**方法签名**为准。`rejection`/`cancel` 仍 `throws Exception` | Skill 接单只塞 `id`；拒单/取消必须 `catch (Exception e)` |
| E6 | 路由写 `icon: "inform"` 或去跑 `npm run svg` | 侧栏用 iconfont 类名；AI 助手写 **`icon: "icon-inform"`** | **不要**动 `src/icons/`，**不要**跑 `npm run svg` |

**同时确认为真的两条**：

- ✅ `OrderController.details` 与 `complete` 确实**漏了 `@PathVariable`**（`delivery` 有而它们没有）⇒ **绝不用 RestTemplate 自调 HTTP，一律直调 Service 传 `Long id`**。本任务**不要**顺手修这个 Controller bug。
- ✅ `OrderServiceImpl` 中管理端方法 `conditionSearch/statistics/confirm/rejection/cancel/delivery/complete` **多数不读 `BaseContext`**；读它的主要是用户端方法。E4 的线程问题未必立刻炸，但 `AutoFillAspect` 等仍依赖 ThreadLocal，**仍然必须**按 E4 传播 `empId`。

---

## 4. 架构与关键设计决策

### 4.1 调用链（同进程三层，禁止 HTTP 套娃）

```
Vue2 页面 AgentChat.vue
   fetch(POST /api/agent/stream, headers:{token})     ← 原生 fetch，禁用 EventSource（带不了请求头）
      → webpack devServer 代理，去掉 /api
         → POST /admin/agent/stream            （被 JwtTokenAdminInterceptor 校验，401 由它兜住）
            → AgentController                 （只做参数校验 + 建 SseEmitter，零 AI 逻辑）
               → AgentChatService              （LangChain4j AiServices + ChatMemory）
                  → @Tool Skill 实例           （直调下面这层）
                     → OrderService / ReportService / WorkspaceService / ShopService
```

### 4.2 **核心设计决策：每请求构造 AiService + Skill 实例**（务必照做）

因为 E3（拿不到工具回调）和 E4（工具在别的线程跑），最稳妥的做法是：

> **不要**把 Skill 做成被注入到单例 AiService 里的单例 `@Component`。
> **每次 SSE 请求**新建一组 Skill 实例，把 `AgentEventSink`（封装 `SseEmitter`）和 `empId` 通过**构造器**传进去；再用 `AiServices.builder().tools(skills)` 现场构造 AiService。

这样一次性解决三个问题：① 工具能直接往本次请求的 SSE 里写 `tool_call`/`tool_result`；② `empId` 天然可用；③ 不同请求互不串扰。

**两条硬约束**（否则工具会静默不注册）：

1. langchain4j 用 `objectWithTools.getClass().getDeclaredMethods()` 扫描工具 ⇒ **`@Tool` 方法必须声明在具体类上，不能靠继承父类拿到**。公共逻辑只能放在被子类调用的 `protected` 辅助方法里。
2. **Skill 实例不能是 Spring AOP / CGLIB 代理对象**。用 `new` 构造，正好规避。

参考骨架（可自行调整命名，但保留语义）：

```java
// com.sky.agent.sse.AgentEventSink —— 串行化写 SSE，幂等关闭
public class AgentEventSink {
    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    public synchronized void text(String delta) { ... }
    public synchronized void toolCall(String name, Map<String, Object> args) { ... }
    public synchronized void toolResult(String name, String result) { ... } // 截断到 2KB
    public synchronized void end() { ... }   // 发 end 后置 closed
    public synchronized void error(String userMessage) { ... } // 发 error 后置 closed
}

// com.sky.agent.skill.AbstractSkill —— 只放辅助方法，不放 @Tool
public abstract class AbstractSkill {
    protected final AgentEventSink sink;
    protected final Long empId;

    /** 统一：发 tool_call → 绑定 empId → 执行 → 异常转中文 → 截断 → 发 tool_result */
    protected String invoke(String toolName, Map<String, Object> args, Callable<String> body) {
        sink.toolCall(toolName, args);
        BaseContext.setCurrentId(empId);
        String result;
        try {
            result = body.call();
        } catch (BaseException e) {
            result = "操作失败：" + e.getMessage();
        } catch (Exception e) {                  // 含 rejection/cancel 的受检异常
            log.error("[agent] tool {} 执行异常", toolName, e);
            result = "操作失败：系统异常，请稍后重试";
        } finally {
            BaseContext.removeCurrentId();
        }
        result = truncate(result, 2000);
        sink.toolResult(toolName, result);
        return result;
    }
}

// com.sky.agent.skill.OrderSkill —— @Tool 必须 declare 在这里
public class OrderSkill extends AbstractSkill {
    private final OrderService orderService;
    @Tool("按订单号/手机号/状态分页查询订单。用户想找某笔或某类订单时使用。")
    public String searchOrders(@P("订单号，可空") String number, ...) {
        return invoke("searchOrders", argsMap, () -> { ... });
    }
}
```

### 4.3 模型与配置

`sky-server/pom.xml` 增加（**只加这两个**，并写注释说明版本原因）：

```xml
<!-- LangChain4j：0.35.0 是最后一个 Java 8 字节码版本；0.36.0+ 最低要求 JDK 17，本项目编译级别为 1.8，不可升级 -->
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j</artifactId>
  <version>0.35.0</version>
</dependency>
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-open-ai</artifactId>
  <version>0.35.0</version>
</dependency>
```

> 传递依赖会引入 `dev.ai4j:openai4j`（Retrofit/OkHttp/Jackson）等。若与 Spring Boot 2.7 托管的 Jackson 冲突导致 `NoSuchMethodError`，**优先排除 openai4j 里的 jackson 传递依赖**沿用 Boot 托管版本，不要反向升级 Boot。

`application.yml` 追加（**密钥只走环境变量，禁止硬编码**）：

```yaml
sky:
  agent:
    api-key: ${SKY_AGENT_API_KEY:}
    base-url: ${SKY_AGENT_BASE_URL:https://api.deepseek.com}
    model: ${SKY_AGENT_MODEL:deepseek-chat}
    timeout-seconds: ${SKY_AGENT_TIMEOUT:120}
    memory-max-messages: ${SKY_AGENT_MEMORY_MAX:20}
```

`AgentProperties`（`@ConfigurationProperties(prefix="sky.agent")`）+ `AgentConfig`。

**启动韧性（D2 硬要求）**：`api-key` 为空时**绝不能**让 Bean 创建失败。

> ⚠ 不要只靠 `@ConditionalOnProperty(prefix="sky.agent", name="api-key")`：由于 yml 里写了默认值 `${SKY_AGENT_API_KEY:}`，该属性**始终存在**（值为空串），而 `@ConditionalOnProperty` 在未指定 `havingValue` 时只把 `"false"` 当作不匹配，空串仍会匹配 ⇒ 条件照样成立，Bean 照样会被创建。

推荐做法：在 `AgentChatService` 里**懒加载**模型（首次使用时才 build），并在入口用 `StringUtils.hasText(properties.getApiKey())` 判空。`AgentController` 检测到不可用时**立刻发一条 `error` 事件**（中文提示「未配置环境变量 SKY_AGENT_API_KEY」）并 `complete()`，不抛异常。

用 `OpenAiStreamingChatModel`（或 0.35.0 等价 Builder）：`baseUrl` / `apiKey` / `modelName`=`deepseek-chat` / `timeout(Duration)` 等。P3 阶段可先用非流式 `OpenAiChatModel` 验证 tool calling。

### 4.4 记忆

- `ChatMemoryProvider`：key 取 `conversationId`（默认为 `empId` 字符串），value = `MessageWindowChatMemory.withMaxMessages(memoryMaxMessages)`，存在**单例** `ConcurrentHashMap` 里（记忆要跨请求存活）。
- `empId` 为空（未登录）时**不建记忆**，直接发 `error`。
- 用 `AiServices.builder().systemMessageProvider(...)` 注入系统提示词。

**系统提示词要点**：你是苍穹外卖商家后台助手，只服务已登录管理员；查任何数据必须调用工具，**严禁编造订单号、金额、统计数字**；用户未提供订单号就要求改状态时，先追问或先调 `searchOrders`，**不得臆造 id**；拒单/取消必须带原因，没有就追问；不回答微信小程序、支付密钥、服务器配置等问题；始终用简体中文，回答简洁。

---

## 5. 隔离边界（越界即不合格）

**允许新建**（放开写）：

```
sky-take-out/sky-server/src/main/java/com/sky/agent/
├── config/     AgentProperties.java, AgentConfig.java
├── controller/ AgentController.java
├── dto/        AgentChatRequest.java
├── sse/        AgentEventSink.java, AgentEvent.java（或直接用 Map/JSON）
├── memory/     ChatMemoryStore.java
├── service/    AgentChatService.java, AgentAssistant.java（AiService 接口）
└── skill/      AbstractSkill.java, OrderSkill.java, ReportSkill.java,
                WorkspaceSkill.java, ShopSkill.java, SkillFactory.java

project-rjwm-admin-vue-ts/src/agent/
├── views/AgentChat.vue
├── sse/agentStream.ts
└── types.ts

docs/  （使用说明，可另写一篇短文档）
```

**允许修改的存量文件（仅此 3 个，且只能增量追加）**：

1. `sky-take-out/sky-server/pom.xml` —— 只加 §4.3 两个依赖
2. `sky-take-out/sky-server/src/main/resources/application.yml` —— 只加 `sky.agent.*` 段
3. `project-rjwm-admin-vue-ts/src/router.ts` —— 只在 Layout `children` 里加一条

**明令禁止**：

- ❌ 改 `WebMvcConfiguration`（`/admin/**` 已覆盖 `/admin/agent/**`）
- ❌ 改任何现有 Controller / Service / Mapper / XML 的业务逻辑来「迁就 Agent」（包括**不要**顺手给 `details`/`complete` 补 `@PathVariable`）
- ❌ 改 `sky-pojo` 里的实体/DTO/VO
- ❌ 在 `sky-common` / `sky-pojo` 的 pom 加依赖
- ❌ 引入 Redux / Zustand / Pinia / 新 Vuex module；会话状态放在 `AgentChat.vue` 的组件 `data` 里
- ❌ 引入 Markdown 重型前端库；纯文本 + `white-space: pre-wrap` 即可
- ❌ Agent 内部再用 `RestTemplate`/HTTP 调 `/admin/**`
- ❌ 新增第四层「Skill REST 接口」（除非另做 `@Profile("test")` 的调试接口）
- ❌ 把项目升级到 Vue3 / Spring Boot 3 / MyBatis-Plus / Element Plus

---

## 6. 分阶段实施计划（**按序执行，每阶段过了退出标准再进下一阶段**）

> 每完成一个阶段就 **commit 一次**（仅当用户明确要求提交时才 commit；否则在最终报告列出建议 commit 点）。commit message 写清阶段号与内容。

### P0 · 预检与基线
- 做完 §1 预检；`git status` 确认工作区；先跑一次 `mvn -pl sky-server -am clean package -DskipTests` 记录**改动前**基线。
- **退出标准**：知道基线能否编译（若基线本就失败，先记录，不要背锅）。

### P1 · 依赖 + 配置 + Properties
- 改 `pom.xml`、`application.yml`，新建 `AgentProperties`。
- **退出标准**：`mvn -pl sky-server -am clean package -DskipTests` 通过；依赖树中出现 langchain4j 0.35.0 且无致命冲突。

### P2 · Skill 层（**先不接 LLM**）
- 实现 `AbstractSkill` + 4 个 Skill（§7），`AgentEventSink` 先允许 no-op。
- 写一个 `@SpringBootTest` 或临时验证，直接 `new OrderSkill(...).searchOrders(...)` 等，验证真实数据、异常转中文、列表截断。
- **退出标准**：至少 `searchOrders`、`countOrdersByStatus`、`getTurnoverReport`（或等价）、`getTodayBusinessData`、`getShopStatus` 跑出真实结果字符串。

### P3 · AiService + 记忆（**先非流式**）
- 先用 `OpenAiChatModel`（非流式）+ `AiServices` 跑通「今天营业额怎么样」。
- **退出标准**：控制台能看到工具被调用 + 一段中文回答。DeepSeek tool calling 不稳时先调提示词和 `@Tool` 描述，**不要**改架构。

### P4 · SSE + TokenStream
- `AgentController` + `AgentEventSink`，映射 5 种事件（§8）。`SseEmitter` 超时 ≥ 120s。
- **退出标准**：`curl -N` 能看到完整事件序列；断言 D3 / D4 / D5。

### P5 · 前端
- `agentStream.ts` + `AgentChat.vue` + `router.ts` 一行增量。
- **退出标准**：D7 通过（或按 §1 记录阻塞原因）。

### P6 · 自测 + 越界审计 + 文档
- 跑完 §10 测试矩阵。
- `git diff --stat` 核对 §5 白名单，**越界立即回滚该文件**。
- 写使用说明（§11）。
- **退出标准**：§0 的 D1–D8 全绿，或对未通过项给出明确原因。

---

## 7. Skill 清单（全部第一期必做）

**通用规则**：

- 包 `com.sky.agent.skill`；每个 public 工具方法都要 `@Tool("中文说明 + 何时使用")`，每个参数都要 `@P("含义 + 是否可空 + 格式")`。
- **参数一律用 `String`**（LLM 传参不可靠），Skill 内部再 `Long.parseLong` / `LocalDate.parse`，解析失败返回友好中文，**绝不抛 NPE / NumberFormatException**。
- 日期格式统一 `yyyy-MM-dd`。
- **列表结果最多 10 条**，每条：订单号、状态（中文）、金额、手机号、下单时间；末尾附「共 N 条，仅显示前 10 条」。
- 单个工具返回值**截断到 2000 字符以内**。

### OrderSkill（注入 `OrderService`）

| 工具方法 | 委托 | 备注 |
|---------|------|------|
| `searchOrders(number, phone, status, page, pageSize)` | `conditionSearch(OrdersPageQueryDTO)` | `page` 默认 1，`pageSize` 默认 10 且**上限 10**；`status` 解析为 1–6，非法则忽略并说明 |
| `getOrderDetail(orderId)` | `details(Long)` | 摘要输出，含菜品明细但要截断 |
| `countOrdersByStatus()` | `statistics()` | 输出待接单/已接单/派送中 |
| `confirmOrder(orderId)` | `confirm(OrdersConfirmDTO)` | ⚠ 只塞 `id` |
| `rejectOrder(orderId, reason)` | `rejection(OrdersRejectionDTO)` | `reason` 必填；⚠ `throws Exception` |
| `cancelOrder(orderId, reason)` | `cancel(OrdersCancelDTO)` | 塞 `cancelReason`；⚠ `throws Exception` |
| `deliverOrder(orderId)` | `delivery(Long)` | — |
| `completeOrder(orderId)` | `complete(Long)` | — |

状态机不合法时抛 `OrderBusinessException`，由 `AbstractSkill.invoke` 捕获并把 `e.getMessage()` 返回给模型。

### ReportSkill（注入 `ReportService`）

- `getTurnoverReport(begin, end)` / `getUserReport(begin, end)` / `getOrderReport(begin, end)` / `getSalesTop10(begin, end)`
- `begin`/`end` 缺省 = **最近 7 天（含今天）**，即 `today.minusDays(6)` ~ `today`。
- VO 里 `dateList`/`turnoverList` 等是**逗号拼接字符串**，按下标对齐拼成可读文本。
- ⛔ 不要暴露 `exportBusinessData`。

### WorkspaceSkill（注入 `WorkspaceService`，注意小写 s）

- `getTodayBusinessData()` → `getBusinessData(LocalDateTime.now().with(LocalTime.MIN), LocalDateTime.now().with(LocalTime.MAX))`，与 `WorkSpaceController` 保持一致。
- `getOrderOverview()` / `getDishOverview()` / `getSetmealOverview()`

### ShopSkill（注入 `ShopService`）

- `getShopStatus()` → `shopService.getStatus()`；`1`→营业中，`0` 或未设置语义按 Service 返回解释（当前实现未命中会初始化为打烊 `0`）。
- `setShopStatus(status)`：只接受 `"0"` / `"1"`，其余返回友好中文；再 `shopService.setStatus(Integer.valueOf(status))`。
- ⛔ **不要**在 Skill 里直接 `RedisTemplate` 读写（除非源码里确认没有 `ShopService`——当前工作区有）。

---

## 8. SSE 协议（严格遵守）

- 端点：`POST /admin/agent/stream`，`produces = MediaType.TEXT_EVENT_STREAM_VALUE`
- 请求体：`{"message": "用户这句话", "conversationId": "可选，默认 empId"}`
- 鉴权：完全复用 `/admin/**` 的 JWT 拦截器，**不要**把 agent 路径加进 `excludePathPatterns`
- 发送：`emitter.send(SseEmitter.event().data(json))`，`json` 为**单行** JSON 对象（注意转义换行）

五种事件：

```json
{"type":"text","content":"片段"}
{"type":"tool_call","name":"searchOrders","arguments":{"phone":"13800000000"}}
{"type":"tool_result","name":"searchOrders","result":"摘要文本，≤2KB"}
{"type":"end"}
{"type":"error","message":"用户可读中文"}
```

**规则**：

- `text` 只发**增量**（对应 `TokenStream.onNext`）
- `end` 或 `error` 之后**不得**再发任何 `text`；`AgentEventSink` 用 `AtomicBoolean closed` 保证幂等
- 任何 LLM / 工具 / 超时异常，都必须 `error` + `emitter.complete()`，**禁止连接无声断开**
- `TokenStream.onError` → 发 `error`；`onComplete` → 发 `end`
- 关键节点 `log.info`：stream start（含 empId、message 摘要）、tool start / end、stream end、exception
- `AgentEventSink` 所有发送方法加 `synchronized`

---

## 9. 前端实现规范

### `src/agent/types.ts`
定义事件联合类型。**TS 3.6.2 不支持 `?.` 和 `??`，一个都不许用**，写显式 `if` 判空。

### `src/agent/sse/agentStream.ts`
- 用**原生 `fetch`**，**不要用 `EventSource`**，**也不要用 `@/utils/request`**。
- 请求：`fetch('/api/agent/stream', { method:'POST', headers:{ 'Content-Type':'application/json', 'Accept':'text/event-stream', token: getToken() }, body: JSON.stringify({message}) })`
  - `getToken` 从 `@/utils/cookies` 导入。
- 用 `response.body.getReader()` + `TextDecoder`，按 `\n\n` 切分事件，解析 `data:` 行；**处理跨 chunk 半行**（保留 buffer）。
- 暴露 `startAgentStream(message, handlers)`，handlers 含 `onText/onToolCall/onToolResult/onEnd/onError`。

### `src/agent/views/AgentChat.vue`
- `vue-property-decorator` 类组件；**`@Component({ name: 'AgentChat' })` 必须与文件名一致**。
- 布局：`el-container`，上消息列表（用户右、助手左），下 `el-input` + 发送。
- `text` 追加到当前助手气泡；正文 `white-space: pre-wrap`。
- 工具：`el-collapse` / `el-card`；`tool_call` 插入 loading 卡片，`tool_result` 按 `name` 匹配最近未完成卡片并置完成。
- 首包未到：三点动画。发送后清空并 `disabled`，直到 `end`/`error`。`error` 用 `this.$message.error`。
- 样式 `<style lang="scss" scoped>`，对齐现有 `views/` 惯例。

### `src/router.ts` 增量（**只加这一段**）

```ts
{
  path: "agent",
  component: () =>
    import(/* webpackChunkName: "agent" */ "@/agent/views/AgentChat.vue"),
  name: "AiAgent",
  meta: {
    title: "AI 助手",
    icon: "icon-inform"   // iconfont 类名，带 icon- 前缀；复用现有类
  }
}
```

最终访问地址：`http://localhost:8081/#/agent`。

---

## 10. 测试矩阵（**必须逐条实跑并记录结果**）

| # | 场景 | 操作 | 期望 |
|---|------|------|------|
| T1 | 未登录 | `curl -i -X POST localhost:8080/admin/agent/stream -H 'Content-Type: application/json' -d '{"message":"你好"}'` | HTTP **401**，无响应体 |
| T2 | 无 API Key | 不设 `SKY_AGENT_API_KEY` 启动 → 带合法 token 请求 | 后端**正常启动**；返回一条 `{"type":"error"}` 中文提示后关闭；**无 500 堆栈** |
| T3 | 报表查询 | 「今天营业额怎么样」 | 事件序列含 `tool_call` + `tool_result` + 多个 `text` + `end` |
| T4 | 缺参防呆 | 只说「接单」 | 助手**追问单号**或先调 `searchOrders`；**不得**出现凭空 id 的 `confirmOrder` |
| T5 | 订单查询 | 「查一下手机号 138xxxx 的订单」 | 调用 `searchOrders`，返回 ≤10 条摘要 |
| T6 | 状态机异常 | 对一个非待接单订单执行接单 | 返回 `OrderBusinessException` 的中文 message，**不是堆栈** |
| T7 | 营业状态 | 「现在营业吗」→「打烊」 | `getShopStatus` → `setShopStatus("0")`；再读为打烊 |
| T8 | 前端交互 | 页面发一条消息 | 按钮禁用→`end` 后恢复；工具卡片 loading→完成 |
| T9 | 回归 | 登录、菜品列表、订单列表页 | 行为与改动前一致 |
| T10 | 越界审计 | `git diff --stat` | 存量文件改动**只有** `sky-server/pom.xml`、`application.yml`、`src/router.ts` 三个 |

**取 token 的方法**（T2–T7 需要）：

```bash
curl -s -X POST http://localhost:8080/admin/employee/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
# 从 data.token 取值，后续请求加 -H "token: <值>"
```

> `sky.sql` 中 admin 密码为 **MD5(`123456`)**；登录接口收**明文**，兼容 MD5/BCrypt/明文并可能升级哈希。请求体使用 `"password":"123456"`，**不要**客户端先做 MD5。

SSE 观察：

```bash
curl -N -X POST http://localhost:8080/admin/agent/stream \
  -H "token: <值>" \
  -H 'Content-Type: application/json' \
  -d '{"message":"今天营业额怎么样"}'
```

---

## 11. 交付物

1. `sky-take-out/sky-server/src/main/java/com/sky/agent/**` 全部类
2. `project-rjwm-admin-vue-ts/src/agent/**` + `src/router.ts` 一行增量
3. `docs/` 下一篇使用说明，必须包含：
   - 环境变量 `SKY_AGENT_API_KEY`（及 `SKY_AGENT_BASE_URL` / `SKY_AGENT_MODEL`）怎么设，Windows / Linux 两种写法
   - 为什么必须用 Node 14/16（以及 Node 18+ 的兜底办法）
   - 端口关系：后端 8080、前端 8081、`/api` 代理规则
   - 如何用 `curl -N` 验证 SSE（含拿 token 的完整命令）
   - langchain4j 锁死 0.35.0 的原因（Java 8 字节码上限）
4. **最终报告**：逐条回答 §0 的 D1–D8 与 §10 的 T1–T10，通过就写「通过」，没通过就写清**具体报错和原因**——**不允许把未验证的项写成通过**。

---

## 12. 故障排查与回退决策表

| 症状 | 最可能原因 | 处理 |
|------|-----------|------|
| 编译报 `class file has wrong version 61.0` | 误用了 langchain4j ≥ 0.36.0 | 降回 **0.35.0** |
| 模型完全不调用工具 | `@Tool` 写在了父类上，或 Skill 被 Spring 代理了 | `@Tool` 挪到具体类；确保用 `new` 构造 Skill |
| 工具调用了但前端没有 `tool_call` 事件 | 误以为 `TokenStream.onToolExecuted` 存在 | 0.35.0 没有该回调，改由 `AbstractSkill.invoke` 自行发送 |
| `BaseContext.getCurrentId()` 为 null | 工具在 OkHttp 线程执行 | 按 §4.2 在 `invoke` 里 set/remove |
| 编译找不到 `ShopService` | 工作区/分支与预期不符 | 先 `ls`/`grep` 确认；当前任务要求用 `ShopService`。若确实不存在，再临时退化为 `RedisTemplate` 操作 `SHOP_STATUS`，并在报告注明 |
| `confirm` 编译不过 / 入参类型错 | 误用 `OrdersCancelDTO` | 改为 `OrdersConfirmDTO`，只设 `id` |
| `rejection`/`cancel` 编译不过 | 它们 `throws Exception` | `catch (Exception e)` |
| 侧栏菜单没图标 | `meta.icon` 写成了 svg 名或不带前缀 | 改成 `icon-inform` |
| 前端 POST 被取消（`重复请求`） | 走了 `@/utils/request` | 换成原生 `fetch` |
| 前端 `npm run serve` 报 `ERR_OSSL_EVP_UNSUPPORTED` | Node ≥17 + webpack4 | 换 Node 14/16，或 `NODE_OPTIONS=--openssl-legacy-provider` |
| `fibers` 安装失败 | Node ≥17 无法编译该原生模块 | 换 Node 14/16，或 `npm install --ignore-scripts` |
| SSE 在浏览器里不流式 | 代理或响应被缓冲 | 确认 `produces=text/event-stream`；必要时调整 proxy |
| 启动就报 Bean 创建失败 | key 为空时强行构造模型 | 改懒加载 + `hasText`（D2 硬要求） |

---

## 13. 执行纪律

1. **先读代码再改代码**。动手前打开对应文件确认签名与上下文。
2. **按阶段推进**；不要攒一个巨型未验证提交。用户未明确要求 commit 时，不要擅自 `git commit`。
3. 遇到与本文件描述不符的情况，**以实际代码为准**，并在最终报告指出「文档第 X 条与实际不符：……」。
4. 不确定的技术选型**不要自由发挥**——版本和架构已锁定，按 §12 处理，不要为绕开问题而升级框架。
5. **禁止**为了让测试通过而修改存量业务逻辑。

---

## 附录：相对早期草稿的关键修正摘要

| 项 | 早期错误 | 本文件结论 |
|----|----------|------------|
| 前端栈 | Vue3 + Element Plus | Vue2.6 + Element UI 2 + 类组件 |
| 后端 ORM | MyBatis-Plus | MyBatis XML + PageHelper |
| Skill 形态 | HTTP 再调 `/admin/**` | 同进程 `@Tool` 直调 Service |
| SSE 路径 | `/api/agent` 无 `/admin` | 前端 `/api/agent/stream` → 后端 `/admin/agent/stream` |
| 鉴权客户端 | `EventSource` | `fetch` + 头 `token` |
| Shop | 无 Service / 直接 Redis | **注入 `ShopService`** |
| confirm DTO | `OrdersCancelDTO` | **`OrdersConfirmDTO`** |
| 登录密码说明 | 明文存库且 MD5 注释掉 | DB 为 MD5，接口收明文并可能升 BCrypt |
