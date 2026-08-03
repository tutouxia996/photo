## ADDED Requirements

### Requirement: 国密加解密统一工具类

sq-common SHALL 提供 `Sm2Utils`、`Sm3Utils`、`Sm4Utils` 三个工具类，封装 BouncyCastle 1.70 的国密原语，作为平台所有国密能力的唯一入口。业务代码 MUST NOT 直接调用 BC 或 Hutool 加密 API。

#### Scenario: SM2 加解密支持 C1C3C2 模式
- **WHEN** 调用 `Sm2Utils.encrypt(publicKey, plaintext)` 或 `Sm2Utils.decrypt(privateKey, ciphertext)`
- **THEN** 加解密 MUST 使用 C1C3C2 编码模式（GM/T 0003.4-2012）
- **AND** 与前端 sm-crypto 0.3.13 的 `cipherMode=1` 输出互通

#### Scenario: SM3 摘要与 HMAC
- **WHEN** 调用 `Sm3Utils.digest(bytes)` 或 `Sm3Utils.hmac(key, bytes)`
- **THEN** 输出 MUST 为 32 字节摘要
- **AND** 实现 MUST 通过国标测试向量校验

#### Scenario: SM4 对称加解密
- **WHEN** 调用 `Sm4Utils.encrypt(key, iv, plaintext, mode)` 或 `Sm4Utils.decrypt(key, iv, ciphertext, mode)`
- **THEN** mode MUST 支持 ECB / CBC 两种
- **AND** 块大小固定 16 字节

### Requirement: BouncyCastle 版本固定为 1.70 jdk15on

sq-dependencies-core SHALL 在父 POM dependencyManagement 中固定 `bcprov-jdk15on:1.70` 与 `bcpkix-jdk15on:1.70`。MUST NOT 引入 jdk18on 或其他版本。

#### Scenario: BC 依赖版本一致
- **WHEN** 检查 sq-dependencies-core 父 POM
- **THEN** `bcprov-jdk15on` 与 `bcpkix-jdk15on` 版本 MUST 均为 `1.70`
- **AND** 全工程 `mvn dependency:tree` 中 MUST NOT 出现 `bcprov-jdk18on`

### Requirement: PEM 公钥序列化

`Sm2Utils` SHALL 提供 `toPem(publicKey)` 方法，将 SM2 公钥序列化为 PEM 格式（`-----BEGIN PUBLIC KEY-----` ... `-----END PUBLIC KEY-----`）。

#### Scenario: PEM 公钥可被前端 sm-crypto 解析
- **WHEN** 后端通过 `/system/publicKey` 返回 PEM 公钥
- **THEN** 前端 sm-crypto 0.3.13 MUST 能从 PEM 中提取 hex 公钥并完成 SM2 加密
