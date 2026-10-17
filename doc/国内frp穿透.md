# 国内 frp 穿透（替代 Cloudflare Tunnel）

熟人点链接访问，相册仍在家里电脑；阿里云 ECS 只做中转。

| 项 | 值 |
|----|-----|
| 云服务器 | 阿里云 ECS `ecs.e-c1m1.large`（2核2G，Ubuntu 22.04） |
| 公网 IP | `101.200.88.62` |
| 外网入口 | **https://album.pengorbit.top/#/login** |
| 本机服务 | `http://127.0.0.1:18080` |
| frp 控制端口 | `7000` |
| 本机映射端口 | 云上 `127.0.0.1:18080`（安全组不放行 18080） |

## 架构

```text
熟人浏览器
  → https://album.pengorbit.top
  → 阿里云 nginx:443
  → 本机 frps 映射的 127.0.0.1:18080
  →（frp 隧道）
  → 家里 frpc → 127.0.0.1:18080 相册
```

## 一、DNS（先做）

在域名解析里把 `album` 指到云主机（**不要**再走 Cloudflare 橙云代理）：

| 主机记录 | 类型 | 记录值 | 代理状态 |
|----------|------|--------|----------|
| `album` | A | `101.200.88.62` | **仅 DNS / 灰云**（关闭代理） |

- 若 NS 仍在 Cloudflare：Dashboard → DNS → `album` 改 A 记录，小云朵变灰。  
- 或把域名 NS 改回阿里云，在阿里云解析里加同上 A 记录。  

改完后本机执行：`ping album.pengorbit.top`，应指向 `101.200.88.62`。

## 二、云服务器（已 SSH 登录后）

安全组入方向需放行：`22`、`80`、`443`、`7000`（不要放行 `18080`）。

把仓库里的 `scripts/frp/setup-server.sh` 上传到服务器后执行，或直接按脚本内容粘贴。  
脚本会安装：frps、nginx、certbot（申请 HTTPS）。

默认 token 见脚本内 `FRP_TOKEN`；部署后请自行改成更长随机串，并与家里 `frpc.toml` 保持一致。

## 三、家里部署机

1. 运行部署包 `bin\install-frpc.bat`（或按文档手动解压到 `D:\frp\`）  
2. 确认 `D:\frp\frpc.toml` 中 `serverAddr`、`auth.token` 正确  
3. 先 `bin\start.bat` 启动相册（18080）  
4. 再 `bin\start-frp.bat`  
5. 访问 https://album.pengorbit.top/#/login  

**停用 Cloudflare：** 关掉 cloudflared 窗口，不要再运行 `start-tunnel.bat`。

## 四、日常开关

| 动作 | 操作 |
|------|------|
| 开外网 | 家里：相册 + `start-frp.bat`；云上 frps/nginx 常开即可 |
| 关外网 | 家里关掉 frpc 窗口（或 Ctrl+C） |
| 云上状态 | `systemctl status frps nginx` |

## 五、相关文件

| 路径 | 说明 |
|------|------|
| `scripts/frp/setup-server.sh` | 云上一次性安装 |
| `scripts/deploy-template/frp/frpc.toml` | 家里客户端配置模板 |
| `scripts/deploy-template/bin/install-frpc.bat` | 家里安装 frpc |
| `scripts/deploy-template/bin/start-frp.bat` | 家里启动穿透 |

旧文档 `doc/Cloudflare固定域名隧道.md` 仅作历史参考，外网请改用本文方案。
