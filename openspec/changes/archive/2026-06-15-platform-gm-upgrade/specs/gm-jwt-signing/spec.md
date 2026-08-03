## ADDED Requirements

### Requirement: JWT 签名算法替换为 SM3-HMAC

平台 SHALL 使用 SM3-HMAC 作为 JWT 唯一签名算法。MUST NOT 再使用 HS256 / HS384 / HS512 / RS256 等基于 SHA 系列或 RSA 的算法。

#### Scenario: TokenService 签发 JWT
- **WHEN** `TokenService.createToken(loginUser)` 被调用
- **THEN** 返回 token 的 header `alg` 字段 SHALL 为 `SM3-HMAC`
- **AND** 签名部分 SHALL 由 sq-common 的 `Sm3Utils.hmac(secret, header.payload)` 计算得出

#### Scenario: TokenService 校验 JWT
- **WHEN** `TokenService.parseToken(token)` 收到 header `alg=HS512` 或其他非 `SM3-HMAC` 的 token
- **THEN** 校验 MUST 失败
- **AND** MUST 抛出明确的算法不匹配异常

### Requirement: JWT 三段结构由平台自实现，不依赖 jjwt

平台 SHALL 自实现 JWT 编解码：`base64url(header) + "." + base64url(payload) + "." + base64url(SM3-HMAC(secret, header + "." + payload))`。MUST NOT 引入 jjwt（io.jsonwebtoken）作为运行时依赖。

#### Scenario: 父 POM 不再管理 jjwt
- **WHEN** 检查 sq-dependencies-core 与 sq-fast 父 POM
- **THEN** 两份 POM 均 MUST NOT 包含 `io.jsonwebtoken` 依赖声明
- **AND** `mvn dependency:tree -Dincludes=io.jsonwebtoken` 输出 SHALL 为空

#### Scenario: JWT 解析容错
- **WHEN** 收到非三段结构、base64 解码失败、或 JSON 解析失败的 token
- **THEN** 解析 MUST 抛出明确异常
- **AND** MUST NOT 泄漏内部堆栈到 HTTP 响应

### Requirement: JWT 签名密钥来源

JWT 签名密钥 SHALL 通过配置项 `token.secret` 注入，并由环境变量覆盖。MUST NOT 使用代码硬编码的默认密钥用于生产环境。

#### Scenario: 密钥配置
- **WHEN** 检查 application.yml
- **THEN** `token.secret` SHALL 写为 `${SQ_JWT_SECRET:<开发态默认值>}` 形式
- **AND** 部署文档 MUST 要求生产环境通过 `SQ_JWT_SECRET` 环境变量注入强随机值（≥ 32 字节）
