## ADDED Requirements

### Requirement: 平台依赖包统一版本治理

sq-dependencies-core 父 POM SHALL 是所有第三方依赖版本与 sq-common 子模块互引版本的唯一权威来源。所有子模块 MUST NOT 在自身 POM 中硬编码任何第三方依赖版本号。

#### Scenario: 子模块引用第三方依赖时使用父 POM 管理的版本
- **WHEN** sq-common / sq-framework / sq-quartz / sq-generator 任一子模块 POM 声明 mybatis-plus / dynamic-datasource / mybatis-plus-join / hutool / swagger-annotations / esdk-obs-java-bundle 等依赖
- **THEN** 子模块 POM 中 MUST NOT 出现 `<version>` 标签
- **AND** 该依赖的版本 MUST 在 sq-dependencies-core 父 POM 的 `<dependencyManagement>` 与 `<properties>` 中统一管理

#### Scenario: 子模块互引版本随父 POM 联动
- **WHEN** sq-framework / sq-quartz / sq-generator 引用 sq-common 模块
- **THEN** 引用版本 MUST 写为 `${project.version}`
- **AND** MUST NOT 出现硬编码的 `1.0.3` 或其他字面量版本

### Requirement: sq-fast 父 POM 不重复管理已被平台包覆盖的依赖

sq-fast 父 POM SHALL 仅管理：4 个 sq-dependencies-core 模块版本引用、自身 sq-system / sq-business 模块版本引用、业务专属依赖（webmagic / 三种数据库 JDBC / spring-cloud-commons）。其余依赖版本管理 SHALL 全部下沉到 sq-dependencies-core。zxing、p6spy、mysql-connector-java SHALL 由 sq-dependencies-core 父 POM 统一管理。

#### Scenario: 重复依赖被清理
- **WHEN** 在 sq-fast 父 POM 中查找 druid / UserAgentUtils / pagehelper / oshi / springfox / commons-io / commons-fileupload / poi / velocity / commons-collections / fastjson2 / jjwt / kaptcha / knife4j 任一依赖
- **THEN** sq-fast 父 POM 中 MUST NOT 存在该依赖的 `<dependencyManagement>` 条目
- **AND** sq-fast 父 POM 的 `<properties>` 中 MUST NOT 存在该依赖对应的 version property

#### Scenario: 误用 ${version} 被修复
- **WHEN** 检查 sq-fast 父 POM 文件
- **THEN** 文件中 MUST NOT 包含 `${version}` 字面量（已知错误位于第 201 行 commons-fileupload 与第 208 行 oshi 引用）
- **AND** 任何对 sq-dependencies-core 模块的引用 MUST 使用 `${sq.version}` 或显式版本

### Requirement: 版本升级必须遵循 JDK 1.8 天花板

所有第三方依赖版本升级 MUST 在 JDK 1.8 兼容范围内进行。触及 JDK 1.8 天花板的依赖（poi 4.1.2、oshi 6.4.0、spring-cloud-commons 2.0.1.RELEASE、webmagic 0.7.3）MUST 保持原版本不动。

#### Scenario: 验证 poi 未升到 5.x
- **WHEN** 检查 sq-dependencies-core 父 POM `mvn help:effective-pom`
- **THEN** poi-ooxml 版本 MUST 为 4.1.2（5.x 起需 JDK 9+）

#### Scenario: 验证 oshi 未升到 6.5+
- **WHEN** 检查 sq-dependencies-core 父 POM
- **THEN** oshi-core 版本 MUST 为 6.4.0（6.5.0+ 需 JDK 11+）

### Requirement: 平台版本号策略

sq-dependencies-core SHALL 升级到 `1.1.0`。sq-fast SHALL 升级到 `1.1.0`。

#### Scenario: 平台依赖包发版
- **WHEN** 检查 sq-dependencies-core 根 POM 与 4 个子模块 POM 的 `<version>`
- **THEN** 全部 MUST 为 `1.1.0`

#### Scenario: sq-fast 发版
- **WHEN** 检查 sq-fast 根 POM 与 sq-system / sq-business 子模块 POM 的 `<version>`
- **THEN** 全部 MUST 为 `1.1.0`
- **AND** sq-fast 父 POM 中 sq-dependencies-core 的引用版本 MUST 为 `1.1.0`

### Requirement: OBS SDK 移出平台核心包

esdk-obs-java-bundle 属于业务能力依赖，SHALL NOT 在 sq-dependencies-core 中声明或管理。需要 OBS 能力的业务模块 SHALL 在自身 POM 中声明该依赖。

#### Scenario: sq-common 不再传递 OBS 依赖
- **WHEN** 检查 sq-common 的 POM 与 `mvn dependency:tree`
- **THEN** sq-common MUST NOT 出现 esdk-obs-java-bundle 依赖

#### Scenario: 业务模块直接声明 OBS
- **WHEN** sq-business 需要使用 OBS 能力
- **THEN** sq-business POM SHALL 直接声明 esdk-obs-java-bundle 依赖
