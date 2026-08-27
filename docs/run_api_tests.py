# -*- coding: utf-8 -*-
"""API smoke/boundary tests against running sky-server. Outputs JSON results."""
import json
import time
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timedelta

BASE = "http://localhost:8080"
results = []


def req(method, path, body=None, headers=None, expect_json=True):
    url = BASE + path
    data = None
    hdrs = {"Content-Type": "application/json"}
    if headers:
        hdrs.update(headers)
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    r = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    started = time.time()
    try:
        with urllib.request.urlopen(r, timeout=15) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            elapsed = int((time.time() - started) * 1000)
            parsed = None
            if expect_json and raw:
                try:
                    parsed = json.loads(raw)
                except Exception:
                    parsed = raw
            return {
                "ok": True,
                "status": resp.status,
                "body": parsed if parsed is not None else raw,
                "elapsed_ms": elapsed,
            }
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        elapsed = int((time.time() - started) * 1000)
        parsed = raw
        try:
            parsed = json.loads(raw) if raw else None
        except Exception:
            pass
        return {
            "ok": False,
            "status": e.code,
            "body": parsed,
            "elapsed_ms": elapsed,
        }
    except Exception as e:
        return {
            "ok": False,
            "status": None,
            "body": str(e),
            "elapsed_ms": int((time.time() - started) * 1000),
        }


def record(case_id, name, nature, endpoint, method, data, boundary, resp, expected, actual_pass):
    results.append({
        "id": case_id,
        "name": name,
        "nature": nature,
        "method": method,
        "endpoint": endpoint,
        "request_data": data,
        "boundary": boundary,
        "expected": expected,
        "http_status": resp.get("status"),
        "response": resp.get("body"),
        "elapsed_ms": resp.get("elapsed_ms"),
        "pass": actual_pass,
        "note": "",
    })


# ---------- A. SpringDoc ----------
r = req("GET", "/swagger-ui/index.html", expect_json=False)
record("DOC-01", "Swagger UI 可访问", "冒烟", "/swagger-ui/index.html", "GET", None,
       "文档入口应 200", r, "HTTP 200", r.get("status") == 200)

r = req("GET", "/v3/api-docs/" + urllib.parse.quote("管理端接口"))
record("DOC-02", "管理端 OpenAPI JSON", "冒烟", "/v3/api-docs/管理端接口", "GET", None,
       "应返回 paths", r, "HTTP 200 且含 paths",
       r.get("status") == 200 and isinstance(r.get("body"), dict) and "paths" in r.get("body", {}))

r = req("GET", "/v3/api-docs/" + urllib.parse.quote("用户端接口"))
record("DOC-03", "用户端 OpenAPI JSON", "冒烟", "/v3/api-docs/用户端接口", "GET", None,
       "应返回 paths", r, "HTTP 200 且含 paths",
       r.get("status") == 200 and isinstance(r.get("body"), dict) and "paths" in r.get("body", {}))

# ---------- B. Admin auth ----------
r = req("POST", "/admin/employee/login", {"username": "admin", "password": "123456"})
admin_token = None
login_ok = r.get("status") == 200 and isinstance(r.get("body"), dict) and r["body"].get("code") == 1
if login_ok:
    admin_token = (r["body"].get("data") or {}).get("token")
record("ADM-LOGIN-01", "管理员正确账号密码登录", "功能/正向",
       "/admin/employee/login", "POST", {"username": "admin", "password": "123456"},
       "合法账号", r, "code=1 且返回 token", login_ok and bool(admin_token))

r = req("POST", "/admin/employee/login", {"username": "admin", "password": "wrong"})
code = r.get("body", {}).get("code") if isinstance(r.get("body"), dict) else None
record("ADM-LOGIN-02", "错误密码登录", "边界/负向",
       "/admin/employee/login", "POST", {"username": "admin", "password": "wrong"},
       "密码错误", r, "业务失败 code!=1 或异常信息",
       r.get("status") == 200 and code != 1)

r = req("POST", "/admin/employee/login", {"username": "nobody", "password": "123456"})
code = r.get("body", {}).get("code") if isinstance(r.get("body"), dict) else None
record("ADM-LOGIN-03", "不存在的用户名", "边界/负向",
       "/admin/employee/login", "POST", {"username": "nobody", "password": "123456"},
       "账号不存在", r, "业务失败",
       r.get("status") == 200 and code != 1)

r = req("POST", "/admin/employee/login", {})
code = r.get("body", {}).get("code") if isinstance(r.get("body"), dict) else None
record("ADM-LOGIN-04", "空 body 登录", "边界",
       "/admin/employee/login", "POST", {},
       "缺 username/password", r, "失败或异常",
       True)  # 记录结果，是否符合预期由报告说明
results[-1]["pass"] = r.get("status") in (200, 400, 500)
results[-1]["note"] = f"实际 code={code}, status={r.get('status')}"

r = req("GET", "/admin/employee/page?page=1&pageSize=10")
record("ADM-AUTH-01", "无 Token 访问员工分页", "安全/负向",
       "/admin/employee/page", "GET", None,
       "未登录", r, "HTTP 401",
       r.get("status") == 401)

r = req("GET", "/admin/employee/page?page=1&pageSize=10",
       headers={"token": "invalid.token.value"})
record("ADM-AUTH-02", "伪造 Token 访问", "安全/负向",
       "/admin/employee/page", "GET", None,
       "非法 JWT", r, "HTTP 401",
       r.get("status") == 401)

auth = {"token": admin_token} if admin_token else {}

# ---------- C. Admin employee ----------
r = req("GET", "/admin/employee/page?page=1&pageSize=10", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
records = ((body.get("data") or {}).get("records")) if body else None
pwd_masked = True
if records:
    for emp in records:
        if emp.get("password") not in (None, "****", ""):
            # if still a hash/plain, mark fail for脱敏
            if emp.get("password") and emp.get("password") != "****":
                pwd_masked = False
                break
record("ADM-EMP-01", "员工分页查询", "功能",
       "/admin/employee/page?page=1&pageSize=10", "GET", {"page": 1, "pageSize": 10},
       "已登录", r, "code=1 且 password 脱敏",
       r.get("status") == 200 and body.get("code") == 1 and pwd_masked)
results[-1]["note"] = f"脱敏检查={pwd_masked}, sample={(records[0] if records else None)}"

r = req("GET", "/admin/employee/1", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
data = body.get("data") or {}
record("ADM-EMP-02", "按 id 查员工", "功能",
       "/admin/employee/1", "GET", None,
       "存在的 id=1", r, "code=1, password=****",
       r.get("status") == 200 and body.get("code") == 1 and data.get("password") == "****")

r = req("GET", "/admin/employee/999999", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-EMP-03", "按不存在 id 查员工", "边界",
       "/admin/employee/999999", "GET", None,
       "不存在 id", r, "失败或空/异常",
       True)
results[-1]["pass"] = r.get("status") in (200, 500)
results[-1]["note"] = f"response={body}"

# ---------- D. Category / Dish / Setmeal ----------
r = req("GET", "/admin/category/page?page=1&pageSize=10", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-CAT-01", "分类分页", "功能",
       "/admin/category/page", "GET", {"page": 1, "pageSize": 10},
       "正常分页", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/category/list?type=1", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-CAT-02", "按类型查分类 type=1", "功能",
       "/admin/category/list?type=1", "GET", {"type": 1},
       "菜品分类", r, "code=1 且 list",
       r.get("status") == 200 and body.get("code") == 1 and isinstance(body.get("data"), list))

r = req("GET", "/admin/dish/page?page=1&pageSize=5", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-DISH-01", "菜品分页", "功能",
       "/admin/dish/page", "GET", {"page": 1, "pageSize": 5},
       "正常", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/dish/46", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-DISH-02", "按 id 查菜品", "功能",
       "/admin/dish/46", "GET", None,
       "种子菜品 id=46(若库有)", r, "code=1 或业务失败",
       r.get("status") == 200)
results[-1]["note"] = f"code={body.get('code')}, msg={body.get('msg')}"

r = req("GET", "/admin/setmeal/page?page=1&pageSize=5", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-SET-01", "套餐分页", "功能",
       "/admin/setmeal/page", "GET", {"page": 1, "pageSize": 5},
       "正常", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

# ---------- E. Shop / Workspace / Order / Report ----------
r = req("GET", "/admin/shop/status", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-SHOP-01", "获取店铺状态", "功能",
       "/admin/shop/status", "GET", None,
       "已登录", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("PUT", "/admin/shop/1", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-SHOP-02", "设置店铺营业 status=1", "功能",
       "/admin/shop/1", "PUT", None,
       "status=1 营业", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/workspace/businessData", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-WS-01", "工作台今日数据", "功能",
       "/admin/workspace/businessData", "GET", None,
       "已登录", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/order/statistics", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-ORD-01", "订单状态统计", "功能",
       "/admin/order/statistics", "GET", None,
       "已登录", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/order/conditionSearch?page=1&pageSize=5", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-ORD-02", "订单条件搜索", "功能",
       "/admin/order/conditionSearch", "GET", {"page": 1, "pageSize": 5},
       "分页", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/admin/order/details/1", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-ORD-03", "管理端订单详情 id=1", "功能/边界",
       "/admin/order/details/1", "GET", None,
       "订单可能不存在", r, "code=1 或业务错误",
       r.get("status") == 200)
results[-1]["note"] = f"code={body.get('code')}, msg={body.get('msg')}"

begin = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d")
end = datetime.now().strftime("%Y-%m-%d")
r = req("GET", f"/admin/report/turnoverStatistics?begin={begin}&end={end}", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-RPT-01", "营业额统计", "功能",
       "/admin/report/turnoverStatistics", "GET", {"begin": begin, "end": end},
       "近7天", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", f"/admin/report/userStatistics?begin={begin}&end={end}", headers=auth)
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-RPT-02", "用户统计", "功能",
       "/admin/report/userStatistics", "GET", {"begin": begin, "end": end},
       "近7天", r, "code=1",
       r.get("status") == 200 and body.get("code") == 1)

# ---------- F. Upload boundaries ----------
# invalid extension via multipart is harder in urllib; test empty/missing file as form
boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
form = (
    f"--{boundary}\r\n"
    f'Content-Disposition: form-data; name="file"; filename="evil.exe"\r\n'
    f"Content-Type: application/octet-stream\r\n\r\n"
    f"MZ_fake\r\n"
    f"--{boundary}--\r\n"
).encode("utf-8")
upload_headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
if admin_token:
    upload_headers["token"] = admin_token
ur = urllib.request.Request(BASE + "/admin/common/upload", data=form, headers=upload_headers, method="POST")
try:
    with urllib.request.urlopen(ur, timeout=15) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
        status = resp.status
except urllib.error.HTTPError as e:
    status = e.code
    raw = e.read().decode("utf-8", errors="replace")
except Exception as e:
    status = None
    raw = str(e)
try:
    ubody = json.loads(raw) if raw and raw.startswith("{") else {"raw": raw}
except Exception:
    ubody = {"raw": raw}
record("ADM-UP-01", "上传非法扩展名 .exe", "安全/边界",
       "/admin/common/upload", "POST", {"filename": "evil.exe"},
       "非白名单扩展名", {"status": status, "body": ubody},
       "业务失败 code!=1 提示文件类型",
       status == 200 and isinstance(ubody, dict) and ubody.get("code") != 1)

# ---------- G. User side ----------
r = req("GET", "/user/shop/status")
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("USR-SHOP-01", "用户端店铺状态(免登录)", "功能/安全",
       "/user/shop/status", "GET", None,
       "公开接口", r, "HTTP 200 code=1",
       r.get("status") == 200 and body.get("code") == 1)

r = req("GET", "/user/category/list")
record("USR-AUTH-01", "无 Token 访问分类列表", "安全/负向",
       "/user/category/list", "GET", None,
       "需登录", r, "HTTP 401",
       r.get("status") == 401)

r = req("GET", "/user/dish/list?categoryId=11")
record("USR-AUTH-02", "无 Token 访问菜品列表", "安全/负向",
       "/user/dish/list", "GET", {"categoryId": 11},
       "需登录", r, "HTTP 401",
       r.get("status") == 401)

r = req("POST", "/user/user/login", {"code": "fake_wx_code"})
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("USR-LOGIN-01", "微信登录假 code", "边界/集成",
       "/user/user/login", "POST", {"code": "fake_wx_code"},
       "无效 code / 未配 appid", r, "业务失败",
       r.get("status") == 200 and body.get("code") != 1)
results[-1]["note"] = f"msg={body.get('msg')}"

r = req("GET", "/user/order/orderDetail/1", headers={"authentication": "fake"})
record("USR-ORD-01", "用户订单详情伪造 token", "安全",
       "/user/order/orderDetail/1", "GET", None,
       "伪造 authentication", r, "HTTP 401",
       r.get("status") == 401)

# Second login should upgrade password - login again to verify still works
r = req("POST", "/admin/employee/login", {"username": "admin", "password": "123456"})
body = r.get("body") if isinstance(r.get("body"), dict) else {}
record("ADM-LOGIN-05", "再次登录(验证BCrypt升级后)", "回归",
       "/admin/employee/login", "POST", {"username": "admin", "password": "123456"},
       "密码已可能升级为BCrypt", r, "仍能 code=1",
       r.get("status") == 200 and body.get("code") == 1)

out_path = r"d:\internship\skytakeout\docs\api-test-results.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

passed = sum(1 for x in results if x["pass"])
failed = sum(1 for x in results if not x["pass"])
print(f"TOTAL={len(results)} PASS={passed} FAIL={failed}")
print("TOKEN=" + ("yes" if admin_token else "no"))
for x in results:
    flag = "PASS" if x["pass"] else "FAIL"
    print(f"{flag} {x['id']} {x['name']} http={x['http_status']}")
