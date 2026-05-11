package com.ai.extender.crypto.aspect;

import com.ai.extender.crypto.annotation.ApiCrypto;
import com.ai.extender.crypto.config.CryptoProperties;
import com.ai.extender.crypto.util.SignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 加密工具组件
 *
 * 提供安全请求头生成和验证的工具方法
 *
 * 注意：
 * - 请求体解密由 CryptoRequestBodyAdvice 处理
 * - 响应体加密由 CryptoResponseBodyAdvice 处理
 * - 安全请求头验证由 CryptoFilter 处理
 *
 * 此组件主要提供工具方法供业务代码或调用方使用
 *
 * @author Crypto SDK
 * @since 1.1.0
 */
@Component
public class CryptoAspect {

    private static final Logger logger = LoggerFactory.getLogger(CryptoAspect.class);

    private final CryptoProperties cryptoProperties;

    public CryptoAspect(CryptoProperties cryptoProperties) {
        this.cryptoProperties = cryptoProperties;
    }

    /**
     * 生成防重放攻击的请求头
     *
     * 供调用方使用，生成调用加密接口所需的安全请求头
     *
     * @param body 请求体内容
     * @return 包含 X-Timestamp、X-Nonce、X-Signature 的请求头Map
     */
    public Map<String, String> generateSecurityHeaders(String body) throws Exception {
        Map<String, String> headers = new HashMap<>();

        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");

        headers.put("X-Timestamp", String.valueOf(timestamp));
        headers.put("X-Nonce", nonce);

        // 生成签名
        if (body != null && cryptoProperties.getSecretKey() != null) {
            String signature = SignatureUtil.signBody(body, timestamp, nonce, cryptoProperties.getSecretKey());
            headers.put("X-Signature", signature);
        }

        return headers;
    }

    /**
     * 验证请求头（防重放和防篡改）
     *
     * 供业务代码手动验证使用
     * 注意：CryptoFilter 已自动处理此验证，通常不需要手动调用
     *
     * @param request HTTP请求
     * @param body 请求体内容
     * @return 验证是否通过
     */
    public boolean verifySecurityHeaders(HttpServletRequest request, String body) {
        try {
            String timestampStr = request.getHeader("X-Timestamp");
            String nonce = request.getHeader("X-Nonce");
            String signature = request.getHeader("X-Signature");

            if (timestampStr == null || nonce == null || signature == null) {
                logger.warn("缺少安全请求头");
                return false;
            }

            long timestamp = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis();

            // 检查时间戳（允许5分钟误差）
            if (Math.abs(currentTime - timestamp) > 5 * 60 * 1000) {
                logger.warn("请求时间戳过期");
                return false;
            }

            // 验证签名
            if (cryptoProperties.getSecretKey() != null) {
                boolean valid = SignatureUtil.verifyBody(body, timestamp, nonce, signature, cryptoProperties.getSecretKey());
                if (!valid) {
                    logger.warn("签名验证失败");
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("验证安全请求头失败", e);
            return false;
        }
    }
}
