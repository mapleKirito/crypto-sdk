/**
 * 加密Axios配置示例
 *
 * 演示如何配置使用加密功能的Axios实例
 */

import axios, { AxiosInstance } from 'axios'
import { CryptoAxiosPlugin, CryptoAxiosPluginConfig } from '../plugins/cryptoAxios'

/**
 * 加密Axios配置
 */
const cryptoConfig: CryptoAxiosPluginConfig = {
  // 服务端RSA公钥（用于加密请求）
  // 从服务端获取，必须使用服务端公钥加密请求
  serverPublicKey: `-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC+xxxxx服务端公钥xxxxx
-----END PUBLIC KEY-----`,

  // 客户端RSA私钥（用于解密响应）
  // 客户端自己生成，公钥需要提前注册到服务端
  clientPrivateKey: `-----BEGIN RSA PRIVATE KEY-----
MIICXAIBAAKBgQC+xxxxx客户端私钥xxxxx
-----END RSA PRIVATE KEY-----`,

  // 客户端RSA公钥（服务端用于加密响应）
  clientPublicKey: `-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC+xxxxx客户端公钥xxxxx
-----END PUBLIC KEY-----`,

  // 签名密钥（HMAC-SHA256）
  // 与服务端共享，用于防篡改
  signatureSecret: 'your-32-char-signature-secret-key',

  // 是否启用签名
  signatureEnabled: true,

  // 是否对所有请求启用加密
  enabled: true,

  // 默认加密模式
  defaultMode: 'RSA_AES_HYBRID' as const,

  // 是否自动添加签名
  autoSignature: true
}

/**
 * 创建加密Axios实例
 */
export function createCryptoAxios(baseURL?: string): AxiosInstance {
  const instance = axios.create({
    baseURL: baseURL || '/api',
    timeout: 30000,
    headers: {
      'Content-Type': 'application/json'
    }
  })

  // 安装加密插件
  CryptoAxiosPlugin.install(instance, cryptoConfig)

  return instance
}

/**
 * 加密Axios实例（单例）
 */
export const cryptoAxios = createCryptoAxios()

export default cryptoAxios
