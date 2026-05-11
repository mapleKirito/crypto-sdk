# Vue前端调用方示例

## 快速开始

### 1. 安装依赖

```bash
npm install axios jsencrypt@3.0.0-beta.1 crypto-js
npm install -D @types/jsencrypt @types/crypto-js
```

### 2. 配置加密客户端

```typescript
// src/plugins/cryptoAxiosConfig.ts
import axios from 'axios'
import { CryptoAxiosPlugin } from './plugins/cryptoAxios'

const cryptoAxios = axios.create({
  baseURL: '/api'
})

CryptoAxiosPlugin.install(cryptoAxios, {
  // 公钥：用于加密请求（服务端给的）
  serverPublicKey: '-----BEGIN PUBLIC KEY-----...-----END PUBLIC KEY-----',
  
  // 私钥：用于解密响应（自己的）
  clientPrivateKey: '-----BEGIN RSA PRIVATE KEY-----...-----END RSA PRIVATE KEY-----',
  
  // 签名密钥（与服务端共享）
  signatureSecret: 'your-32-char-signature-secret-key',
  
  signatureEnabled: true
})

export default cryptoAxios
```

### 3. 调用加密接口

```typescript
import cryptoAxios from '@/plugins/cryptoAxiosConfig'

// 方式1：直接使用
const response = await cryptoAxios.post('/api/order/create', {
  orderId: '123',
  amount: 100.00
})

// 方式2：使用封装的API服务
import { cryptoApi } from '@/utils/cryptoApi'

const result = await cryptoApi.post('/api/order/create', {
  orderId: '123',
  amount: 100.00
})
```

## 功能说明

| 功能 | 说明 |
|------|------|
| 自动加密请求 | 使用服务端公钥加密 |
| 自动解密响应 | 使用客户端私钥解密 |
| 自动添加安全头 | X-Timestamp、X-Nonce、X-Signature |
| 防重放攻击 | 每次请求生成唯一 Nonce |
| 防篡改 | HMAC-SHA256 签名验证 |

## 密钥配置

### 共用一套密钥

前端和服务端使用同一对密钥：
- `serverPublicKey`：同一对密钥的公钥
- `clientPrivateKey`：同一对密钥的私钥

### 各自密钥

- `serverPublicKey`：服务端的公钥（用于加密请求）
- `clientPrivateKey`：前端自己的私钥（用于解密响应）

## Vue组件示例

```vue
<template>
  <form @submit.prevent="submitOrder">
    <input v-model="order.productId" placeholder="商品ID" />
    <input v-model="order.quantity" type="number" placeholder="数量" />
    <button type="submit">提交订单</button>
  </form>
</template>

<script>
import { cryptoApi } from '@/utils/cryptoApi'

export default {
  data() {
    return {
      order: {
        productId: '',
        quantity: 1
      }
    }
  },
  methods: {
    async submitOrder() {
      try {
        const result = await cryptoApi.post('/api/order/create', this.order)
        this.$message.success('订单创建成功！')
        console.log('订单ID:', result.orderId)
      } catch (error) {
        this.$message.error('订单创建失败：' + error.message)
      }
    }
  }
}
</script>
```
