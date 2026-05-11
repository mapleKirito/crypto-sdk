# Postman / Apifox 加密调试工具

为 API 调试工具（Postman、Apifox）提供的请求前置/后置加解密脚本。

## 文件说明

| 文件 | 用途 | 适用环境 |
|------|------|----------|
| `pre-request.js` | 请求加密脚本 | Postman（Web Crypto API + JSEncrypt） |
| `pre-request-apifox.js` | 请求加密脚本 | Apifox（jsrsasign + node-forge） |
| `post-response.js` | 响应解密脚本 | Postman（Web Crypto API + JSEncrypt） |
| `post-response-apifox.js` | 响应解密脚本 | Apifox（jsrsasign + node-forge） |

## 快速开始

### 1. 配置环境变量

在 Postman / Apifox 中配置以下环境变量：

```
SERVER_PUBLIC_KEY    = 服务端公钥（用于加密请求）
CLIENT_PRIVATE_KEY   = 客户端私钥（用于解密响应）
SIGNATURE_SECRET     = HMAC签名密钥（双方共享）
ENABLE_CRYPTO        = true（可选，默认启用）
```

### 2. Postman 配置

#### 请求加密（Pre-request Script）

1. 打开 Postman，选择需要加密的请求
2. 切换到 **Pre-request Script** 标签
3. 粘贴 `pre-request.js` 中的代码
4. 确保已配置环境变量

#### 响应解密（Tests）

1. 切换到 **Tests** 标签
2. 粘贴 `post-response.js` 中的代码
3. 发送请求，响应将自动解密并在控制台显示

### 3. Apifox 配置

#### 请求加密（前置操作）

1. 打开 Apifox，选择需要加密的接口
2. 点击 **前置操作** → **添加前置操作** → **自定义脚本**
3. 粘贴 `pre-request-apifox.js` 中的代码
4. 确保已配置环境变量且网络正常（首次执行会从 CDN 加载 node-forge）

#### 响应解密（后置操作）

1. 点击 **后置操作** → **添加后置操作** → **自定义脚本**
2. 粘贴 `post-response-apifox.js` 中的代码
3. 发送请求，响应将自动解密

## 重要说明

### Postman vs Apifox 脚本区别

| 特性 | Postman | Apifox |
|------|---------|--------|
| 请求加密脚本 | `pre-request.js` | `pre-request-apifox.js` |
| 响应解密脚本 | `post-response.js` | `post-response-apifox.js` |
| RSA 库 | JSEncrypt（动态加载） | jsrsasign（内置） |
| AES-GCM 库 | Web Crypto API | node-forge（CDN 动态加载） |
| HMAC 库 | Web Crypto API | node-forge（CDN 动态加载） |
| 联网要求 | 首次需加载 JSEncrypt | 首次需加载 node-forge |

### Apifox 依赖说明

**为什么不用 crypto-js？**

Apifox 内置的 crypto-js（v3.1.9-1）和 npm 上的 crypto-js（v4.x）**都不支持 AES-GCM 模式**。crypto-js 仅支持 CBC、CFB、CTR、OFB、ECB 模式。而服务端使用的是 Java `AES/GCM/NoPadding`，因此必须使用支持 GCM 的库。

Apifox 脚本采用以下策略：

- **RSA 加解密**：使用 Apifox 内置的 `jsrsasign` 库（`require('jsrsasign')`），无需联网
- **AES-GCM 加解密**：通过 `pm.sendRequest` 加载 `node-forge`（~300KB），需要联网
- **HMAC-SHA256**：使用 `node-forge` 的 HMAC 模块

### 公钥/私钥格式

支持两种格式：

1. **PEM 格式（推荐）：**
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
-----END PUBLIC KEY-----
```

2. **Base64 格式（纯密钥内容）：**
```
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
```

脚本会自动检测并补全 PEM 头尾。

## 加密流程

```
请求流程：
1. 获取原始请求体
2. 生成 AES 密钥（256位）
3. 生成 IV（96位）
4. AES-GCM 加密数据（输出 = 密文 + 16字节认证标签）
5. 组合 IV + 密文 + TAG
6. RSA 加密 AES 密钥
7. 生成安全请求头（Timestamp、Nonce、Signature）
8. 发送加密后的请求

响应流程：
1. 接收加密响应
2. RSA 解密获取 AES 密钥
3. 分离 IV、密文、TAG
4. AES-GCM 解密数据（自动验证认证标签）
5. 显示解密后的响应
```

## 调试技巧

1. **查看加密后的请求**：在 Postman Console / Apifox 控制台中查看
2. **查看解密后的响应**：在环境变量 `DECRYPTED_RESPONSE` 中查看
3. **禁用加密**：设置环境变量 `ENABLE_CRYPTO = false`

## 常见问题

### 1. Apifox 提示 "加载 node-forge 失败"

确保网络可以访问 `cdn.jsdelivr.net`。node-forge 文件约 300KB，首次加载可能需要几秒。

### 2. RSA encryption failed

检查公钥格式是否正确：
- 确保是有效的 RSA-2048 公钥
- PEM 格式需要包含完整的头部和尾部
- Base64 格式不要有换行符

### 3. 签名验证失败

确保 `SIGNATURE_SECRET` 环境变量配置正确，且与服务端一致。

### 4. 解密后数据为空

检查 `CLIENT_PRIVATE_KEY` 是否与服务端加密使用的公钥对应。

### 5. 为什么 crypto-js 不支持 GCM？

crypto-js（包括 v3.x 和 v4.x）的 block modes 仅包含 CBC、CFB、CTR、OFB、ECB，没有 GCM。GCM（Galois/Counter Mode）需要额外的 GHASH 认证标签计算，crypto-js 作者未实现该模式。如需 GCM，需使用 node-forge 或浏览器原生 Web Crypto API。

## 示例

### 原始请求

```json
{
  "orderId": "123456",
  "amount": 100.00
}
```

### 加密后的请求

```json
{
  "data": "x9k2m7p4...",
  "key": "w3t8y5n1..."
}
```

### 请求头

```
X-Timestamp: 1715241600000
X-Nonce: a1b2c3d4e5f6...
X-Signature: s8v3n2k1...
X-Encrypted: true
Content-Type: application/json
```
