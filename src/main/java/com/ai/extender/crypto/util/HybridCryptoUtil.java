package com.ai.extender.crypto.util;

import com.ai.extender.crypto.annotation.CryptoMode;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类
 *
 * 使用 RSA+AES 混合加密模式：
 * - RSA 用于加密 AES 密钥（安全密钥交换）
 * - AES 用于加密实际数据（高性能）
 *
 * 优势：
 * 1. 安全性高：AES 密钥加密传输，无法被截获
 * 2. 性能好：AES 加密大数据，RSA 仅加密密钥
 * 3. 数据量无限制：不受 RSA 加密长度限制
 *
 * JDK 8兼容版本
 *
 * @author Crypto SDK
 * @since 1.1.0
 */
public class HybridCryptoUtil {

    private static final int AES_KEY_SIZE = 32; // 256 bits = 32 bytes
    private static final int IV_SIZE = 12; // 96 bits for GCM

    /**
     * 加密数据
     *
     * @param plaintext 明文
     * @param publicKey Base64编码的RSA公钥
     * @return 加密后的数据包
     */
    public static CryptoData encrypt(String plaintext, String publicKey) throws Exception {
        // 1. 生成随机AES密钥
        byte[] aesKeyBytes = new byte[AES_KEY_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(aesKeyBytes);

        // 2. 生成随机IV
        byte[] iv = new byte[IV_SIZE];
        random.nextBytes(iv);

        // 3. AES加密数据
        byte[] plaintextBytes = plaintext.getBytes("UTF-8");
        byte[] encryptedData = AESUtil.encryptWithIV(plaintextBytes, aesKeyBytes, iv);

        // 4. 组合IV和密文
        byte[] combined = new byte[IV_SIZE + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(encryptedData, 0, combined, IV_SIZE, encryptedData.length);

        // 5. RSA加密AES密钥
        byte[] encryptedKey = RSAUtil.encryptBytesByPublicKey(aesKeyBytes, publicKey);

        CryptoData data = new CryptoData();
        data.setEncryptedData(Base64.getEncoder().encodeToString(combined));
        data.setEncryptedKey(Base64.getEncoder().encodeToString(encryptedKey));
        return data;
    }

    /**
     * 解密数据
     *
     * @param cryptoData 加密数据包
     * @param privateKey Base64编码的RSA私钥
     * @return 明文
     */
    public static String decrypt(CryptoData cryptoData, String privateKey) throws Exception {
        // 1. RSA解密AES密钥
        byte[] encryptedKey = Base64.getDecoder().decode(cryptoData.getEncryptedKey());
        byte[] aesKeyBytes = RSAUtil.decryptBytesByPrivateKey(encryptedKey, privateKey);

        // 2. 分离IV和密文
        byte[] combined = Base64.getDecoder().decode(cryptoData.getEncryptedData());
        byte[] iv = new byte[IV_SIZE];
        byte[] encryptedData = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);
        System.arraycopy(combined, IV_SIZE, encryptedData, 0, encryptedData.length);

        // 3. AES解密数据
        byte[] plaintextBytes = AESUtil.decryptWithIV(encryptedData, aesKeyBytes, iv);
        return new String(plaintextBytes, "UTF-8");
    }

    /**
     * 加密数据包
     */
    public static class CryptoData {
        private String encryptedData;
        private String encryptedKey;

        public String getEncryptedData() {
            return encryptedData;
        }

        public void setEncryptedData(String encryptedData) {
            this.encryptedData = encryptedData;
        }

        public String getEncryptedKey() {
            return encryptedKey;
        }

        public void setEncryptedKey(String encryptedKey) {
            this.encryptedKey = encryptedKey;
        }
    }
}
