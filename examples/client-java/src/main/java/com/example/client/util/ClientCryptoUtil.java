package com.example.client.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 客户端加密工具类
 * JDK 8兼容版本
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
 * @author Crypto SDK
 * @since 1.1.0
 */
public class ClientCryptoUtil {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_KEY_ALGORITHM = "AES";

    private static final int AES_KEY_SIZE = 256;        // 256 bits
    private static final int GCM_IV_LENGTH = 12;        // 96 bits for GCM
    private static final int GCM_TAG_LENGTH = 128;      // 128 bits

    /**
     * 加密结果封装
     */
    public static class EncryptResult {
        private String encryptedData;
        private String encryptedKey;

        public String getEncryptedData() { return encryptedData; }
        public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
        public String getEncryptedKey() { return encryptedKey; }
        public void setEncryptedKey(String encryptedKey) { this.encryptedKey = encryptedKey; }
    }

    // ==================== 加密方法 ====================

    /**
     * 加密数据（RSA+AES混合模式）
     *
     * @param plaintext 明文数据
     * @param serverPublicKey 服务端RSA公钥
     * @return 加密结果
     */
    public static EncryptResult encrypt(String plaintext, String serverPublicKey) throws Exception {
        // 1. 生成随机AES密钥
        byte[] aesKeyBytes = generateRandomBytes(AES_KEY_SIZE / 8);

        // 2. 生成随机IV
        byte[] iv = generateRandomBytes(GCM_IV_LENGTH);

        // 3. AES加密数据
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedData = aesEncrypt(plaintextBytes, aesKeyBytes, iv);

        // 4. 组合IV和加密数据
        byte[] combined = new byte[GCM_IV_LENGTH + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedData, 0, combined, GCM_IV_LENGTH, encryptedData.length);

        // 5. RSA加密AES密钥
        byte[] encryptedKey = rsaEncrypt(aesKeyBytes, serverPublicKey);

        EncryptResult result = new EncryptResult();
        result.setEncryptedData(Base64.getEncoder().encodeToString(combined));
        result.setEncryptedKey(Base64.getEncoder().encodeToString(encryptedKey));
        return result;
    }

    // ==================== 解密方法 ====================

    /**
     * 解密数据（RSA+AES混合模式）
     *
     * @param encryptedData 加密数据
     * @param encryptedKey 加密的AES密钥
     * @param clientPrivateKey 客户端RSA私钥
     * @return 解密后的明文
     */
    public static String decrypt(String encryptedData, String encryptedKey,
                                  String clientPrivateKey) throws Exception {
        // 1. RSA解密获取AES密钥
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedKey);
        byte[] aesKeyBytes = rsaDecrypt(encryptedKeyBytes, clientPrivateKey);

        // 2. 解析加密数据
        byte[] combined = Base64.getDecoder().decode(encryptedData);
        byte[] ivBytes = new byte[GCM_IV_LENGTH];
        byte[] cipherData = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, ivBytes, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, cipherData, 0, cipherData.length);

        // 3. AES解密
        byte[] decrypted = aesDecrypt(cipherData, aesKeyBytes, ivBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ==================== 签名方法 ====================

    /**
     * 生成签名
     * @param body 请求体
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param secret 签名密钥
     * @return Base64编码的签名
     */
    public static String generateSignature(String body, long timestamp, String nonce, String secret) throws Exception {
        String content = body + "|" + timestamp + "|" + nonce;
        return hmacSHA256(content, secret);
    }

    /**
     * 验证签名
     * @param body 请求体
     * @param timestamp 时间戳
     * @param nonce 随机数
     * @param signature 签名
     * @param secret 签名密钥
     * @return 是否验证通过
     */
    public static boolean verifySignature(String body, long timestamp, String nonce,
                                          String signature, String secret) throws Exception {
        String expected = generateSignature(body, timestamp, nonce, secret);
        return constantTimeEquals(expected, signature);
    }

    /**
     * 生成防重放请求头
     * @return 包含timestamp和nonce的数组 [timestamp, nonce]
     */
    public static String[] generateSecurityHeaders() {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        return new String[]{String.valueOf(timestamp), nonce};
    }

    // ==================== 密钥生成方法 ====================

    /**
     * 生成RSA密钥对
     * @return [publicKey, privateKey]
     */
    public static String[] generateRSAKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        keyGen.initialize(2048, new SecureRandom());
        KeyPair pair = keyGen.generateKeyPair();

        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        return new String[]{publicKey, privateKey};
    }

    // ==================== 内部工具方法 ====================

    private static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static byte[] rsaEncrypt(byte[] data, String publicKeyStr) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        PublicKey publicKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    private static byte[] rsaDecrypt(byte[] data, String privateKeyStr) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyStr);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(spec);

        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    private static byte[] aesEncrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_KEY_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(data);
    }

    private static byte[] aesDecrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_KEY_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher.doFinal(data);
    }

    private static String hmacSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
