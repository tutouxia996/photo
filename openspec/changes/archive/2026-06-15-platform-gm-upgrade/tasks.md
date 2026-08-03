    # Implementation Tasks

## 1. 阶段一：sq-dependencies-core 依赖收敛与版本治理（v1.1.0）

- [x] 1.1 升级 sq-dependencies-core 根 POM 与 4 个子模块 POM `<version>` 至 `1.1.0`
- [x] 1.2 在父 POM `<properties>` 新增国密版本：`bouncycastle.version=1.70`
- [x] 1.3 在父 POM `<dependencyManagement>` 新增 `bcprov-jdk15on:1.70` 与 `bcpkix-jdk15on:1.70`
- [x] 1.4 在父 POM `<dependencyManagement>` 显式管理：mybatis-plus、mybatis-plus-join、dynamic-datasource、hutool、swagger-annotations、commons-io、commons-fileupload、velocity、zxing、redisson、p6spy 十一项（从 sq-common 硬编码或 sq-fast 子 POM 硬编码迁出）
- [x] 1.5 更新父 POM `<properties>` 中的版本属性值：fastjson2 2.0.20→2.0.56+、druid 1.2.15→1.2.24、pagehelper 1.4.6→1.4.7、mybatis-plus 3.5.1→3.5.8（JDK 1.8 天花板）、mybatis-plus-join 1.4.5→1.4.12、hutool 5.8.8→5.8.34、dynamic-datasource 3.5.2→3.6.1、commons-io 2.11.0→2.18.0、velocity 2.3→2.4、swagger-annotations 1.5.22→1.6.14、redisson 3.16.2→3.36.0+、zxing 3.4.1→3.5.3、p6spy 3.9.1→3.9.2
- [x] 1.6 在父 POM `<properties>` 新增 `<mysql.version>8.0.33</mysql.version>`（覆盖 Spring Boot BOM 默认 8.0.28）
- [x] 1.7 在父 POM `<dependencyManagement>` 新增：mysql-connector-java（版本引用 mysql.version）
- [x] 1.8 在父 POM 中**移除** jjwt 与 esdk-obs-java-bundle 的 dependencyManagement 与 version property
- [x] 1.9 在父 POM 中**移除**所有 springfox 相关依赖与 property
- [x] 1.10 在父 POM 中**移除** knife4j-openapi2-spring-boot-starter 依赖与 property（随 springfox 移除）
- [x] 1.11 修改 sq-common/pom.xml：删除 mybatis-plus / dynamic-datasource / mybatis-plus-join / hutool / swagger-annotations / esdk-obs-java-bundle 六项 `<version>` 标签（保留 `<dependency>` 声明，版本由父 POM 管理）
- [x] 1.12 修改 sq-common/pom.xml：**移除** esdk-obs-java-bundle 整个 `<dependency>` 块（OBS 移出平台核心包）
- [x] 1.13 修改 sq-framework/pom.xml、sq-quartz/pom.xml、sq-generator/pom.xml：将对 sq-common 的 `<version>1.0.3</version>` 改为 `<version>${project.version}</version>`
- [x] 1.14 在 sq-common/pom.xml 新增 `bcprov-jdk15on` 与 `bcpkix-jdk15on` 依赖（不写 version，由父 POM 管理）
- [x] 1.15 在 sq-fast/sq-business/pom.xml 新增 esdk-obs-java-bundle 依赖（业务模块直接持有）；删除 sq-business/pom.xml 中 redisson 3.16.2 硬编码版本（版本已上提至父 POM 管理）
- [x] 1.16 sq-dependencies-core 根目录执行 `mvn -T 1C clean install -DskipTests` 验证编译成功
- [x] 1.17 执行 `mvn dependency:tree -pl sq-common` 验证 sq-common 不再传递 OBS 依赖
- [x] 1.18 执行 `mvn dependency:tree -Dincludes=io.springfox` 与 `-Dincludes=io.jsonwebtoken` 验证两者均为空
- [x] 1.19 验证 `mvn help:effective-pom` 中 mysql-connector-java 版本为 8.0.33
- [x] 1.20 确认 poi 4.1.2、oshi 6.4.0、spring-cloud-commons 2.0.1.RELEASE、webmagic 0.7.3 版本保持不动（JDK 1.8 天花板）
- [ ] 1.21 git tag `v1.1.0-deps-aligned`

## 2. 阶段二：sq-fast 父 POM 清理与 CVE 修复（同 v1.1.0）

- [x] 2.1 升级 sq-fast 根 POM 与 sq-system / sq-business / sq-admin-server 子模块 POM `<version>` 至 `1.1.0`
- [x] 2.2 升级 sq-fast 父 POM 中 sq-dependencies-core 4 个模块的引用版本至 `1.1.0`（`<sq.version>1.1.0</sq.version>`）
- [x] 2.3 修复 sq-fast 父 POM commons-fileupload 的 `${version}` → `${commons-fileupload.version}` 或显式版本（已随 2.5 移除整条 dm 条目）
- [x] 2.4 修复 sq-fast 父 POM oshi 的 `${version}` → 正确变量（已随 2.5 移除整条 dm 条目）；并将 sq-system / sq-business 引用 `${version}` → `${sq.version}`
- [x] 2.5 删除 sq-fast 父 POM 中 14 项重复 dependencyManagement：druid、UserAgentUtils、pagehelper、oshi、springfox、commons-io、commons-fileupload、poi、velocity、commons-collections、fastjson2、jjwt、kaptcha、knife4j
- [x] 2.6 删除 sq-fast 父 POM `<properties>` 中上述 14 项对应的 version property
- [x] 2.7 sq-fast 父 POM 新增 `<dependency type=pom scope=import>` 引入 `sq-dependencies-core` BOM；保留并继续管理：webmagic、达梦 JDBC、GaussDB JDBC、spring-cloud-commons（zxing、p6spy、mysql-connector-java 已上提至 sq-dependencies-core 父 POM 管理，sq-fast 不再管理）
- [x] 2.8 删除 sq-fast 中所有引用 springfox 的配置类与 import（实测 sq-fast 全工程无 springfox/SwaggerConfig/Knife4jConfig 残留，全部位于 sq-dependencies-core/sq-framework，已在 Phase 1 清理）
- [x] 2.9 sq-fast 根目录执行 `mvn -T 1C clean install -DskipTests` 验证编译成功（mybatis-plus 3.5.9 → 3.5.8 降级，因 3.5.9 起 Pagination/BlockAttack 拆出到 jsqlparser 模块且为 JDK 11 字节码，违反 JDK 1.8 天花板；sq-admin-server 临时保留 jjwt 0.9.1 直接依赖，待 Phase 4 task 4.16–4.18 替换为 SM3-HMAC JWT 后移除）
- [x] 2.10 执行 `mvn dependency:tree -Dincludes=io.springfox` 验证空输出
- [x] 2.11 执行 `mvn dependency:tree -Dincludes=io.jsonwebtoken` 验证仅 sq-admin-server 临时引用 jjwt 0.9.1（Phase 4 移除）
- [x] 2.12 执行 `mvn dependency:tree -Dincludes=org.bouncycastle` 验证仅 1.70 jdk15on
- [ ] 2.13 git tag `v1.1.0-cve-fixed`

## 3. 阶段三：通用工具与基类下沉至 sq-common

- [x] 3.1 在 sq-common 新增 `com.sq.common.core.domain.ILoginUser` 接口（含 `getUserId()` / `getDeptId()` / `getUsername()`）
- [x] 3.1.1 在 sq-common 新增 `com.sq.common.core.domain.ITreeSelectable` 接口（解耦 TreeSelect 与 SysDept/SysMenu）
- [x] 3.1.2 在 sq-common 新增 `com.sq.common.core.domain.IDictData` 接口（解耦 DictUtils 与 SysDictData）
- [x] 3.2 迁移 `TreeEntity` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.core.domain`
- [x] 3.3 迁移 `TreeSelect` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.core.domain`；构造器签名改为 `TreeSelect(ITreeSelectable)`
- [x] 3.4 迁移 `BaseController` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.core.controller`；`getLoginUser()` 返回 `ILoginUser`
- [x] 3.5 迁移 `SecurityUtils` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.utils`；将 `getLoginUser()` 返回值改为 `ILoginUser`；BCrypt 方法标注 `@Deprecated`
- [x] 3.6 迁移 `DictUtils` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.utils`；新增 `setDefaultDictDataClass(Class)` 兼容入口，业务在启动期注入 `SysDictData.class`
- [x] 3.7 迁移 `ExcelUtil` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.utils.poi`
- [x] 3.8 迁移 `GeneratorUtil` 从 sq-fast/sq-system 至 sq-common，包路径 `com.sq.common.utils`
- [x] 3.9 sq-fast 中的 `LoginUser` 类增加 `implements ILoginUser`，实现三个接口方法
- [x] 3.9.1 sq-fast 中的 `SysDept` 增加 `implements ITreeSelectable`（getTreeId/getTreeLabel/getTreeChildren 委托至 deptId/deptName/children）
- [x] 3.9.2 sq-fast 中的 `SysMenu` 增加 `implements ITreeSelectable`（getTreeId/getTreeLabel/getTreeChildren 委托至 menuId/menuName/children）
- [x] 3.9.3 sq-fast 中的 `SysDictData` 增加 `implements IDictData`（已有 getDictLabel/getDictValue 方法，无需新增）
- [x] 3.9.4 sq-fast 中的 `AdminApplication` 启动期调用 `DictUtils.setDefaultDictDataClass(SysDictData.class)`
- [x] 3.10 删除 sq-fast 中已迁移的 7 个原始类文件
- [x] 3.11 sq-fast 全工程编译，依据编译错误逐一修正 import 与 LoginUser 显式向下转型（13 处 `(LoginUser) SecurityUtils.getLoginUser()` / `(LoginUser) getLoginUser()`，1 处 `((LoginUser) SecurityUtils.getLoginUser()).getUser()`，SysLoginController 增加 `LoginUser` import）
- [x] 3.12 验证 sq-common `mvn dependency:tree` 不依赖 sq-fast 任何模块（sq-dependencies-core 与 sq-fast 双工程 BUILD SUCCESS）
- [ ] 3.13 git tag `v1.1.0-model-extracted`

## 4. 阶段四：国密替换（SM2 / SM3 / JWT / 前端）

- [x] 4.1 在 sq-common 新增 `com.sq.common.crypto.Sm2Utils`：`encrypt` / `decrypt`（C1C3C2 模式）、`toPem` / `fromPem`、`generateKeyPair`
- [x] 4.2 在 sq-common 新增 `com.sq.common.crypto.Sm3Utils`：`digest(bytes)`、`hmac(key, bytes)`
- [x] 4.3 在 sq-common 新增 `com.sq.common.crypto.Sm4Utils`：`encrypt` / `decrypt`（ECB / CBC）
- [x] 4.4 在 sq-common 新增 `com.sq.common.crypto.Sm2KeystoreLoader`：按 `external > classpath` 顺序加载 PKCS12，外部失败拒绝启动
- [x] 4.5 在 sq-common 新增 `com.sq.common.crypto.Sm2KeystoreGenerator` 命令行工具：生成 PKCS12 文件
- [x] 4.6 在 sq-common 新增 `com.sq.common.crypto.Sm3PasswordEncoder`：实现 Spring Security `PasswordEncoder`，编码格式 `{sm3}<base64(salt)>$<hex(SM3(salt||raw))>`，`matches` 仅识别 `{sm3}` 前缀
- [x] 4.7 sq-common 添加 BouncyCastle Provider 注册（静态初始化 `Security.addProvider(new BouncyCastleProvider())`）
- [x] 4.8 sq-common 编写国标测试向量单元测试：SM2 加解密往返、SM3 摘要、SM3-HMAC、`Sm3PasswordEncoder` 编码与校验
- [x] 4.9 sq-common 编写前后端互通测试：使用 sm-crypto 0.3.13 已知密文样本验证 `Sm2Utils.decrypt` 正确解密
- [x] 4.10 sq-admin-server 中执行 `Sm2KeystoreGenerator` 生成开发态 `keystore/sq-sm2.p12`，放入 `src/main/resources/keystore/`
- [x] 4.11 sq-admin-server `application.yml` 新增 `sq.crypto.sm2` 配置块（external/classpath 双路径、环境变量密码占位符）
- [x] 4.12 sq-admin-server `application.yml` 修改 `token.secret` 为 `${SQ_JWT_SECRET:<dev-default>}`
- [x] 4.13 sq-admin-server 新增 `SysPublicKeyController`：`GET /system/publicKey` 返回 PEM 公钥，permitAll
- [x] 4.14 sq-admin-server `SecurityConfig` 中：将 `BCryptPasswordEncoder` 替换为 `Sm3PasswordEncoder`；在 permitAll 路径中加入 `/system/publicKey`
- [x] 4.15 sq-admin-server `SysLoginService.login()` 在 Spring Security 认证前增加 SM2 解密步骤；解密失败抛出明确异常
- [x] 4.16 sq-admin-server `TokenService` 重写 `createToken` 与 `parseToken`：自实现三段 JWT，签名算法 SM3-HMAC，header `alg=SM3-HMAC`；移除 jjwt import
- [x] 4.17 sq-admin-server `JwtAuthenticationTokenFilter` 调用新版 `TokenService.parseToken`，验证 `alg=SM3-HMAC`，否则拒绝
- [x] 4.18 sq-admin-server 全模块搜索 `io.jsonwebtoken` import，全部移除
- [x] 4.19 sq-admin-server 编译并运行启动冒烟（验证 BC Provider 注册、keystore 加载、`/system/publicKey` 返回 PEM）
- [x] 4.20 sq-ui (Vue2) `package.json`：移除 `jsencrypt`，新增 `"sm-crypto": "0.3.13"`，执行 `npm install`
- [x] 4.21 sq-ui (Vue2) 重写 `src/utils/jsencrypt.js` → `src/utils/smcrypto.js`：导出 `fetchPublicKey()` 与 `encryptPassword(plaintext)`，使用 `sm-crypto.sm2.doEncrypt(text, pubKey, 1)`
- [x] 4.22 sq-ui (Vue2) `src/views/login.vue`：在登录提交前调用 `encryptPassword`，移除任何硬编码公钥
- [x] 4.23 sq-ui-vue3 `package.json`：移除 `jsencrypt`，新增 `"sm-crypto": "0.3.13"`，执行 `npm install`
- [x] 4.24 sq-ui-vue3 重写 `src/utils/jsencrypt.js` → `src/utils/smcrypto.js`，逻辑同 4.21
- [x] 4.25 sq-ui-vue3 `src/views/login.vue` 同 4.22
- [x] 4.26 两套前端搜索 `BEGIN PUBLIC KEY` 与 `JSEncrypt`，确认全部清零
- [x] 4.27 准备 admin 默认密码 `Sq@admin2025` 经 `Sm3PasswordEncoder` 编码后的字面量（运行一次性脚本生成）
- [x] 4.28 同步更新 `doc/db/mysql_init.sql`、`doc/db/dm_init.sql`、`doc/db/gaussdb_init.sql` 中 admin 用户 password 字段为该 `{sm3}` 字面量
- [x] 4.29 准备生产批量重置 SQL `doc/db/reset_passwords_sm3.sql`：将所有现存用户密码重置为 `{sm3}` 临时密码，并备份原 BCrypt 字段至 `backup_pwd_<timestamp>` 表
- [x] 4.30 修正 jsqlparser 版本约束：BOM 锁定从 4.9 改回 4.6（pagehelper 5.3.3 编译期依赖 SelectBody，jsqlparser 4.7+ 已删除该类型；mp 3.5.8 PaginationInnerInterceptor 引用 4.7+ 的 ParenthesedSelect / SelectItem(class)，二者在任一 jsqlparser 版本下不可调和）**【已被 4.34 取代——4.6 方案在运行时仍触发 mp `BlockAttackInnerInterceptor` 调用 `parseStatements(String,ExecutorService,Consumer)` 的 NoSuchMethodError，因 mp 全套 InnerInterceptor 都依赖 jsqlparser 4.7+ API】**
- [x] 4.31 sq-admin-server `MybatisPlusConfig.mybatisPlusInterceptor()` 移除 `paginationInnerInterceptor()` 挂载，仅保留乐观锁与阻断插件；删除 `paginationInnerInterceptor()` 方法及 `DbType` / `PaginationInnerInterceptor` import **【已被 4.36 回退】**
- [x] 4.32 `BusKeywordController.list()` 与 `export()`：mp `Page<T>` + `lambdaQuery().page(...)` 改为 `PageHelper.startPage(int,int)` + `lambdaQuery().list()` + `new PageInfo<>(list)`，与项目其他 controller 的 PageHelper 模式对齐 **【保留——双栈共存策略下 PageHelper 风格亦合规】**
- [x] 4.34 升级 pagehelper-spring-boot-starter 1.4.7 → 2.1.1（内置 pagehelper 6.1.1，已基于 jsqlparser 4.7+ API 重写 CountSqlParser；JDK 8 + Spring Boot 2.5.x 兼容）；jsqlparser 锁定改 4.6 → 4.9，使 pagehelper 6.x 与 mp 3.5.8 全套 InnerInterceptor 在同一 jsqlparser 版本下共存
- [x] 4.35 重写 `sq-dependencies-core/pom.xml` 的 jsqlparser 锁定决策注释：从「4.6 与 mp 不可调和，禁用 mp 分页插件」改为「pagehelper-spring-boot-starter 2.1.1 + mp 3.5.8 + jsqlparser 4.9 标准组合，分页双栈共存」
- [x] 4.36 sq-admin-server `MybatisPlusConfig.mybatisPlusInterceptor()` 恢复 `PaginationInnerInterceptor(DbType.MYSQL)` 挂载（位于乐观锁与阻断插件之前）；恢复 `DbType` / `PaginationInnerInterceptor` import；更新决策注释说明双栈共存
- [x] 4.37 构建验证：`mvn -pl sq-dependencies-core install -DskipTests` BUILD SUCCESS；`mvn -pl sq-fast/sq-admin-server clean package -DskipTests` BUILD SUCCESS；`target/lib/` 含 `jsqlparser-4.9.jar` + `pagehelper-6.1.1.jar` + `pagehelper-spring-boot-starter-2.1.1.jar`，旧版本 `jsqlparser-4.6.jar` / `pagehelper-5.3.3.jar` 不再出现；`mvn dependency:tree` 确认 effective 依赖：`pagehelper-spring-boot-starter:2.1.1 → pagehelper-spring-boot-autoconfigure:2.1.1 → pagehelper:6.1.1 → jsqlparser:4.9`，BOM 强制覆盖 pagehelper 6.1.1 默认的 jsqlparser 4.7 至 4.9 生效
- [ ] 4.38 IDEA Reload Maven + 重启 AdminApplication，端到端回归：`SysConfigController.edit`（验证 `BlockAttackInnerInterceptor` 不再 `NoSuchMethodError`）+ 任意 PageHelper 风格分页接口（如 `BusKeywordController.list`）+ 任意 mp `Page<>` 风格分页接口
- [ ] 4.33 git tag `v1.1.0-gm-ready`

## 5. 阶段五：联调验证与发版

- [ ] 5.1 在 staging 环境执行批量重置 SQL，备份 BCrypt 原值
- [ ] 5.2 部署 sq-admin-server 至 staging，注入 `SQ_KEYSTORE_PWD` / `SQ_KEY_PWD` / `SQ_JWT_SECRET` 环境变量
- [ ] 5.3 部署 sq-ui 与 sq-ui-vue3 至 staging
- [ ] 5.4 端到端验证：admin 用 `Sq@admin2025` 登录成功，断言请求 payload 中密码为 SM2 密文
- [ ] 5.5 端到端验证：登录响应 JWT header `alg=SM3-HMAC`（base64url 解码 header 验证）
- [ ] 5.6 端到端验证：携带新 JWT 调用受保护接口成功
- [ ] 5.7 篡改 JWT 签名后调用受保护接口，验证返回 401
- [ ] 5.8 用旧 BCrypt 密码尝试登录，验证拒绝
- [ ] 5.9 三种数据库（MySQL / DM / GaussDB）逐一执行 init.sql 与冒烟登录
- [ ] 5.10 在国产化目标 OS（麒麟 / 统信 UOS / 欧拉）任选一种验证 BC 1.70 加载与登录闭环
- [ ] 5.11 故意删除外部 keystore 文件验证启动失败、错误日志明确、无静默 fallback
- [ ] 5.12 提前一周向所有用户发布密码重置通知与找回密码通道说明
- [ ] 5.13 生产部署窗口：执行批量重置 SQL → 部署后端 → 部署前端 → admin 冒烟
- [ ] 5.14 上线后 1 小时监控登录成功率，>95% 视为通过；<95% 触发回滚
- [ ] 5.15 git tag `v1.1.0-release`，发布 sq-dependencies-core 1.1.0 与 sq-fast 1.1.0
- [ ] 5.16 运行 `openspec status --change platform-gm-upgrade` 确认全部 done，准备 archive
