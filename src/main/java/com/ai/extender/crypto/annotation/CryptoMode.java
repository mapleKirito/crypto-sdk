package com.ai.extender.crypto.annotation;

/**
 * 加密模式枚举
 *
 * 当前版本统一使用 RSA_AES_HYBRID 混合加密模式：
 * - RSA 用于加密 AES 密钥（密钥交换）
 * - AES 用于加密实际数据（高性能）
 *
 * @author Crypto SDK
 * @since 1.1.0
 */
public enum CryptoMode {

    /**
     * RSA+AES 混合加密模式（推荐）
     *
     * 加密流程：
     * 1. 生成随机 AES 密钥
     * 2. AES 加密数据
     * 3. RSA 公钥加密 AES 密钥
     *
     * 解密流程：
     * 1. RSA 私钥解密得到 AES 密钥
     * 2. AES 解密数据
     */
    RSA_AES_HYBRID
}
