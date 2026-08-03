## ADDED Requirements

### Requirement: 前端密码字段使用 SM2 加密传输

sq-ui (Vue2) 与 sq-ui-vue3 (Vue3) 登录页 SHALL 使用 sm-crypto@0.3.13 进行 SM2 加密。MUST NOT 再使用 jsencrypt（RSA）。MUST NOT 在前端代码中硬编码任何公钥。

#### Scenario: 登录页加密密码字段
- **WHEN** 用户在 sq-ui 或 sq-ui-vue3 登录页提交账号密码
- **THEN** 前端 SHALL 通过 `GET /system/publicKey` 拉取后端 SM2 公钥
- **AND** SHALL 使用 sm-crypto 0.3.13 的 `doEncrypt(password, publicKey, 1)` 加密（cipherMode=1 即 C1C3C2）
- **AND** 加密后的密文 SHALL 在请求体中替换原明文密码字段

#### Scenario: 移除 jsencrypt 依赖
- **WHEN** 检查 `sq-ui/package.json` 与 `sq-ui-vue3/package.json`
- **THEN** 两份 `dependencies` 中 MUST NOT 包含 `jsencrypt`
- **AND** 两份 `dependencies` 中 SHALL 包含 `sm-crypto: 0.3.13`

#### Scenario: 移除前端硬编码公钥
- **WHEN** 在 `sq-ui/src/utils/jsencrypt.js` 与 `sq-ui-vue3/src/utils/jsencrypt.js` 及登录页中搜索
- **THEN** 文件中 MUST NOT 出现任何 `BEGIN PUBLIC KEY` 字面量
- **AND** MUST NOT 出现任何硬编码的 hex/base64 公钥常量

### Requirement: 公钥拉取接口

后端 SHALL 提供 `GET /system/publicKey` 接口返回当前 SM2 公钥。该接口 SHALL 配置为 permitAll（无需认证），返回 PEM 格式公钥字符串。

#### Scenario: 公钥接口可匿名访问
- **WHEN** 未携带 token 直接 GET `/system/publicKey`
- **THEN** 响应状态码 SHALL 为 200
- **AND** 响应体 SHALL 包含以 `-----BEGIN PUBLIC KEY-----` 开头的 PEM 字符串

#### Scenario: 公钥接口注册在 SecurityConfig 白名单
- **WHEN** 检查 SecurityConfig
- **THEN** `/system/publicKey` SHALL 出现在 `permitAll` 路径列表中

### Requirement: 后端登录前置 SM2 解密

`SysLoginService.login()` SHALL 在调用 Spring Security 认证前，将请求中的密码字段通过 SM2 私钥解密为明文。解密失败时 MUST 拒绝登录。

#### Scenario: 正常登录解密
- **WHEN** 收到的登录请求中密码为前端 SM2 加密的密文
- **THEN** 后端 SHALL 用 keystore 加载的 SM2 私钥解密
- **AND** 解密得到的明文 SHALL 传入 `Sm3PasswordEncoder.matches()` 进行校验

#### Scenario: 解密失败拒绝
- **WHEN** 收到的密码字段无法被 SM2 私钥解密
- **THEN** 登录 MUST 失败
- **AND** 错误响应 MUST NOT 暴露 SM2 私钥相关错误细节
