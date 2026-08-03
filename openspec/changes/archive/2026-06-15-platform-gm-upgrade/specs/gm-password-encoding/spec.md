## ADDED Requirements

### Requirement: Sm3PasswordEncoder 替换 BCryptPasswordEncoder

平台 SHALL 提供 `Sm3PasswordEncoder` 实现 Spring Security 的 `PasswordEncoder` 接口，作为唯一密码编码器。SecurityConfig 中 MUST NOT 再注册 `BCryptPasswordEncoder`。

#### Scenario: SecurityConfig 注册 Sm3PasswordEncoder
- **WHEN** 检查 sq-admin-server 的 SecurityConfig
- **THEN** `passwordEncoder()` Bean MUST 返回 `Sm3PasswordEncoder` 实例
- **AND** MUST NOT 引用 `BCryptPasswordEncoder`

### Requirement: 密码编码格式

`Sm3PasswordEncoder.encode(rawPassword)` SHALL 输出格式 `{sm3}<base64(salt)>$<hex(SM3(salt || rawPassword))>`。salt 长度 SHALL 为 16 字节，由 `SecureRandom` 生成。

#### Scenario: 编码输出格式
- **WHEN** 调用 `encoder.encode("Sq@admin2025")`
- **THEN** 返回字符串 SHALL 以 `{sm3}` 前缀开头
- **AND** 前缀后 SHALL 为 base64 编码的 16 字节 salt
- **AND** 中间 SHALL 为 `$` 分隔符
- **AND** 末尾 SHALL 为 hex 编码的 32 字节 SM3 摘要

#### Scenario: 同一明文每次编码输出不同
- **WHEN** 对同一 rawPassword 连续调用 `encode` 两次
- **THEN** 两次输出 SHALL 不同（salt 随机）
- **AND** 两次输出 SHALL 均能通过 `matches(rawPassword, encoded)` 校验

### Requirement: 不兼容旧 BCrypt 密码

`Sm3PasswordEncoder.matches()` SHALL 仅识别 `{sm3}` 前缀。对于 `$2a$` / `$2b$` / `$2y$` 等 BCrypt 前缀或任何其他格式，MUST 直接返回 false。

#### Scenario: BCrypt 密码被拒绝
- **WHEN** 调用 `matches("anyPassword", "$2a$10$abcdef...")`
- **THEN** 返回值 MUST 为 false
- **AND** MUST NOT 调用 BCrypt 的校验逻辑

#### Scenario: 非 sm3 前缀被拒绝
- **WHEN** 调用 `matches("anyPassword", "plaintext_no_prefix")`
- **THEN** 返回值 MUST 为 false

### Requirement: 上线时强制重置全部用户密码

上线流程 SHALL 包含批量 SQL 脚本，将所有现存用户的密码字段重置为 `{sm3}` 格式临时密码。admin 用户 SHALL 重置为 `Sq@admin2025` 对应的 SM3 编码。脚本 SHALL 同步覆盖 MySQL / 达梦 DM / GaussDB 三份初始化脚本。

#### Scenario: 三种数据库 admin 默认密码一致
- **WHEN** 检查 `doc/db/mysql_init.sql`、`doc/db/dm_init.sql`、`doc/db/gaussdb_init.sql`
- **THEN** 三份脚本中 admin 用户的 password 字段 MUST 均为 `Sq@admin2025` 经 `Sm3PasswordEncoder` 编码后的字面量
- **AND** 三份脚本中 admin 密码 MUST NOT 出现 `$2a$` 或其他 BCrypt 格式
