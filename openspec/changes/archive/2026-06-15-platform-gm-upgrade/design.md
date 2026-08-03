## Context

**当前状态：**
- sq-dependencies-core v1.0.3：4 个子模块（sq-common / sq-framework / sq-quartz / sq-generator），父 POM 管理 14 项依赖版本，子模块中存在 6 处硬编码版本。
- sq-fast v1.0.0：3 个后端模块（sq-admin-server / sq-system / sq-business）+ 2 个前端项目（sq-ui Vue2 / sq-ui-vue3 Vue3）。sq-fast 父 POM 重复声明了与 sq-dependencies-core 完全一致的 14 项 dependencyManagement，第 201/208 行误用 `${version}`。
- 安全栈：BCrypt（密码）+ HS512 jjwt 0.9.1（JWT）+ RSA-1024 jsencrypt 硬编码公钥（前端密码）+ MD5（摘要）。
- 三种数据库支持：MySQL / 达梦 DM / GaussDB。

**约束（用户决策）：**
- JDK 1.8、Spring Boot 2.5.14、Spring Security 5.x 保留。
- 模型提炼范围：方案 B（仅工具与基类，不含 RBAC 实体）。
- TokenService 与 JwtAuthenticationTokenFilter 不下沉，保留 sq-fast/sq-admin-server。
- 不做旧 BCrypt 密码兼容，强制重置。
- SM2 密钥用 PKCS12 keystore + 环境变量密码。
- 不引入双轨开关，国密直接替换。

**利益相关方：** 平台依赖包维护者 / sq-fast 后端开发 / 前端开发（Vue2 + Vue3）/ 运维 / 终端用户。

## Goals / Non-Goals

**Goals:**
- 平台依赖包 v1.1.0：版本统一治理，消除散落硬编码与重复声明，修复已知 CVE。
- 国密合规：登录链路全链路替换为 SM2 / SM3，建立可复用国密能力底座。
- 强化安全：keystore 管理 SM2 密钥，环境变量注入密码，禁止硬编码。
- 提高复用度：通用工具与基类下沉至 sq-common。
- 边界清晰：业务依赖（OBS）不污染平台核心包。

**Non-Goals:**
- 不升级 JDK 与 Spring Boot 主版本。
- 不下沉 RBAC 实体。
- 不下沉 TokenService 至 sq-framework。
- 不做旧密码兼容。
- 不引入双轨国密开关。
- 不本次迁移 Springfox→Springdoc（仅移除 springfox）。
- 不本次实现 HTTPS 国密 TLS（GM-TLS）。
- 不变更 RBAC 数据表结构（仅更新 admin 密码哈希字段值）。

## Decisions

### D1：JWT 签名算法选 SM3-HMAC，自实现三段拼接

**选择：** 自实现 JWT 编解码，仅依赖 sq-common 的 `Sm3Utils`。从 sq-dependencies-core 父 POM 中**移除 jjwt 依赖**。

**理由：**
- jjwt 0.11.5 不原生支持 SM3-HMAC，自定义 `SignatureAlgorithm` 集成成本高。
- 自实现三段拼接（base64url(header).base64url(payload).base64url(SM3-HMAC(key, header.payload))）逻辑直白、无第三方耦合。
- 直接移除 jjwt 避免引入历史 CVE 频发的库。

**备选：** jjwt 0.11.5 + 自定义算法（淘汰）；Hutool JWT（SM 支持有限）。

### D2：SM2 密钥用 PKCS12 keystore，外部路径优先，环境变量注入密码

**选择：** PKCS12 文件；加载策略 `external > classpath`；密码通过 `${SQ_KEYSTORE_PWD}` / `${SQ_KEY_PWD}` 环境变量注入；提供命令行工具 `Sm2KeystoreGenerator` 生成密钥。

**理由：**
- JKS 不支持 SM2，PKCS12 是 BC 对 SM 系列支持最完整的格式。
- 外部路径优先支持生产环境密钥独立部署（chmod 600）。
- 环境变量注入避免明文密码进入仓库或镜像。
- Java keytool 不直接支持 SM2，需自带工具生成。

**备选：** KMS（基础设施未就位）；sys_config 表（泄漏风险高）；yml 明文（违反基线）。

### D3：模型提炼方案 B（最小集），通过 ILoginUser 接口解耦

**选择：** 仅迁移 7 类至 sq-common；新增 `ILoginUser` 接口；sq-fast 的 `LoginUser` 实现该接口；RBAC 实体保留 sq-fast。

**理由：**
- 平台包不应承载具体业务约定（RBAC 表结构）。
- `SecurityUtils.getLoginUser()` 需要 LoginUser；通过 `ILoginUser` 接口（getUserId/getDeptId/getUsername）抽象。
- 迁移面小，回归测试可控。

### D4：TokenService 不下沉，保留 sq-fast/sq-admin-server

**理由：** TokenService 强依赖业务 LoginUser、RedisCache key 约定、application.yml 配置；用户决策 Q3=否；国密改造在原位调用 sq-common Sm3Utils 即可完成。

### D5：BouncyCastle 1.70（jdk15on）

**选择：** `bcprov-jdk15on:1.70` + `bcpkix-jdk15on:1.70`。

**理由：** BC 1.70 对 SM2/SM3/SM4 与 PKCS12 支持最稳定，JDK 1.8 兼容性经多项目验证；1.71+ 改名为 `bcprov-jdk18on` 行为有变；1.69 及更早 SM2 有缺陷。

### D6：前端 sm-crypto 0.3.13，C1C3C2 模式

**选择：** sq-ui 与 sq-ui-vue3 均采用 `sm-crypto@0.3.13`，SM2 加密统一使用 C1C3C2（cipherMode=1）。

**理由：** 0.3.x 社区最稳；C1C3C2 是国标 GM/T 0003.4-2012 规定模式，与后端 BC 互通必须一致。

### D7：禁用旧 BCrypt 兼容，强制重置密码

**选择：** `Sm3PasswordEncoder.matches()` 仅识别 `{sm3}` 前缀；非该前缀直接 false；上线时批量 SQL 重置所有用户密码。

**理由：** 双算法兼容会保留 BCrypt 解析路径形成长期攻击面；用户决策 Q5=不需要兼容；上线流程通过强制改密 + 提前通知控制影响。

### D8：版本治理边界

- sq-dependencies-core 父 POM：管理一切第三方依赖版本 + sq-common 子模块互引版本（`${project.version}` 替换硬编码 `1.0.3`）。覆盖 Spring Boot BOM 的非 JDK 8 安全版本（如 mysql-connector-java 8.0.33 替代 8.0.28）。
- sq-fast 父 POM：仅管理 4 个 sq-dependencies-core 模块引用 + 自身 2 个模块 + 业务专属依赖（webmagic、zxing、p6spy、3 种数据库 JDBC、spring-cloud-commons）。
- sq-fast 父 POM 删除：14 项与平台包重复的 version property 与 dependencyManagement 条目。

### D9：版本号策略

- sq-dependencies-core: `1.0.3` → `1.1.0`
- sq-fast: `1.0.0` → `1.1.0`

### D10：公钥接口 `/system/publicKey`

`GET /system/publicKey`，permitAll，返回 PEM 格式 SM2 公钥。控制器 `SysPublicKeyController` 放 sq-admin-server。

### D11：Sm3PasswordEncoder 编码格式

`{sm3}<base64(salt)>$<hex(SM3(salt || rawPassword))>`。salt 长度 16 字节，SecureRandom 生成。

`{sm3}` 前缀显式标识算法版本，便于未来扩展；与 BCrypt 前缀（`$2a$`）不冲突。

### D12：SM2 密钥配置项

```yaml
sq:
  crypto:
    sm2:
      keystore-path: classpath:keystore/sq-sm2.p12
      keystore-external-path: ${SQ_KEYSTORE_EXTERNAL_PATH:}
      keystore-password: ${SQ_KEYSTORE_PWD:changeit}
      key-alias: sq-sm2-key
      key-password: ${SQ_KEY_PWD:changeit}
      keystore-type: PKCS12
```

加载顺序：`keystore-external-path` 非空 → 加载外部；否则加载 `keystore-path`（classpath）。外部路径加载失败时记录明确错误日志并启动失败，**禁止默默 fallback** 到 classpath（避免运维误以为成功）。

### D13：其余第三方依赖版本升级策略（基于 JDK 1.8 天花板）

**选择：** 在阶段一同步完成以下依赖的版本升级。原则：在 JDK 1.8 兼容的前提下，尽可能升到最新小版本；明确触达 JDK 1.8 天花板的依赖（poi 4.1.2、oshi 6.4.0、spring-cloud-commons 2.0.1、webmagic 0.7.3）保持不动。

**必须升（CVE / 功能需要）：**
- `<fastjson.version>2.0.20` → `2.0.56+`（CVE-2024-23672 等 10+ 漏洞修复）
- `<mysql.version>8.0.28` (SB BOM) → 显式 override 为 `8.0.33`（最后 JDK 8 兼容的 8.0.x 版本，20+ CVE 修复）

**建议升：**
| 依赖 | 当前 | 目标 | 理由 |
| --- | --- | --- | --- |
| mybatis-plus | 3.5.1 | 3.5.8 | JDK 8 天花板：3.5.9 起 Pagination/BlockAttack 拆出到独立 jsqlparser 模块且为 JDK 11 字节码 |
| mybatis-plus-join | 1.4.5 | 1.4.12 | 与 mp 3.5.8 兼容 |
| hutool | 5.8.8 | 5.8.34 | 最新 5.x（6.x 需 JDK 9+） |
| druid | 1.2.15 | 1.2.24 | 连接池泄漏修复 |
| commons-io | 2.11.0 | 2.18.0 | 最后 JDK 8 兼容版本 |
| velocity | 2.3 | 2.4 | 模板解析 bug 修复 |
| zxing | 3.4.1 | 3.5.3 | 编解码 bug 修复 |
| p6spy | 3.9.1 | 3.9.2 | 最新版 |
| redisson | 3.16.2 | 3.36.0+ | 2 年 bug 修复 |
| dynamic-datasource | 3.5.2 | 3.6.1 | 最新版 |
| swagger-annotations | 1.5.22 | 1.6.14 | 最后 JDK 8 兼容版本 |
| pagehelper | 1.4.6 | 1.4.7 | 最新版 |

**触达 JDK 1.8 天花板（不动）：**
- poi 4.1.2：5.x 起需 JDK 9+
- oshi 6.4.0：6.5.0+ 需 JDK 11+
- spring-cloud-commons 2.0.1：3.x 需 JDK 11+
- webmagic 0.7.3：项目 2018 年停更，无替代

## Risks / Trade-offs

- **[BC 1.70 与国产化操作系统/JVM 兼容性]** → 上线前在目标环境（麒麟、统信 UOS、欧拉）验证；准备 BC 1.69 作 fallback；CI 中加 BC 加载冒烟测试。
- **[PKCS12 keystore 在云原生场景挂载失败]** → 同时支持 classpath 与外部路径；外部路径加载失败时记录明确错误日志并启动失败，禁止默默 fallback。
- **[前端 sm-crypto 与后端 BC SM2 编码模式不一致]** → 文档与代码注释明确 C1C3C2；sq-common Sm2Utils 添加单元测试覆盖与前端协议互通用例（前端密文样本）。
- **[强制重置密码影响所有用户]** → 上线前执行批量重置 SQL；普通用户首次登录强制改密；提前一周通知；提供找回密码通道。
- **[前后端发版顺序错误导致登录中断]** → 顺序固定为：先发后端（提供 `/system/publicKey` 接口）→ 再发前端（切换 sm-crypto）；发版窗口安排在低峰期。
- **[keystore 密码泄漏导致 SM2 私钥被破解]** → 仓库禁止提交生产 keystore；密码仅通过环境变量注入；生产 keystore 文件 chmod 600；运维文档明确密钥轮换流程。
- **[BCrypt 与 SM3 PasswordEncoder 兼容期混用导致登录异常]** → 编码格式前缀 `{sm3}` 显式判定，非该前缀直接拒绝；上线前 SQL 批量重置确保 100% `{sm3}` 化。
- **[jjwt 依赖被其他间接依赖引入导致冲突]** → 父 POM 不再 manage jjwt；通过 `mvn dependency:tree` 验证无 transitive 引入；如有则通过 `<exclusion>` 排除。
- **[Hutool 5.8.32 中 SmUtil 与手写 BC 实现行为差异]** → 统一封装在 `Sm2Utils` / `Sm3Utils`，禁止业务代码直接调用 BC 或 Hutool 加密 API；通过 PR review checklist 把关。
- **[三个数据库 SQL 脚本同步维护漏改]** → 同一 review checklist 覆盖三份脚本；阶段五验证三份均执行通过。
- **[移除 OBS SDK 后业务模块缺失依赖编译失败]** → 阶段一同步在 sq-business/pom.xml 中添加 OBS 依赖；通过完整编译验证（`mvn -T 1C clean install -DskipTests`）。
- **[模型迁移导致 import 路径变化但漏改]** → 包路径保持 `com.sq.common.*` 不变（sq-fast 与 sq-common 同包名前缀）；迁移后跑全模块编译，编译错误即定位漏改点。

## Migration Plan

**部署顺序（强约束）：**

1. **T-7 天：** 通知所有用户上线后需重置密码。
2. **T-1 天：** 在 staging 环境完整跑通登录闭环（前端拉公钥 → SM2 加密 → 后端解密 → SM3 校验 → SM3-HMAC JWT 签发 → 后续接口验签）。
3. **T 时刻（停服窗口）：**
   - 步骤 1：执行 SQL 批量重置脚本，所有用户密码改为 `{sm3}` 格式临时密码（admin 改为 `Sq@admin2025`）。
   - 步骤 2：部署 sq-fast 后端新版本（含 `/system/publicKey` 接口与 SM2 keystore）。
   - 步骤 3：部署 sq-ui / sq-ui-vue3 前端新版本（已切换 sm-crypto）。
   - 步骤 4：冒烟测试 admin 登录。
4. **T+1 小时：** 监控登录成功率与异常日志；若登录失败率 > 5% 触发回滚。

**回滚策略：**

每阶段独立 commit + tag：
- 阶段一完成 → tag `v1.1.0-deps-aligned`
- 阶段二完成 → tag `v1.1.0-cve-fixed`
- 阶段三完成 → tag `v1.1.0-model-extracted`
- 阶段四完成 → tag `v1.1.0-gm-ready`
- 阶段五完成 → tag `v1.1.0-release`

**回滚动作：**
- 后端：`git revert` 至上一个 tag，重新打包部署。
- 前端：发布上一版本构建产物。
- 数据库密码：保留批量重置前的 BCrypt 密码备份脚本（`backup_pwd_<timestamp>.sql`），需要时回放。
- keystore：保留 `sq-sm2.p12.bak` 至少 30 天。

## Open Questions

- 生产环境 keystore 是否需要支持 HSM（硬件安全模块）？本期不实现，下期演进。
- 是否需要 SM2 密钥定期轮换机制？本期靠运维流程手工轮换，下期可加自动轮换。
- 前端公钥是否需要带签名证书链以防中间人攻击？本期通过 HTTPS 传输信任，下期考虑公钥指纹校验。
- 移除 jjwt 后，sq-system 是否还有其他模块通过传递依赖间接使用 jjwt？需 `mvn dependency:tree -Dincludes=io.jsonwebtoken` 验证。
