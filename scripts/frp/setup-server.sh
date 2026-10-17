#!/usr/bin/env bash
# 阿里云 Ubuntu 上安装 frps + nginx + HTTPS（album.pengorbit.top）
# 用法：sudo bash setup-server.sh
set -euo pipefail

DOMAIN="${DOMAIN:-album.pengorbit.top}"
FRP_TOKEN="${FRP_TOKEN:-a5113a9b4d704d12900e3a20}"
FRP_VER="${FRP_VER:-0.61.1}"
EMAIL="${EMAIL:-admin@pengorbit.top}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "请用 root 执行: sudo bash setup-server.sh"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nginx curl ca-certificates

# --- frps ---
ARCH=amd64
TMP="/tmp/frp_${FRP_VER}"
mkdir -p "$TMP"
cd "$TMP"
URL="https://github.com/fatedier/frp/releases/download/v${FRP_VER}/frp_${FRP_VER}_linux_${ARCH}.tar.gz"
echo "下载 frp: $URL"
if ! curl -fL --connect-timeout 30 -o frp.tgz "$URL"; then
  MIRROR="https://ghproxy.net/$URL"
  echo "直连失败，尝试镜像: $MIRROR"
  curl -fL --connect-timeout 60 -o frp.tgz "$MIRROR"
fi
tar -xzf frp.tgz
install -m 755 "frp_${FRP_VER}_linux_${ARCH}/frps" /usr/local/bin/frps
mkdir -p /etc/frp

cat >/etc/frp/frps.toml <<EOF
bindPort = 7000
auth.method = "token"
auth.token = "${FRP_TOKEN}"

# 仅允许映射浏览端口，由本机 nginx 反代
allowPorts = [
  { start = 18080, end = 18080 }
]
EOF

cat >/etc/systemd/system/frps.service <<'EOF'
[Unit]
Description=frp server
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/frps -c /etc/frp/frps.toml
Restart=on-failure
RestartSec=5
LimitNOFILE=1048576

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now frps

# --- nginx 反代到 frp 映射的本地 18080（先 HTTP，证书申请后再上 HTTPS）---
cat >/etc/nginx/sites-available/album <<EOF
server {
    listen 80;
    server_name ${DOMAIN};

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }
}
EOF

ln -sfn /etc/nginx/sites-available/album /etc/nginx/sites-enabled/album
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo
echo "======== frps / nginx 已启动 ========"
echo "token: ${FRP_TOKEN}"
echo "请确认 DNS: ${DOMAIN} -> 本机公网 IP（灰云/仅 DNS）"
echo
echo "DNS 生效后申请 HTTPS："
echo "  apt-get install -y certbot python3-certbot-nginx"
echo "  certbot --nginx -d ${DOMAIN} --agree-tos -m ${EMAIL} --redirect -n"
echo
systemctl --no-pager --full status frps || true
