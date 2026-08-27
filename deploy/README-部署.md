# 苍穹外卖 部署说明（Nginx + Spring Boot）

本目录提供一套可直接上线的部署骨架。先在本机/内网跑通，再平滑切到公网 + HTTPS。

```
deploy/
├── nginx/sky-take-out.conf      # Nginx 站点：托管前端 + 反代后端 + SSE
├── sky-server.env.example       # 后端密钥模板（复制为 sky-server.env 填值）
├── sky-server.service.example   # systemd 单元（EnvironmentFile 注入密钥）
├── run-local.sh                 # 本地一键：加载 env 后启动 jar
└── README-部署.md               # 本文
```

---

## 1. 架构与端口

```
浏览器 ──▶ Nginx(80/443) ──┬─ /            → 前端静态文件 dist
                           ├─ /api/**      → 改写为后端 /admin/**（含 /api/agent SSE）
                           ├─ /notify/**   → 后端（微信支付回调，公网可达）
                           └─ /ws/**       → 后端 WebSocket（来单提醒）
                                              后端 Spring Boot :8080
                                                 ├─ MySQL :3306
                                                 └─ Redis :6379
```

前端生产环境固定请求 `/api/**`（见 `.env.production` 的 `VUE_APP_BASE_API=/api`），
Nginx 负责把 `/api/x` 改写成后端的 `/admin/x`，与开发环境 devServer 代理规则一致。

---

## 2. 构建产物

**后端（JDK 8/11 均可编译，源码级别 1.8）：**
```bash
cd sky-take-out
mvn -pl sky-server -am clean package -DskipTests
# 产物：sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

**前端（Node 14/16 最稳；Node 18+ 需 export NODE_OPTIONS=--openssl-legacy-provider）：**
```bash
cd project-rjwm-admin-vue-ts
npm install
npm run build          # 产物：dist/
```
把 `dist/` 拷到 Nginx 的 `root` 目录（默认 `/var/www/sky-admin`）。

---

## 3. ⭐ 密钥放哪里——「在 Nginx 里预留变量由你填充」的可行性评估

**结论：对「应用密钥」不可行；对「Nginx 自身的配置项」可行。**

### 为什么不可行
Nginx 是反向代理 / Web 服务器，它的变量（`set`、`map`、`env`）只作用于**它自己处理请求响应**的过程，
**无法把值注入到另一个进程（后端 JVM）的环境变量里**。而本项目的密钥——数据库口令、
`SKY_JWT_*`、`SKY_AGENT_API_KEY`、微信/OSS 密钥——全部是**后端 Spring Boot 读取的**
（`application.yml` 里的 `${SKY_...}`）。因此把它们写进 `nginx.conf` 后端根本读不到，
既不生效，还把密钥散落到了 Web 层配置里，增大泄露面。

Nginx 层**唯一天然属于它**的密钥是 **TLS 证书/私钥**（`ssl_certificate*`），
这个确实预留在 `nginx/sky-take-out.conf` 里由你填。此外域名、上游地址也预留在那里。

### 推荐的密钥填充方案（本仓库采用）
把应用密钥集中在一个**环境变量文件**里，由后端进程加载。三种等价落地方式：

| 方式 | 怎么做 | 适用 |
|------|--------|------|
| **systemd（推荐上线）** | `sky-server.service` 里 `EnvironmentFile=/etc/sky-take-out/sky-server.env`，文件 `chmod 600` | 裸机/VM 生产 |
| **Docker** | `docker run --env-file deploy/sky-server.env ...`，或 compose 的 `env_file` | 容器化 |
| **裸机脚本（先跑起来）** | `deploy/run-local.sh` 内 `set -a; source sky-server.env; set +a; java -jar` | 本地/内网验证 |

你要做的就是：`cp sky-server.env.example sky-server.env`，然后**只在这一个文件里填值**。
`application-dev.yml` 只做「环境变量 → 配置项」的映射，不含明文密钥。

### 更进一步（生产强化，可选）
- 用 **Vault / 阿里云 KMS / AWS Secrets Manager** 托管，启动时拉取并导出为环境变量；
- Docker 环境用 **docker secret**（挂载为文件）而非明文 `--env-file`；
- 无论哪种，`sky-server.env` 都**不要提交 Git**（仓库只保留 `.example`）。

---

## 4. 本地先上线（最快路径）

```bash
# 1) 准备 MySQL + Redis，并导入库表
mysql -uroot -p < database/sky.sql
mysql -uroot -p < database/security_hardening.sql   # 索引 + 密码列加宽 + admin 口令转 MD5

# 2) 后端：复制并填写 application-dev.yml（它只读环境变量，无需写死密钥）
cp sky-take-out/sky-server/src/main/resources/application-dev.yml.example \
   sky-take-out/sky-server/src/main/resources/application-dev.yml

# 3) 构建后端
cd sky-take-out && mvn -pl sky-server -am clean package -DskipTests && cd ..

# 4) 填密钥并启动后端
cp deploy/sky-server.env.example deploy/sky-server.env   # 编辑填值
bash deploy/run-local.sh

# 5) 前端构建 + 用 Nginx 托管（或本机 nginx 指向 dist）
cd project-rjwm-admin-vue-ts && npm install && npm run build && cd ..
sudo cp deploy/nginx/sky-take-out.conf /etc/nginx/conf.d/sky-take-out.conf
sudo mkdir -p /var/www/sky-admin && sudo cp -r project-rjwm-admin-vue-ts/dist/* /var/www/sky-admin/
sudo nginx -t && sudo nginx -s reload
```

登录默认账号 `admin / 123456`（`security_hardening.sql` 已将其存为 MD5，
首次登录后端会自动升级为 BCrypt）。**上线后请立即用「修改密码」改掉初始口令。**

自检：
```bash
curl -i http://localhost/api/employee/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'      # 期望 code=1 且返回 token
```

---

## 5. 切公网 + HTTPS

1. 域名解析到服务器；用 certbot 或云厂商签发证书。
2. 打开 `nginx/sky-take-out.conf` 里注释的 443 server 块，填 `server_name` 与证书路径，
   并启用 80→443 跳转。
3. 后端 `sky-server.env` 里把 `SKY_JWT_*` 换成足够长的随机串（`openssl rand -base64 48`），
   微信回调地址 `SKY_WECHAT_NOTIFY_URL` 指向 `https://你的域名/notify/paySuccess`。
4. `sudo nginx -t && sudo systemctl reload nginx`。

---

## 6. 已知未在代码内解决、上线前需注意的项

- **RBAC**：当前任何登录员工权限相同（无角色）。Nginx 无法弥补，需后端加角色模型；
  在此之前，请严格控制管理端账号发放。
- **Druid 控制台 / Swagger**：`nginx/sky-take-out.conf` 已在代理层 `return 404` 拦截公网访问，
  作为纵深防御；如需内网查看可临时放开。
- **微信支付回调验签**：后端目前依赖 APIv3 密钥 AES-GCM 解密提供真实性，未做证书验签；
  需要平台证书后再补。
- **JWT 默认密钥**：`application.yml` 内置的开发默认值仅用于本地；生产**必须**通过
  `SKY_JWT_*` 环境变量覆盖，否则会用公开默认密钥签发令牌。
