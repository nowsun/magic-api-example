# Magic-API SaaS example

This example uses PostgreSQL for both Magic-API resource storage and SaaS access-key
authentication. API scripts are maintained by the platform team; SaaS tenants can
only obtain tokens and call published APIs. They do not receive the Magic-API editor
credentials.

## Configuration

Copy `cfg/magic-api.ini.example` to `cfg/magic-api.ini` and set the PostgreSQL
connection, the Magic-API administrator password, and a randomly generated
`saas.auth.token-signing-key` of at least 32 UTF-8 bytes. `cfg/magic-api.ini` is
ignored by Git. The INI loader supports `[section]` and `key=value`; section and key
names are joined with a dot, so `[spring.datasource]` plus `url=...` configures
`spring.datasource.url`. Environment variables and command-line properties can
override INI values.

`[magic-api] secret-key` is intentionally empty in the template. Leave it empty
unless you use Magic-API remote push; setting it enables the push receiver, so use
a separate randomly generated secret if that feature is required.

For example, use PowerShell to copy the template:

```powershell
Copy-Item cfg\magic-api.ini.example cfg\magic-api.ini
```

设计器地址为 `http://localhost:9999/web`。在 `[magic-api.security]` 中设置
`login=1` 时需要登录，并从同一分区读取管理员用户名和密码；设置 `login=0` 时关闭
设计器登录验证。未配置 `login` 时默认为 `1`，仅接受 `0` 或 `1`。关闭登录验证后，
任何能够访问该地址的人都可以进入设计器，因此只应在可信的内部网络中使用，并通过
防火墙或反向代理限制访问。

接口文档（Knife4j）地址为 `http://localhost:9999/doc.html`，用于查看 Magic-API 动态
接口生成的 Swagger 文档。

## PostgreSQL schema

Create a dedicated database and least-privilege application role, then apply:

```sql
CREATE TABLE magic_api_file_v2 (
    file_path    varchar(512) PRIMARY KEY,
    file_content text
);

CREATE TABLE magic_backup_record_v2 (
    id          varchar(32) NOT NULL,
    create_date bigint NOT NULL,
    tag         varchar(32),
    type        varchar(32),
    name        varchar(64),
    content     bytea,
    create_by   varchar(64),
    PRIMARY KEY (id, create_date)
);

CREATE TABLE magic_api_access (
    id                       bigserial PRIMARY KEY,
    tenant_id                bigint NOT NULL,
    access_key_id            varchar(128) NOT NULL UNIQUE,
    access_key_secret_hash   char(64) NOT NULL,
    status                   int2 NOT NULL DEFAULT 1,
    expires_at               timestamptz,
    created_at               timestamptz NOT NULL DEFAULT current_timestamp
);
```

`magic_api_file_v2` stores the APIs and functions. `magic_backup_record_v2` stores
their history. `magic_api_access` stores tenant credentials; only a lowercase
SHA-256 hex digest of the AccessKey Secret is stored. Create and rotate high-entropy
tenant secrets through a trusted provisioning process. For a local-only development
seed, enable PostgreSQL `pgcrypto`; never put a real secret in a committed SQL file:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO magic_api_access (tenant_id, access_key_id, access_key_secret_hash)
VALUES (123456789012345, 'replace-with-access-key-id',
        encode(digest('replace-with-access-key-secret', 'sha256'), 'hex'));
```

Grant the application role `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on the
resource and backup tables, but only `SELECT` on `magic_api_access`. Use a separate
provisioning role to create, rotate, disable, and expire tenant credentials. If
database extensions are not permitted, have the credential provisioning service
compute the SHA-256 hex digest before inserting.

### 在低代码 JavaScript 环境中生成 AccessKey ID 和 Secret

如果当前 JavaScript 执行环境已验证支持 `crypto.getRandomValues` 和
`crypto.subtle.digest`，可以用下面的代码生成较短凭据：AccessKey ID 为 24 个
十六进制字符，Secret 为 32 个十六进制字符（128 位随机熵），摘要为 64 个字符。

```javascript
function randomHex(byteLength) {
  var bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  var hex = "";
  for (var i = 0; i < bytes.length; i++) {
    hex += ("0" + bytes[i].toString(16)).slice(-2);
  }
  return hex;
}

async function generateAccessKey() {
  var accessKeyId = randomHex(12);
  var accessKeySecret = randomHex(16);
  var input = new Uint8Array(accessKeySecret.length);
  for (var i = 0; i < accessKeySecret.length; i++) {
    input[i] = accessKeySecret.charCodeAt(i);
  }
  var digest = new Uint8Array(await crypto.subtle.digest("SHA-256", input));
  var accessKeySecretHash = "";
  for (var j = 0; j < digest.length; j++) {
    accessKeySecretHash += ("0" + digest[j].toString(16)).slice(-2);
  }
  return { accessKeyId: accessKeyId, accessKeySecret: accessKeySecret,
           accessKeySecretHash: accessKeySecretHash };
}

return generateAccessKey();
```

凭据只能在受信任的管理员签发流程中生成，绝不能通过租户可调用的动态 API
暴露。将 `accessKeySecretHash` 写入 `magic_api_access`，明文 `accessKeySecret`
仅向客户展示一次，不要存储或记录它。该示例只负责生成值；数据库写入应由受信任
的凭据管理端执行。

如果想单独检查当前 JavaScript 运行环境是否支持 `crypto.subtle.digest`，运行下面
的异步代码。它用 SHA-256 标准测试向量 `abc` 验证结果；成功时返回 `supported: true`：

```javascript
async function verifySubtleDigest() {
  if (typeof crypto === "undefined"
      || !crypto.subtle
      || typeof crypto.subtle.digest !== "function") {
    return { supported: false, reason: "crypto.subtle.digest 不可用" };
  }

  var input = new Uint8Array([97, 98, 99]); // UTF-8 bytes for "abc"
  var digestBuffer = await crypto.subtle.digest("SHA-256", input);
  var digestBytes = new Uint8Array(digestBuffer);
  var hex = "";
  for (var i = 0; i < digestBytes.length; i++) {
    hex += ("0" + digestBytes[i].toString(16)).slice(-2);
  }

  var expected = "ba7816bf8f01cfea414140de5dae2223"
      + "b00361a396177a9cb410ff61f20015ad";
  return {
    supported: hex === expected,
    digest: hex,
    expected: expected
  };
}

return verifySubtleDigest();
```

生成代码需要运行环境支持 Promise/`async`，并且执行器能够等待异步返回值。有些 Web
运行环境还要求安全上下文（HTTPS 或 localhost）。即使该验证通过，也只证明当前
JavaScript 执行环境能运行 Web Crypto；不要假设 MagicScript 服务端自动提供同一套 API。

MagicScript 的 `tenantId` 是 Java `long` 数值。若从 JavaScript 数据库客户端传入
PostgreSQL `bigint`，应使用十进制字符串，避免超过 `Number.MAX_SAFE_INTEGER`
时的精度损失。

## Tenant token flow

1. A tenant sends its credentials to `POST /auth/token`:

   ```http
   POST /auth/token HTTP/1.1
   Content-Type: application/json

   {
     "accessKeyId": "tenant-access-key-id",
     "accessKeySecret": "tenant-access-key-secret"
   }
   ```

2. A successful response contains `accessToken`, `tokenType` (`token`) and
   `expiresIn` (seconds). The default token lifetime is one hour and can be set with
   `saas.auth.token-ttl-seconds` in the INI file (allowed range: 60–86400 seconds).
3. The tenant sends the token to each dynamic API:

   ```http
   token: <accessToken>
   ```

The `token` header is separate from any API parameter also named `token`.

The token is HMAC-SHA256 signed with the platform signing key and checked against
`magic_api_access` by a Spring MVC pre-handler on every dynamic API request, before
Magic-API parameter validation or script execution. Disabling or expiring a key
therefore rejects its existing tokens immediately. Missing, invalid, or expired
credentials receive HTTP 401. PostgreSQL availability errors are not treated as
authentication failures.
Serve both token issuance and APIs over HTTPS, and apply rate limits to the token
endpoint at the gateway.

For each authorized request the script context receives `tenantId`. Use it in every
tenant-owned query and mutation, for example:

```javascript
return db.select("select id, name from customer where tenant_id = ?", [tenantId]);
```

Authentication alone does not enforce row-level tenant isolation: every published
script must constrain reads, updates, and deletes to `tenantId`, and must not trust a
tenant ID supplied as a query, path, header, or body parameter. Review scripts before
publishing them.

## Running

With Java 8+ and Maven installed, build and run:

```powershell
mvn package
java -jar target\magic-api-demo.jar
```

Apply the schema and configure the INI before starting. Do not deploy the sample
credentials or expose the editor publicly. The included `.ms` files are feature
examples; with database-backed resources they are not automatically copied into
PostgreSQL. Import only the reviewed scripts you intend to publish, then check every
tenant-owned query for `tenantId` enforcement.
