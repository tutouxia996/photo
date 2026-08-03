## Why

当前 sq-dependencies-core（v1.0.3）与 sq-fast（v1.0.0）存在四类突出问题：

1. **安全合规缺口**：登录链路使用国际算法（RSA-1024 / BCrypt / MD5 / HS512），不满足国密合规；jjwt 0.9.1、commons-fileupload 1.4、poi 4.1.2、fastjson2 2.0.20 等存在已知 CVE。
2. **依赖治理混乱**：sq-fast/pom.xml 完全重复声明了 sq-dependencies-core 的 14 项版本属性与依赖；sq-common 中 mybatis-plus、dynamic-datasource、hutool、swagger-annotations、mybatis-plus-join、esdk-obs-java-bundle 等 6 项版本硬编码；sq-framework / sq-quartz / sq-generator 中 sq-common 引用版本写死为 `1.0.3`；sq-fast/pom.xml 第 201、208 行误用 Maven 内置变量 `${version}`。
3. **复用度不足**：sq-fast/sq-system 中 TreeEntity、TreeSelect、SecurityUtils、DictUtils、ExcelUtil、GeneratorUtil、BaseController 等通用工具与基类未沉淀至平台依赖包。
4. **依赖职责越位**：sq-common 引入华为云 OBS SDK（esdk-obs-java-bundle），属业务依赖污染平台核心包。

借此机会一次性完成平台依赖包升级（v1.0.3 → v1.1.0）与业务应用国密替换，建立可复用的国密能力底座。

## What Changes

- **BREAKING** 密码哈希 BCrypt → SM3+盐，旧密码不兼容（强制重置，admin 默认密码改为 `Sq@admin2025`）
- **BREAKING** JWT 签名 HS512 → SM3-HMAC，旧 token 全部失效
- **BREAKING** 前端登录密码加密 RSA（jsencrypt）→ SM2（sm-crypto），公钥由 `GET /system/publicKey` 动态下发，不再硬编码
- **BREAKING** sq-dependencies-core 升级至 v1.1.0；sq-fast/pom.xml 中 14 项与平台包重复的依赖与版本属性被删除
- **BREAKING** OBS SDK 从 sq-common 移除，下沉至 sq-fast/sq-business
- 在 sq-common 新增国密工具：`Sm2Utils` / `Sm3Utils` / `Sm4Utils` / `Sm3PasswordEncoder`
- 在 sq-common 新增 PKCS12 keystore SM2 密钥管理：`Sm2KeyStoreLoader` / `Sm2KeyProvider` / `Sm2KeystoreProperties`，并提供命令行密钥生成工具 `Sm2KeystoreGenerator`
- 在 sq-common 新增 `BouncyCastleProviderConfig`，启动时注册 BC Provider 1.70
- 在 sq-common 新增 `ILoginUser` 接口（包路径 `com.sq.common.core.domain.ILoginUser`），sq-fast 的 `LoginUser` 实现该接口，解耦 SecurityUtils 与 RBAC 实体
- 从 sq-fast/sq-system 提炼 7 类下沉至 sq-common：TreeEntity、TreeSelect、SecurityUtils、DictUtils、ExcelUtil、GeneratorUtil、BaseController
- CVE 与依赖升级（基于 JDK 1.8 天花板）：commons-fileupload 1.4→1.5、fastjson2 2.0.20→2.0.56+、mysql-connector-java（SB BOM 8.0.28）→8.0.33、mybatis-plus 3.5.1→3.5.8、mybatis-plus-join 1.4.5→1.4.12、hutool 5.8.8→5.8.34、druid 1.2.15→1.2.24、pagehelper 1.4.6→1.4.7、commons-io 2.11.0→2.18.0、velocity 2.3→2.4、zxing 3.4.1→3.5.3、p6spy 3.9.1→3.9.2、redisson 3.16.2→3.36.0+、dynamic-datasource 3.5.2→3.6.1、swagger-annotations 1.5.22→1.6.14
- jjwt 0.9.1→移除（自实现 JWT 三段拼接，不依赖 jjwt）
- 移除 springfox-boot-starter 与 knife4j-openapi2-spring-boot-starter（springfox 停止维护，本期不引入替代品）
- 三份数据库初始化脚本（mysql_init.sql / dm_init.sql / gaussdb_init.sql）admin 密码哈希更新为 SM3 格式
- **保持不变**：JDK 1.8、Spring Boot 2.5.14、Spring Security 5.x、poi 4.1.2（5.x 需 JDK 9+）、oshi 6.4.0（6.5+ 需 JDK 11+）、spring-cloud-commons 2.0.1.RELEASE（3.x 需 JDK 11+）、webmagic 0.7.3（项目停更无替代）；TokenService 与 JwtAuthenticationTokenFilter 保留在 sq-fast/sq-admin-server；RBAC 实体（SysUser/SysRole/SysMenu/SysDept/SysDictType/SysDictData）与登录模型（LoginUser/LoginBody/RegisterBody）保留在 sq-fast/sq-system

## Capabilities

### New Capabilities

- `platform-dependency-management`: sq-dependencies-core 平台依赖包的版本统一治理能力——第三方依赖版本属性、子模块互引版本、CVE 修复版本均由父 POM 集中声明；业务应用不得重复声明已由平台包管理的依赖；OBS 等业务 SDK 不得驻留 sq-common。
- `gm-crypto-toolkit`: 国密算法工具包能力——基于 BouncyCastle 1.70 提供 SM2 加解密与签名验签、SM3 摘要与 HMAC、SM4 对称加密的统一封装；应用启动时注册 BouncyCastleProvider；业务代码必须经由本工具包调用。
- `sm2-keystore-management`: SM2 密钥的 PKCS12 keystore 管理能力——支持 classpath 与外部路径双源加载（外部优先）、keystore 与 key 密码通过环境变量注入、提供命令行密钥生成工具、对外暴露 SM2 公私钥获取接口。
- `gm-password-encoding`: 国密密码编码能力——基于 SM3+盐实现 Spring Security 的 `PasswordEncoder`，编码格式 `{sm3}<salt>$<hash>`；不兼容 BCrypt 旧哈希；强制全员重置密码上线。
- `gm-jwt-signing`: 国密 JWT 签名能力——使用 SM3-HMAC 对 JWT 进行三段（header.payload.signature）签名验签；自实现 JWT 编解码，不依赖 jjwt 签名机制；签名密钥由配置项管理。
- `gm-frontend-encryption`: 前端登录密码国密传输能力——前端通过 `/system/publicKey` 动态获取 SM2 公钥；使用 sm-crypto 0.3.13 加密；后端用私钥解密后再 SM3 校验；Vue2 与 Vue3 双端同步改造。
- `platform-common-toolkit`: 平台通用工具与基类沉淀能力——TreeEntity / TreeSelect / BaseController / SecurityUtils / DictUtils / ExcelUtil / GeneratorUtil 由 sq-fast 下沉至 sq-common；通过 `ILoginUser` 接口解耦 SecurityUtils 与具体 RBAC 实体。

### Modified Capabilities

<!-- 当前仓库 openspec/specs/ 为空，无既有能力规格需要修改。 -->

## Impact

**平台依赖包 sq-dependencies-core：**
- `pom.xml`（父）：新增国密依赖、统一版本管理、版本号升至 1.1.0
- `sq-common`：新增国密工具与 keystore、迁入 7 个通用类、移除 OBS、删除硬编码版本
- `sq-framework` / `sq-quartz` / `sq-generator`：sq-common 引用改 `${project.version}`

**业务应用 sq-fast：**
- `pom.xml`：删除 14 项重复声明、修复 `${version}` BUG、依赖 sq-dependencies-core 1.1.0
- `sq-admin-server`：SecurityConfig PasswordEncoder 切换、TokenService JWT 算法切换、新增 `SysPublicKeyController`、SysLoginService 增加 SM2 解密、application.yml 新增 `sq.crypto.sm2.*` 配置、新增 keystore 资源
- `sq-system`：删除 7 个已迁出的文件、LoginUser 实现 ILoginUser
- `sq-business`：新增 esdk-obs-java-bundle 依赖
- `sq-ui`（Vue2）：移除 jsencrypt、新增 sm-crypto、改造 jsencrypt.js → sm-crypto.js、login.vue 同步修改、新增 publicKey API
- `sq-ui-vue3`（Vue3）：同上
- `doc/db/*.sql`：三份脚本 admin 密码哈希更新为 SM3 格式

**API 影响：**
- 新增 `GET /system/publicKey`（permitAll，返回 PEM 格式 SM2 公钥）
- `POST /login`：password 字段语义变更（前端必须先 SM2 加密）
- 所有 JWT-protected 接口：旧 token 失效，发版后用户必须重新登录

**运行环境影响：**
- 类路径必须能加载 BouncyCastle 1.70
- 必须存在 PKCS12 格式的 SM2 keystore（dev 随 jar；prod 通过外部路径 + 环境变量）
- 生产部署需注入环境变量 `SQ_KEYSTORE_PWD`、`SQ_KEY_PWD`

**用户影响：**
- 旧密码作废，必须使用初始重置密码登录后强制改密
- 当前会话失效（JWT 不兼容），需重新登录

**版本：**
- sq-dependencies-core: 1.0.3 → 1.1.0
- sq-fast: 1.0.0 → 1.1.0
