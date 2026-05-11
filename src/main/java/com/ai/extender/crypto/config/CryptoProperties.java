package com.ai.extender.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加密配置属性
 *
 * 配置前缀：crypto
 *
 * 密钥使用原则：
 * - 公钥：用于加密数据
 * - 私钥：用于解密数据
 *
 * 配置灵活性：
 * - 可以配置同一对密钥（双方共用）
 * - 也可以配置不同密钥（各自持有自己的私钥，交换公钥）
 *
 * @author Crypto SDK
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "crypto")
public class CryptoProperties {

    /**
     * 是否启用加密功能
     */
    private boolean enabled = true;

    /**
     * RSA密钥配置
     */
    private Key key = new Key();

    /**
     * 签名验证配置
     */
    private Signature signature = new Signature();

    // ==================== Key 内部类 ====================

    public static class Key {
        /**
         * RSA公钥（Base64编码）
         * 用途：加密数据（请求或响应）
         */
        private String publicKey;

        /**
         * RSA私钥（Base64编码）
         * 用途：解密数据（请求或响应）
         */
        private String privateKey;

        /**
         * 是否自动生成密钥对（仅开发环境使用）
         */
        private boolean autoGenerate = false;

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public boolean isAutoGenerate() {
            return autoGenerate;
        }

        public void setAutoGenerate(boolean autoGenerate) {
            this.autoGenerate = autoGenerate;
        }
    }

    // ==================== Signature 内部类 ====================

    public static class Signature {
        /**
         * 是否启用签名验证（防重放、防篡改）
         */
        private boolean enabled = true;

        /**
         * HMAC-SHA256签名密钥（至少32字符）
         * 双方共享，用于签名验证
         */
        private String secret;

        /**
         * 时间戳有效期（毫秒）
         * 默认5分钟
         */
        private long timestampValidity = 5 * 60 * 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTimestampValidity() {
            return timestampValidity;
        }

        public void setTimestampValidity(long timestampValidity) {
            this.timestampValidity = timestampValidity;
        }
    }

    // ==================== Getters/Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    // ==================== 便捷方法 ====================

    public String getPublicKey() {
        return key.getPublicKey();
    }

    public String getPrivateKey() {
        return key.getPrivateKey();
    }

    public String getSecretKey() {
        return signature.getSecret();
    }
}
