#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Crypto SDK - 密钥生成工具

功能：
1. 生成 RSA-2048 密钥对（用于加解密）
2. 生成 HMAC-SHA256 签名密钥（用于防篡改）

支持环境：
- Python 3.x
- Windows / Linux / Mac / Git Bash

使用方法：
  python generate-keys.py

输出：
  - RSA 公钥（Base64）
  - RSA 私钥（Base64）
  - HMAC 签名密钥（32字节，Base64）
  - 完整配置模板
"""

import base64
import os
import sys
import secrets

def generate_hmac_secret(length=32):
    """生成 HMAC-SHA256 签名密钥"""
    random_bytes = secrets.token_bytes(length)
    return base64.b64encode(random_bytes).decode('utf-8')

def generate_rsa_keypair():
    """生成 RSA 密钥对"""
    try:
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.backends import default_backend
    except ImportError:
        print("错误：缺少 cryptography 库")
        print("请安装：pip install cryptography")
        print("")
        print("或者使用以下替代方案：")
        print("  - Windows: 运行 generate-keys.bat")
        print("  - Linux/Mac: 运行 generate-keys.sh")
        print("  - 在线生成: https://www.allkeysgenerator.com/")
        return None, None
    
    # 生成 RSA 密钥对
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
        backend=default_backend()
    )
    
    # 导出私钥（PKCS8格式）
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    )
    
    # 导出公钥
    public_key = private_key.public_key()
    public_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    
    # 转换为 Base64 单行格式
    private_base64 = base64.b64encode(private_pem).decode('utf-8')
    public_base64 = base64.b64encode(public_pem).decode('utf-8')
    
    return public_base64, private_base64

def print_config_template(public_key, private_key, hmac_secret):
    """打印配置模板"""
    print("")
    print("=" * 60)
    print("生成的密钥配置")
    print("=" * 60)
    print("")
    print("【1】RSA 公钥（用于加密）：")
    print("-" * 60)
    print(public_key[:80] + "..." if public_key else "生成失败")
    print("")
    print("【2】RSA 私钥（用于解密）：")
    print("-" * 60)
    print(private_key[:80] + "..." if private_key else "生成失败")
    print("")
    print("【3】HMAC 签名密钥（用于防篡改）：")
    print("-" * 60)
    print(hmac_secret)
    print("")
    print("=" * 60)
    print("application.yml 配置模板")
    print("=" * 60)
    print("""
crypto:
  enabled: true

  # RSA密钥配置
  # 使用原则：公钥加密，私钥解密
  key:
    # 公钥：用于加密数据
    public-key: |
      {public_key}
    
    # 私钥：用于解密数据
    private-key: |
      {private_key}
    
    auto-generate: false

  # 签名验证配置（防重放、防篡改）
  signature:
    enabled: true
    secret: {hmac_secret}
    timestamp-validity: 300000
""".format(
        public_key=public_key if public_key else "<RSA公钥>",
        private_key=private_key if private_key else "<RSA私钥>",
        hmac_secret=hmac_secret
    ))
    print("=" * 60)
    print("使用说明")
    print("=" * 60)
    print("""
1. 将上述配置复制到 application.yml
2. 公钥和私钥可以是一对（双方共用），也可以是各自的
3. HMAC 签名密钥需要双方共享
4. 生产环境建议使用环境变量存储密钥

安全建议：
- 私钥严格保密，不要泄露
- HMAC 密钥通过安全渠道交换
- 始终使用 HTTPS 传输
""")

def main():
    print("=" * 60)
    print("Crypto SDK - 密钥生成工具")
    print("=" * 60)
    print("")
    print("正在生成密钥...")
    print("")
    
    # 生成 HMAC 签名密钥
    hmac_secret = generate_hmac_secret(32)
    
    # 生成 RSA 密钥对
    public_key, private_key = generate_rsa_keypair()
    
    # 打印配置模板
    print_config_template(public_key, private_key, hmac_secret)

if __name__ == "__main__":
    main()
