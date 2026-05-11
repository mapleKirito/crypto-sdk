/**
 * API 加密服务封装
 *
 * 提供便捷的加密API调用方式
 */

import { CryptoMode } from './crypto'
import { createCryptoAxios } from '../plugins/cryptoAxiosConfig'

/**
 * 加密API服务
 */
export class CryptoApiService {
  private axios = createCryptoAxios()

  /**
   * 发送加密请求
   * @param url 请求地址
   * @param data 请求数据
   * @param options 加密选项
   */
  async post<T = any>(
    url: string,
    data?: any,
    options?: {
      crypto?: boolean
      cryptoMode?: CryptoMode
      skipSignature?: boolean
    }
  ): Promise<T> {
    const config: any = {
      crypto: options?.crypto !== false,
      cryptoMode: options?.cryptoMode,
      skipSignature: options?.skipSignature
    }

    const response = await this.axios.post(url, data, config)
    // 返回解密后的数据
    return response._decryptedData || response.data || response
  }

  /**
   * 发送GET请求（通常不需要加密，但可以启用）
   */
  async get<T = any>(
    url: string,
    params?: any,
    options?: {
      crypto?: boolean
    }
  ): Promise<T> {
    const config: any = {
      crypto: options?.crypto || false // GET请求默认不加密
    }

    const response = await this.axios.get(url, { params, ...config })
    return response.data
  }

  /**
   * 发送加密的PUT请求
   */
  async put<T = any>(
    url: string,
    data?: any,
    options?: {
      crypto?: boolean
      cryptoMode?: CryptoMode
    }
  ): Promise<T> {
    const config: any = {
      crypto: options?.crypto !== false,
      cryptoMode: options?.cryptoMode
    }

    const response = await this.axios.put(url, data, config)
    return response._decryptedData || response.data || response
  }

  /**
   * 发送加密的DELETE请求
   */
  async delete<T = any>(
    url: string,
    params?: any,
    options?: {
      crypto?: boolean
    }
  ): Promise<T> {
    const config: any = {
      crypto: options?.crypto || false
    }

    const response = await this.axios.delete(url, { params, ...config })
    return response.data
  }
}

// 导出单例
export const cryptoApi = new CryptoApiService()

export default cryptoApi
