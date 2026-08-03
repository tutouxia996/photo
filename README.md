
# sq-fast 基线版本

前后端分离研发框架基线版本，面向新项目快速落地。

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot + Spring Security + MyBatis Plus | Boot 2.5.14 / MP 3.3.2 |
| 数据库支持 | MySQL 8、达梦 DM8、GaussDB | 三库兼容 |
| 前端 Vue2 | Vue 2 + Element UI + Webpack | Node >= 14 |
| 前端 Vue3 | Vue 3 + Element Plus + Vite + TypeScript | Node >= 14 |
| 构建工具 | Maven | >= 3.5.4 |
| JDK | JDK | >= 1.8 |

## 项目结构

```
├── doc/                   项目文档
│   ├── db/               数据库初始化脚本
│   │   ├── mysql_init.sql
│   │   ├── dm_init.sql
│   │   ├── gaussdb_init.sql
│   ├── assembly/         部署相关
│   ├── 定制基线步骤.md    基线定制指南
│   ├── 生产部署打包.md     生产构建与部署
│   ├── 前后端合并部署流程.md
├── sq-dependencies-core/  平台基础组件包（公共依赖）— 须先 mvn install
│   ├── sq-common/        公共工具、异常、实体、统一响应对象 R
│   ├── sq-framework/     框架核心（安全、异常处理、全局异常拦截、统一响应封装 R）
├── sq-system/            系统管理后台
├── sq-business/          业务后台
├── sq-admin-server/      Web 管理服务（启动入口）
├── sq-ui/                前端 Vue2 工程
├── sq-ui-vue3/           前端 Vue3 工程
```

## 快速启动

### 1. 初始化数据库

```sql
source doc/db/mysql_init.sql
```

### 2. 配置数据库连接

`sq-admin-server/src/main/resources/application-{profile}.yml` 中修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db?useUnicode=true&characterEncoding=utf8
    username: your_user
    password: your_password
```

### 3. 启动后端

```bash
mvn clean install -DskipTests
cd sq-admin-server
mvn spring-boot:run -Dspring-boot.run.profiles=mysql-dev
```

### 4. 启动前端

```bash
# Vue2
cd sq-ui
npm install --registry=https://registry.npmmirror.com
npm run dev

# Vue3
cd sq-ui-vue3
npm install --registry=https://registry.npmmirror.com
npm run dev
```

浏览器访问 `http://localhost`，默认账号密码 `admin / admin123`。

---

## sq-dependencies-core 核心组件包

`sq-dependencies-core` 是平台公共依赖包，所有其他模块（`sq-system`、`sq-business`、`sq-admin-server`）均通过 Maven 依赖引用它。

### 模块结构

| 子模块 | 路径 | 职责 |
|---|---|---|
| sq-common | `sq-common/` | 公共工具类、异常体系、统一响应 `R` / `AjaxResult`、常量、Redis 工具、i18n 消息 |
| sq-framework | `sq-framework/` | 安全框架（Spring Security 封装）、全局异常处理器、统一响应封装、Token 管理 |

### 统一异常处理体系

所有业务异常继承 `BaseException`（`RuntimeException` 子类），层次结构：

```
RuntimeException
 └── BaseException
      ├── ServiceException           — 通用业务异常（含 code + message）
      ├── UserException              — 用户业务异常基类
      │    ├── UserPasswordNotMatchException       — 用户不存在/密码错误
      │    ├── UserPasswordRetryLimitExceedException — 密码重试超限
      │    ├── CaptchaException                     — 验证码错误
      │    └── CaptchaExpireException               — 验证码过期
      ├── DemoModeException          — 演示模式限制
      ├── FileException              — 文件上传异常
      ├── GlobalException            — 全局异常
      └── UtilException              — 工具类异常
```

### 全局异常处理器

`sq-framework/.../GlobalExceptionHandler.java` 拦截所有 Controller 未捕获异常，按类型分级处理：

| 异常类型 | 前端 msg | HTTP Code | 日志级别 |
|---|---|---|---|
| `AccessDeniedException` | "没有权限，请联系管理员授权" | 403 | error |
| `ServiceException` | `e.getMessage()`（含 code）| 500 或自定义 | error |
| `UserException`（密码/锁定） | "用户不存在或密码不正确"（模糊）| 500 | error |
| `UserException`（验证码） | "验证码错误" / "验证码已失效"（原文）| 500 | error |
| `BindException` / 参数校验 | 校验失败详情 | 500 | error |
| `RuntimeException` / `Exception` | "系统异常，请联系管理员！" | 500 | error |

前端展示安全策略：密码类错误统一模糊提示，验证码类保留原文。避免攻击者通过错误消息差异枚举有效用户名。后端日志始终保持精确（module、code、args、message、异常栈）。

---

## 定制基线步骤

基于基线版本研发新系统的完整定制指南，包括系统名称、主题颜色、皮肤、密码策略等配置项说明。

详见 [doc/定制基线步骤.md](doc/定制基线步骤.md)

---

## 生产打包与部署

生产环境构建、打包、部署全流程，包括证书制作、环境变量配置、前后端合并部署等。

详见 [doc/生产打包与部署.md](doc/生产打包与部署.md)

---

## 注意事项

1. **Node 版本**：Vue2 项目在 Node 22+ 下构建需要设置 `NODE_OPTIONS="--openssl-legacy-provider"`，建议使用 Node 14~18。
2. **Maven 版本**：`flatten-maven-plugin` 要求 Maven >= 3.5.4，建议使用 Maven 3.6+。
3. **JWT 密钥**：生产环境必须通过环境变量 `SQ_JWT_SECRET` 注入，不要使用默认值。
4. **数据库初始化**：初始化脚本包含基础数据（菜单、角色、配置参数），首次运行必须执行。
