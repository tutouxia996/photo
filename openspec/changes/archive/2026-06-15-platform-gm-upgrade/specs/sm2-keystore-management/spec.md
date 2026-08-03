## ADDED Requirements

### Requirement: SM2 密钥使用 PKCS12 keystore 存储

平台 SHALL 使用 PKCS12 格式（`.p12`）keystore 存储 SM2 密钥对。MUST NOT 使用 JKS 格式。MUST NOT 在 yml、properties 或代码中以明文形式存储 SM2 私钥。

#### Scenario: 密钥文件格式
- **WHEN** 检查 SM2 密钥文件
- **THEN** 文件扩展名 SHALL 为 `.p12`
- **AND** keystore 类型 SHALL 为 `PKCS12`
- **AND** 通过 BouncyCastle Provider 加载

### Requirement: keystore 加载策略 external > classpath

应用启动时 SHALL 按以下顺序加载 keystore：当 `keystore-external-path` 配置项非空时加载该绝对路径文件；否则加载 `keystore-path` 指定的 classpath 资源。外部路径加载失败时 MUST 启动失败并记录明确错误日志，MUST NOT 静默 fallback 到 classpath。

#### Scenario: 外部路径优先
- **WHEN** `sq.crypto.sm2.keystore-external-path` 配置为 `/etc/sq/sq-sm2.p12` 且文件存在可读
- **THEN** 应用 SHALL 加载该外部文件作为 SM2 密钥源
- **AND** MUST NOT 加载 classpath 中的 keystore

#### Scenario: 外部路径加载失败拒绝启动
- **WHEN** `keystore-external-path` 配置非空但文件不存在或密码错误
- **THEN** 应用 SHALL 启动失败
- **AND** 日志中 MUST 包含明确的失败原因（路径、错误类型）
- **AND** MUST NOT 自动回退到 classpath

#### Scenario: classpath fallback
- **WHEN** `keystore-external-path` 为空且 `keystore-path: classpath:keystore/sq-sm2.p12` 存在
- **THEN** 应用 SHALL 加载 classpath 中的 keystore

### Requirement: keystore 与 key 密码通过环境变量注入

keystore 密码与 key 密码 SHALL 通过环境变量 `SQ_KEYSTORE_PWD` 与 `SQ_KEY_PWD` 注入。配置文件中 SHALL 使用占位符 `${SQ_KEYSTORE_PWD}` 与 `${SQ_KEY_PWD}` 引用。生产环境 MUST NOT 提交 keystore 文件或密码到代码仓库。

#### Scenario: 配置项使用环境变量占位符
- **WHEN** 检查 application.yml
- **THEN** `sq.crypto.sm2.keystore-password` MUST 写为 `${SQ_KEYSTORE_PWD:changeit}` 形式
- **AND** `sq.crypto.sm2.key-password` MUST 写为 `${SQ_KEY_PWD:changeit}` 形式

#### Scenario: 生产 keystore 不入库
- **WHEN** 检查 git 仓库与构建产物
- **THEN** 生产环境的 `sq-sm2.p12` MUST NOT 在仓库中
- **AND** `.gitignore` SHALL 包含 `*.p12` 或显式排除生产 keystore 路径

### Requirement: 提供 SM2 密钥生成命令行工具

sq-common SHALL 提供 `Sm2KeystoreGenerator` 命令行工具，用于生成 PKCS12 格式 SM2 密钥对，输出 `.p12` 文件，供运维人员在部署环境本地执行。

#### Scenario: 生成密钥
- **WHEN** 运维执行 `java -cp sq-common.jar com.sq.common.crypto.Sm2KeystoreGenerator --output sq-sm2.p12 --alias sq-sm2-key --keystore-pwd <pwd> --key-pwd <pwd>`
- **THEN** 工具 SHALL 在指定路径生成 PKCS12 文件
- **AND** 文件 MUST 能被 `Sm2KeystoreLoader` 正确加载并取出公私钥
