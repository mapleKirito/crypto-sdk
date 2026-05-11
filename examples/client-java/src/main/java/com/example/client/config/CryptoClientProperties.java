package com.example.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端加密配置
 * 用于配置调用加密接口所需的密钥和安全参数
 */
@ConfigurationProperties(prefix = "crypto-client")
public class CryptoClientProperties {

    /**
     * 是否启用加密
     */
    private boolean enabled = true;

    /**
     * 服务端RSA公钥（用于加密请求）
     * 从服务端获取或通过安全渠道交换
     */
    private String serverPublicKey;

    /**
     * 客户端RSA私钥（用于解密响应）
     * 客户端自己生成，公钥需要提前注册到服务端
     */
    private String clientPrivateKey;

    /**
     * 客户端RSA公钥（可选，用于服务端加密响应）
     */
    private String clientPublicKey;

    /**
     * 签名密钥（HMAC-SHA256）
     * 与服务端共享，用于防篡改签名
     */
    private String signatureSecret;

    /**
     * 是否启用签名
     */
    private boolean signatureEnabled = true;

    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 30000;

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerPublicKey() {
        return serverPublicKey;
    }

    public void setServerPublicKey(String serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    public String getClientPrivateKey() {
        return clientPrivateKey;
    }

    public void setClientPrivateKey(String clientPrivateKey) {
        this.clientPrivateKey = clientPrivateKey;
    }

    public String getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(String clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

    public String getSignatureSecret() {
        return signatureSecret;
    }

    public void setSignatureSecret(String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
