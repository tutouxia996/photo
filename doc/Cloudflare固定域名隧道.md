# Cloudflare 固定域名隧道（pengorbit.top）

本文记录本机相册通过 **Cloudflare Named Tunnel** 使用自有域名的配置（已验证可用）。

## 结果

| 项 | 值 |
|----|-----|
| 注册商 | 阿里云（支付宝购买） |
| 域名 | `pengorbit.top` |
| DNS | 已改为 Cloudflare Nameserver（站点 Active） |
| 外网入口 | **https://album.pengorbit.top/#/login** |
| 本机服务 | `http://127.0.0.1:18080`（前后端合并单端口包） |
| 隧道名称 | `album-home` |
| 隧道 ID | `f99d35ee-71e1-4f3c-9b01-4b5851de0cc7` |

## 为何能免费用域名

- **临时隧道**（`tunnel --url`）：免费，但地址是 `*.trycloudflare.com`，每次会变。  
- **Named Tunnel + 自有域名**：用 CLI 登录授权后创建，**不必**走 Zero Trust 控制台的信用卡开通页。  
- 域名在阿里云购买（人民币）；Cloudflare 仅做 DNS + 隧道，个人用量通常 $0。

> 控制台 Zero Trust「Activate Free」若要求绑卡，可忽略；本方案以 CLI 为准。

## 本机文件位置

```text
%USERPROFILE%\.cloudflared\
├── cert.pem                          # tunnel login 后生成
├── f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json   # 隧道凭证（保密）
└── config.yml                        # 入口配置

D:\cloudflared\cloudflared.exe        # 客户端
```

### config.yml 内容

```yaml
tunnel: f99d35ee-71e1-4f3c-9b01-4b5851de0cc7
credentials-file: C:\Users\guoxinpeng\.cloudflared\f99d35ee-71e1-4f3c-9b01-4b5851de0cc7.json

ingress:
  - hostname: album.pengorbit.top
    service: http://127.0.0.1:18080
  - service: http_status:404
```

## 首次搭建回顾（新机器可照做）

```bat
:: 1. 登录授权（浏览器选择 pengorbit.top）
D:\cloudflared\cloudflared.exe tunnel login

:: 2. 创建隧道
D:\cloudflared\cloudflared.exe tunnel create album-home

:: 3. 编写 config.yml（tunnel id / credentials-file 以实际为准）

:: 4. 把子域名指到隧道
D:\cloudflared\cloudflared.exe tunnel route dns album-home album.pengorbit.top

:: 5. 运行
D:\cloudflared\cloudflared.exe tunnel run album-home
```

阿里云侧 DNS 服务器须为 Cloudflare 提供的 NS（例如 `dean.ns.cloudflare.com` / `olga.ns.cloudflare.com`），且站点在 Cloudflare 为 **Active**。若开启了 DNSSEC，注册商处先关闭。

## 日常使用

```text
1. 启动 MySQL、Redis
2. 启动相册（桌面「相册网站-部署包\bin\start.bat」或 IDE）
3. 确认 http://127.0.0.1:18080/#/login
4. 启动隧道：cloudflared.exe tunnel run album-home
5. 访问 https://album.pengorbit.top/#/login
```

停止外网：关掉 cloudflared 窗口，或 `Ctrl+C`。

## 备用：临时隧道

```bat
D:\cloudflared\cloudflared.exe tunnel --url http://127.0.0.1:18080
```

## 安全建议

- 修改默认管理员密码  
- 链接只发给信任的人  
- 不要泄露 `.cloudflared` 下的 `cert.pem` 与隧道 `.json`  
- 用完可关隧道，减少暴露时间  

## 相关文档

- `doc/相册网站家用合并部署说明.md`  
- 桌面 `相册网站-部署包\部署说明.md`  
- `doc/生产打包与部署.md`  
