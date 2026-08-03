## ADDED Requirements

### Requirement: 通用工具与基类下沉至 sq-common

平台 SHALL 将以下 7 个类从 sq-fast 迁移至 sq-common，作为可复用的平台底座：`TreeEntity`、`TreeSelect`、`SecurityUtils`、`DictUtils`、`ExcelUtil`、`GeneratorUtil`、`BaseController`。迁移后包路径 SHALL 保持 `com.sq.common.*` 不变。MUST NOT 在迁移过程中改变类的公开 API 签名。

#### Scenario: 7 个类位于 sq-common
- **WHEN** 检查 sq-common 模块的源码目录
- **THEN** SHALL 存在 `com.sq.common.core.domain.TreeEntity`
- **AND** SHALL 存在 `com.sq.common.core.domain.TreeSelect`
- **AND** SHALL 存在 `com.sq.common.utils.SecurityUtils`
- **AND** SHALL 存在 `com.sq.common.utils.DictUtils`
- **AND** SHALL 存在 `com.sq.common.utils.poi.ExcelUtil`
- **AND** SHALL 存在 `com.sq.common.utils.GeneratorUtil`
- **AND** SHALL 存在 `com.sq.common.core.controller.BaseController`

#### Scenario: sq-fast 不再持有这 7 个类
- **WHEN** 检查 sq-fast 各模块源码
- **THEN** sq-fast 中 MUST NOT 存在上述 7 个类的副本
- **AND** sq-fast 中对这些类的 import 语句 SHALL 全部指向 `com.sq.common.*`

### Requirement: ILoginUser 接口解耦 SecurityUtils

sq-common SHALL 新增 `com.sq.common.core.domain.ILoginUser` 接口，至少包含 `getUserId()`、`getDeptId()`、`getUsername()` 方法。`SecurityUtils.getLoginUser()` SHALL 返回该接口类型，避免反向依赖 sq-fast 的 `LoginUser` 具体类。

#### Scenario: SecurityUtils 不依赖 sq-fast
- **WHEN** 检查 sq-common 模块的 `mvn dependency:tree`
- **THEN** sq-common MUST NOT 依赖 sq-fast 的任何模块
- **AND** `SecurityUtils` 源码 MUST NOT 出现 `import com.sq.framework.web.domain.LoginUser`

#### Scenario: sq-fast LoginUser 实现 ILoginUser
- **WHEN** 检查 sq-fast 中 `LoginUser` 类定义
- **THEN** SHALL `implements ILoginUser`
- **AND** SHALL 实现 `getUserId()`、`getDeptId()`、`getUsername()` 三个方法

### Requirement: RBAC 实体保留在 sq-fast

`SysUser`、`SysRole`、`SysDept`、`SysMenu` 等 RBAC 实体 SHALL 保留在 sq-fast 模块。sq-common MUST NOT 包含任何 RBAC 业务实体。

#### Scenario: sq-common 无 RBAC 实体
- **WHEN** 检查 sq-common 源码
- **THEN** MUST NOT 存在 `SysUser` / `SysRole` / `SysDept` / `SysMenu` 等任何 RBAC 实体类

### Requirement: Springfox 移除

sq-dependencies-core 与 sq-fast SHALL 移除 springfox 相关依赖与配置。本期 MUST NOT 引入 springdoc-openapi 替代实现。

#### Scenario: 父 POM 不再管理 springfox
- **WHEN** 检查 sq-dependencies-core 与 sq-fast 父 POM
- **THEN** 两份 POM 均 MUST NOT 包含任何 `springfox` 依赖声明
- **AND** `mvn dependency:tree -Dincludes=io.springfox` 输出 SHALL 为空

#### Scenario: SwaggerConfig 已清理
- **WHEN** 检查 sq-fast 各模块源码
- **THEN** MUST NOT 存在引用 `springfox.documentation.*` 的配置类
