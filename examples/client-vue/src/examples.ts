/**
 * 使用示例
 *
 * 演示如何在Vue项目中使用加密客户端
 */

import { createCryptoAxios } from './plugins/cryptoAxiosConfig'
import { cryptoApi } from './utils/cryptoApi'
import { ClientCrypto, CryptoMode, initCryptoClient, generateSecurityHeaders } from './utils/crypto'

// ==================== 示例1：基础使用（推荐） ====================

/**
 * 使用封装好的API服务
 */
async function exampleWithCryptoApi() {
  // 创建订单 - 自动加密请求，自动解密响应
  const orderData = {
    orderId: 'ORD' + Date.now(),
    userId: 'user123',
    items: [
      { productId: 'P001', quantity: 2, price: 99.99 }
    ],
    totalAmount: 199.98
  }

  try {
    // 调用加密接口
    const result = await cryptoApi.post('/api/order/create', orderData)
    console.log('订单创建成功:', result)

    // 查询订单
    const order = await cryptoApi.get('/api/order/get', { orderId: 'ORD123' })
    console.log('订单信息:', order)

  } catch (error) {
    console.error('请求失败:', error)
  }
}

// ==================== 示例2：直接使用Axios ====================

/**
 * 直接使用配置好的Axios实例
 */
async function exampleWithAxios() {
  const axios = createCryptoAxios('http://localhost:8080')

  const requestData = {
    username: 'admin',
    password: '123456'
  }

  try {
    // 发送加密请求
    const response = await axios.post('/api/auth/login', requestData)

    // 获取解密后的数据
    const data = response._decryptedData || response.data
    console.log('登录成功:', data)

  } catch (error) {
    console.error('请求失败:', error)
  }
}

// ==================== 示例3：手动加密 ====================

/**
 * 手动使用加密工具
 */
async function exampleManualCrypto() {
  // 初始化加密客户端
  const crypto = initCryptoClient({
    serverPublicKey: '-----BEGIN PUBLIC KEY-----...服务端公钥...-----END PUBLIC KEY-----',
    clientPrivateKey: '-----BEGIN RSA PRIVATE KEY-----...客户端私钥...-----END RSA PRIVATE KEY-----',
    signatureSecret: 'your-32-char-signature-secret-key',
    signatureEnabled: true
  })

  // 1. 手动加密数据
  const plaintext = JSON.stringify({ orderId: '12345', amount: 100 })
  const encrypted = crypto.encrypt(plaintext, CryptoMode.RSA_AES_HYBRID)

  console.log('加密结果:', {
    encryptedData: encrypted.encryptedData.substring(0, 50) + '...',
    encryptedKey: encrypted.encryptedKey?.substring(0, 50) + '...',
    iv: encrypted.iv?.substring(0, 20) + '...'
  })

  // 2. 生成安全请求头
  const securityHeaders = crypto.generateSecurityHeaders()
  console.log('安全请求头:', securityHeaders)

  // 3. 生成签名
  const signature = crypto.generateSignature(
    plaintext,
    parseInt(securityHeaders.timestamp),
    securityHeaders.nonce
  )
  console.log('签名:', signature.substring(0, 50) + '...')

  // 4. 手动构建请求
  const requestBody = {
    data: encrypted.encryptedData,
    key: encrypted.encryptedKey,
    iv: encrypted.iv
  }

  // 5. 发送请求
  // ... 使用axios发送请求 ...

  // 6. 手动解密响应
  const mockEncryptedResponse = {
    data: 'encrypted_data_here',
    key: 'encrypted_key_here',
    iv: 'iv_here'
  }

  try {
    const decrypted = crypto.decryptHybrid(
      mockEncryptedResponse.data,
      mockEncryptedResponse.key,
      mockEncryptedResponse.iv
    )
    console.log('解密后的响应:', decrypted)
  } catch (error) {
    console.error('解密失败:', error)
  }
}

// ==================== 示例4：密钥生成 ====================

/**
 * 生成客户端密钥对
 */
async function exampleKeyGeneration() {
  // 生成新的密钥对
  const keyPair = await ClientCrypto.generateKeyPair()

  console.log('生成的公钥:', keyPair.publicKey)
  console.log('生成的私钥:', keyPair.privateKey)
  console.log('')
  console.log('【重要】请将公钥注册到服务端，私钥安全存储在客户端！')

  // 私钥应该存储在安全的地方，如：
  // - 浏览器的sessionStorage（会话级）
  // - 使用加密的localStorage
  // - 或者从服务端安全获取
}

// ==================== 示例5：Vue组件中使用 ====================

/**
 * Vue组件示例
 */
/*
import { cryptoApi } from '@/utils/cryptoApi'

export default {
  name: 'OrderForm',

  methods: {
    async submitOrder() {
      try {
        const orderData = {
          productId: this.productId,
          quantity: this.quantity,
          address: this.address
        }

        // 自动加密并发送
        const result = await cryptoApi.post('/api/order/create', orderData)

        this.$message.success('订单创建成功！')
        this.orderId = result.orderId

      } catch (error) {
        this.$message.error('订单创建失败：' + error.message)
      }
    },

    async fetchOrder(orderId) {
      // 查询订单（通常不需要加密GET请求）
      const order = await cryptoApi.get('/api/order/get', { orderId })
      return order
    }
  }
}
*/

// ==================== 导出示例函数 ====================

export {
  exampleWithCryptoApi,
  exampleWithAxios,
  exampleManualCrypto,
  exampleKeyGeneration
}
