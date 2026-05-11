#!/bin/bash
# Crypto SDK - 密钥生成工具 (Linux/Mac)
# 生成 RSA 密钥对和 HMAC 签名密钥

echo "========================================"
echo "Crypto SDK - 密钥生成工具 (Linux/Mac)"
echo "========================================"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查 Python 是否可用
if command -v python3 &> /dev/null; then
    echo "使用 Python3 生成密钥..."
    echo ""
    python3 "$SCRIPT_DIR/generate-keys.py"
    exit 0
elif command -v python &> /dev/null; then
    echo "使用 Python 生成密钥..."
    echo ""
    python "$SCRIPT_DIR/generate-keys.py"
    exit 0
fi

# 使用 OpenSSL 生成
echo "Python 未找到，使用 OpenSSL 生成..."
echo ""

echo "========================================"
echo "正在生成密钥..."
echo "========================================"
echo ""

# 生成 HMAC 签名密钥（32字节）
HMAC_SECRET=$(openssl rand -base64 32)

echo "========================================"
echo "生成的密钥"
echo "========================================"
echo ""

echo "【1】HMAC 签名密钥（用于防篡改）："
echo "----------------------------------------"
echo "$HMAC_SECRET"
echo ""

# 生成 RSA 密钥对
echo "【2】RSA 密钥对："
echo "----------------------------------------"

if command -v openssl &> /dev/null; then
    # 生成私钥
    openssl genrsa -out /tmp/temp_private.pem 2048 2>/dev/null
    
    # 导出公钥
    openssl rsa -in /tmp/temp_private.pem -pubout -out /tmp/temp_public.pem 2>/dev/null
    
    # 转换为 Base64
    PRIVATE_KEY=$(base64 -w 0 /tmp/temp_private.pem)
    PUBLIC_KEY=$(base64 -w 0 /tmp/temp_public.pem)
    
    # 清理临时文件
    rm -f /tmp/temp_private.pem /tmp/temp_public.pem
    
    echo "RSA 公钥（前80字符）："
    echo "${PUBLIC_KEY:0:80}..."
    echo ""
    echo "RSA 私钥（前80字符）："
    echo "${PRIVATE_KEY:0:80}..."
    echo ""
else
    echo "OpenSSL 未找到，使用在线工具生成 RSA 密钥对："
    echo "https://www.allkeysgenerator.com/"
    echo ""
    PUBLIC_KEY="<RSA公钥>"
    PRIVATE_KEY="<RSA私钥>"
fi

echo "========================================"
echo "application.yml 配置模板"
echo "========================================"
echo ""
echo "crypto:"
echo "  enabled: true"
echo ""
echo "  key:"
echo "    public-key: |"
echo "      $PUBLIC_KEY"
echo "    private-key: |"
echo "      $PRIVATE_KEY"
echo "    auto-generate: false"
echo ""
echo "  signature:"
echo "    enabled: true"
echo "    secret: $HMAC_SECRET"
echo "    timestamp-validity: 300000"
echo ""

echo "========================================"
echo "使用说明"
echo "========================================"
echo ""
echo "1. 将上述配置复制到 application.yml"
echo "2. 公钥加密，私钥解密"
echo "3. HMAC 密钥双方共享"
echo ""
echo "推荐安装 Python 以获得完整功能："
echo "  pip install cryptography"
echo "  python generate-keys.py"
echo ""
