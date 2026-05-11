/**
 * 客户端加密工具类 - 前端Vue版本
 *
 * 使用 RSA+AES 混合加密模式：
 * - RSA 用于加密 AES 密钥（安全密钥交换）
 * - AES 用于加密实际数据（高性能）
 *
 * 功能：
 * 1. 请求加密（使用服务端公钥）
 * 2. 响应解密（使用客户端私钥）
 * 3. HMAC-SHA256签名（用于防篡改）
 *
 * 依赖：jsencrypt、crypto-js
 * 安装：npm install jsencrypt@3.0.0-beta.1 crypto-js
 *
 * @author Crypto SDK
 * @since 1.1.0
 */

import JSEncrypt from 'jsencrypt'
import CryptoJS from 'crypto-js'

/**
 * 加密结果
 */
export interface EncryptResult {
  encryptedData: string
  encryptedKey: string
}

/**
 * 客户端加密工具
 */
export class ClientCrypto {
  private serverPublicKey: string = ''
  private clientPrivateKey: string = ''
  private signatureSecret: string = ''
  private signatureEnabled: boolean = true

  constructor(config: CryptoClientConfig) {
    this.serverPublicKey = config.serverPublicKey || ''
    this.clientPrivateKey = config.clientPrivateKey || ''
    this.signatureSecret = config.signatureSecret || ''
    this.signatureEnabled = config.signatureEnabled !== false
  }

  /**
   * 更新配置
   */
  public updateConfig(config: Partial<CryptoClientConfig>) {
    if (config.serverPublicKey) this.serverPublicKey = config.serverPublicKey
    if (config.clientPrivateKey) this.clientPrivateKey = config.clientPrivateKey
    if (config.signatureSecret) this.signatureSecret = config.signatureSecret
    if (config.signatureEnabled !== undefined) this.signatureEnabled = config.signatureEnabled
  }

  /**
   * 加密数据（RSA+AES混合模式）
   * @param plaintext 明文数据
   * @returns 加密结果
   */
  public encrypt(plaintext: string): EncryptResult {
    // 1. 生成随机AES密钥（32字节=256位）
    const aesKey = CryptoJS.lib.WordArray.random(32)

    // 2. 生成随机IV（12字节=96位）
    const iv = CryptoJS.lib.WordArray.random(12)

    // 3. AES加密数据
    const encrypted = CryptoJS.AES.encrypt(plaintext, aesKey, {
      iv: iv,
      mode: CryptoJS.mode.GCM,
      padding: CryptoJS.pad.NoPadding
    })

    // 4. 组合IV和加密数据
    const combined = iv.clone()
    combined.concat(encrypted.ciphertext)

    // 5. RSA加密AES密钥
    const encryptor = new JSEncrypt()
    encryptor.setPublicKey(this.serverPublicKey)
    const encryptedKey = encryptor.encrypt(aesKey.toString(CryptoJS.enc.Base64))

    if (!encryptedKey) {
      throw new Error('RSA encryption failed')
    }

    return {
      encryptedData: combined.toString(CryptoJS.enc.Base64),
      encryptedKey: encryptedKey
    }
  }

  /**
   * 解密数据（RSA+AES混合模式）
   */
  public decrypt(encryptedData: string, encryptedKey: string): string {
    // 1. RSA解密获取AES密钥
    const decryptor = new JSEncrypt()
    decryptor.setPrivateKey(this.clientPrivateKey)
    const aesKeyBase64 = decryptor.decrypt(encryptedKey)

    if (!aesKeyBase64) {
      throw new Error('RSA decryption failed')
    }

    const aesKey = CryptoJS.enc.Base64.parse(aesKeyBase64)

    // 2. 解析加密数据（分离IV和密文）
    const combined = CryptoJS.enc.Base64.parse(encryptedData)
    const ivWordArray = CryptoJS.lib.WordArray.create(combined.words.slice(0, 3), 12)
    const cipherText = CryptoJS.lib.WordArray.create(
      combined.words.slice(3),
      combined.sigBytes - 12
    )

    // 3. AES解密
    const decrypted = CryptoJS.AES.decrypt(
      { ciphertext: cipherText } as CryptoJS.lib.CipherParams,
      aesKey,
      {
        iv: ivWordArray,
        mode: CryptoJS.mode.GCM,
        padding: CryptoJS.pad.NoPadding
      }
    )

    return decrypted.toString(CryptoJS.enc.Utf8)
  }

  /**
   * 解密响应
   */
  public decryptResponse(response: any): any {
    // 检查是否加密
    if (!response.data || !response.key) {
      return response
    }

    try {
      const decryptedData = this.decrypt(response.data, response.key)
      response._decryptedData = JSON.parse(decryptedData)
      return response
    } catch (error) {
      console.error('Decryption failed:', error)
      throw error
    }
  }

  /**
   * 生成签名
   * @param body 请求体
   * @param timestamp 时间戳
   * @param nonce 随机数
   * @returns Base64编码的签名
   */
  public generateSignature(body: string, timestamp: number, nonce: string): string {
    const content = `${body}|${timestamp}|${nonce}`
    return CryptoJS.HmacSHA256(content, this.signatureSecret).toString(CryptoJS.enc.Base64)
  }

  /**
   * 验证签名
   */
  public verifySignature(body: string, timestamp: number, nonce: string, signature: string): boolean {
    const expected = this.generateSignature(body, timestamp, nonce)
    return this.constantTimeEquals(expected, signature)
  }

  /**
   * 生成防重放请求头
   * @returns 包含timestamp和nonce的对象
   */
  public generateSecurityHeaders(): { timestamp: string; nonce: string } {
    return {
      timestamp: Date.now().toString(),
      nonce: this.generateUUID()
    }
  }

  /**
   * 生成UUID
   */
  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0
      const v = c === 'x' ? r : (r & 0x3) | 0x8
      return v.toString(16)
    })
  }

  /**
   * 常量时间比较，防止时序攻击
   */
  private constantTimeEquals(a: string, b: string): boolean {
    if (a.length !== b.length) {
      return false
    }
    let result = 0
    for (let i = 0; i < a.length; i++) {
      result |= a.charCodeAt(i) ^ b.charCodeAt(i)
    }
    return result === 0
  }

  /**
   * 生成RSA密钥对
   * @returns Promise<{ publicKey: string; privateKey: string }>
   */
  public static async generateKeyPair(): Promise<{ publicKey: string; privateKey: string }> {
    return new Promise((resolve, reject) => {
      const key = new JSEncrypt({ default_key_size: '2048' })
      key.getKey((key: any) => {
        if (key) {
          resolve({
            publicKey: key.getPublicKey(),
            privateKey: key.getPrivateKey()
          })
        } else {
          reject(new Error('Key generation failed'))
        }
      })
    })
  }
}

/**
 * 加密客户端配置
 */
export interface CryptoClientConfig {
  /** 服务端RSA公钥（用于加密请求） */
  serverPublicKey: string
  /** 客户端RSA私钥（用于解密响应） */
  clientPrivateKey: string
  /** 签名密钥（HMAC-SHA256） */
  signatureSecret: string
  /** 是否启用签名 */
  signatureEnabled?: boolean
}

// 导出单例实例（需要初始化）
let cryptoInstance: ClientCrypto | null = null

/**
 * 初始化加密客户端
 */
export function initCryptoClient(config: CryptoClientConfig): ClientCrypto {
  cryptoInstance = new ClientCrypto(config)
  return cryptoInstance
}

/**
 * 获取加密客户端实例
 */
export function getCryptoClient(): ClientCrypto {
  if (!cryptoInstance) {
    throw new Error('Crypto client not initialized. Call initCryptoClient() first.')
  }
  return cryptoInstance
}

export default ClientCrypto
