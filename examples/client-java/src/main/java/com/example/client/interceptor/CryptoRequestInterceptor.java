package com.example.client.interceptor;

import com.example.client.config.CryptoClientProperties;
import com.example.client.util.ClientCryptoUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 加密请求拦截器
 *
 * 功能：
 * 1. 自动加密请求体（使用服务端公钥）
 * 2. 自动添加安全请求头（Timestamp、Nonce、Signature）
 * 3. 自动解密响应体（使用客户端私钥）
 *
 * 使用方式：
 * - 配置到RestTemplate即可自动处理加解密
 * - 通过CryptoMode决定加密模式
 */
public class CryptoRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CryptoRequestInterceptor.class);

    private final CryptoClientProperties properties;
    private final ClientCryptoUtil.CryptoMode defaultMode;

    public CryptoRequestInterceptor(CryptoClientProperties properties) {
        this(properties, ClientCryptoUtil.CryptoMode.RSA_AES_HYBRID);
    }

    public CryptoRequestInterceptor(CryptoClientProperties properties, ClientCryptoUtil.CryptoMode defaultMode) {
        this.properties = properties;
        this.defaultMode = defaultMode;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        try {
            // 1. 如果加密被禁用，直接发送原始请求
            if (!properties.isEnabled()) {
                return execution.execute(request, body);
            }

            // 2. 生成安全请求头（防重放）
            String[] securityHeaders = ClientCryptoUtil.generateSecurityHeaders();
            String timestamp = securityHeaders[0];
            String nonce = securityHeaders[1];

            // 3. 加密请求体
            String requestBody = new String(body, StandardCharsets.UTF_8);
            EncryptedRequest encryptedRequest = encryptRequest(requestBody);

            // 4. 生成签名（防篡改）
            String signature = "";
            if (properties.isSignatureEnabled() && properties.getSignatureSecret() != null) {
                signature = ClientCryptoUtil.generateSignature(
                        requestBody, Long.parseLong(timestamp), nonce, properties.getSignatureSecret());
            }

            // 5. 设置请求头
            request.getHeaders().add("X-Timestamp", timestamp);
            request.getHeaders().add("X-Nonce", nonce);
            request.getHeaders().add("X-Signature", signature);
            request.getHeaders().add("X-Encrypted", "true");
            request.getHeaders().add("X-Encrypted-Key", encryptedRequest.encryptedKey);
            if (encryptedRequest.iv != null) {
                request.getHeaders().add("X-IV", encryptedRequest.iv);
            }
            request.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

            // 6. 执行请求
            byte[] encryptedBody = encryptedRequest.encryptedData.getBytes(StandardCharsets.UTF_8);
            ClientHttpResponse response = execution.execute(request, encryptedBody);

            // 7. 解密响应（如果响应被加密）
            return new CryptoResponseWrapper(response, properties);

        } catch (Exception e) {
            logger.error("加密请求处理失败", e);
            throw new IOException("Crypto request processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * 加密请求体
     */
    private EncryptedRequest encryptRequest(String plaintext) throws Exception {
        EncryptedRequest result = new EncryptedRequest();

        switch (defaultMode) {
            case RSA_ONLY:
                ClientCryptoUtil.EncryptResult rsaResult =
                    ClientCryptoUtil.encryptRSAOnly(plaintext, properties.getServerPublicKey());
                result.encryptedData = rsaResult.getEncryptedData();
                break;

            case RSA_AES_HYBRID:
            default:
                ClientCryptoUtil.EncryptResult hybridResult =
                    ClientCryptoUtil.encryptHybrid(plaintext, properties.getServerPublicKey());
                result.encryptedData = hybridResult.getEncryptedData();
                result.encryptedKey = hybridResult.getEncryptedKey();
                break;
        }

        return result;
    }

    /**
     * 加密请求封装
     */
    private static class EncryptedRequest {
        String encryptedData;
        String encryptedKey;
        String iv;
    }
}
