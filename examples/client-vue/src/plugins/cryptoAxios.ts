/**
 * Axios 加密请求拦截器插件
 *
 * 功能：
 * 1. 自动加密请求体（RSA+AES混合模式）
 * 2. 自动添加安全请求头（Timestamp、Nonce、Signature）
 * 3. 自动解密响应体（使用客户端私钥）
 *
 * 使用方式：
 * import { CryptoAxiosPlugin } from './plugins/cryptoAxios'
 * axios.use(CryptoAxiosPlugin, { serverPublicKey, clientPrivateKey, signatureSecret })
 *
 * @author Crypto SDK
 * @since 1.1.0
 */

import { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'
import { ClientCrypto, initCryptoClient, CryptoClientConfig } from '../utils/crypto'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 是否加密此请求 */
    crypto?: boolean
    /** 是否跳过签名 */
    skipSignature?: boolean
  }

  interface AxiosResponse {
    /** 解密后的数据 */
    _decryptedData?: any
  }
}

/**
 * 加密Axios插件配置
 */
export interface CryptoAxiosPluginConfig extends CryptoClientConfig {
  /** 是否对所有请求启用加密（默认true） */
  enabled?: boolean
  /** 是否自动添加签名 */
  autoSignature?: boolean
}

/**
 * 创建加密Axios拦截器
 */
export function createCryptoInterceptor(config: CryptoAxiosPluginConfig) {
  let crypto: ClientCrypto | null = null

  // 初始化加密客户端
  const initCrypto = () => {
    if (!crypto) {
      crypto = initCryptoClient({
        serverPublicKey: config.serverPublicKey,
        clientPrivateKey: config.clientPrivateKey,
        signatureSecret: config.signatureSecret,
        signatureEnabled: config.signatureEnabled !== false
      })
    }
    return crypto
  }

  // 请求拦截器
  const onRequest = (config: AxiosRequestConfig): AxiosRequestConfig => {
    // 检查是否启用加密
    const enabled = config.crypto !== false && (config.enabled !== false)
    if (!enabled) {
      return config
    }

    const cryptoClient = initCrypto()

    // 如果没有请求体，直接返回
    if (!config.data) {
      return config
    }

    try {
      // 1. 生成安全请求头（防重放）
      const securityHeaders = cryptoClient.generateSecurityHeaders()
      config.headers!['X-Timestamp'] = securityHeaders.timestamp
      config.headers!['X-Nonce'] = securityHeaders.nonce

      // 2. 处理请求数据
      let requestBody = config.data
      if (typeof requestBody === 'object') {
        requestBody = JSON.stringify(requestBody)
      }

      // 3. 加密请求体（RSA+AES混合模式）
      const encrypted = cryptoClient.encrypt(requestBody)
      config.data = {
        data: encrypted.encryptedData,
        key: encrypted.encryptedKey
      }

      // 4. 添加加密标识头
      config.headers!['X-Encrypted'] = 'true'
      config.headers!['X-Encrypted-Key'] = encrypted.encryptedKey

      // 5. 生成签名（防篡改）
      if (config.skipSignature !== true && config.autoSignature !== false) {
        const signature = cryptoClient.generateSignature(
          requestBody,
          parseInt(securityHeaders.timestamp),
          securityHeaders.nonce
        )
        config.headers!['X-Signature'] = signature
      }

      // 6. 设置Content-Type
      config.headers!['Content-Type'] = 'application/json;charset=UTF-8'

    } catch (error) {
      console.error('[Crypto] Request encryption failed:', error)
      throw error
    }

    return config
  }

  // 响应拦截器
  const onResponse = (response: AxiosResponse): AxiosResponse => {
    // 检查是否需要解密
    if (!response.data || !response.data.key) {
      return response
    }

    try {
      const cryptoClient = initCrypto()

      // 解密响应
      const decryptedData = cryptoClient.decrypt(response.data.data, response.data.key)

      // 解析解密后的数据
      response._decryptedData = JSON.parse(decryptedData)

      // 替换响应数据
      response.data = response._decryptedData

    } catch (error) {
      console.error('[Crypto] Response decryption failed:', error)
      // 解密失败不影响原始响应
    }

    return response
  }

  // 错误处理
  const onError = (error: AxiosError): Promise<any> => {
    // 如果是加密相关错误，增强错误信息
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        error.message = '[Crypto] Security verification failed: ' + (data?.message || 'Unauthorized')
      } else if (status === 403) {
        error.message = '[Crypto] Access forbidden: ' + (data?.message || 'Forbidden')
      }
    }
    return Promise.reject(error)
  }

  return {
    onRequest,
    onResponse,
    onError
  }
}

/**
 * 加密Axios插件（适用于Vue项目的便捷导出）
 */
export const CryptoAxiosPlugin = {
  /**
   * 安装插件
   * @param axios Axios实例
   * @param config 加密配置
   */
  install(axios: AxiosInstance, config: CryptoAxiosPluginConfig): void {
    const interceptor = createCryptoInterceptor(config)

    // 添加请求拦截器
    axios.interceptors.request.use(
      interceptor.onRequest,
      interceptor.onError
    )

    // 添加响应拦截器
    axios.interceptors.response.use(
      interceptor.onResponse,
      interceptor.onError
    )

    console.log('[Crypto] Axios crypto plugin installed')
  }
}

export default CryptoAxiosPlugin
