package com.ai.extender.crypto.advice;

import com.ai.extender.crypto.annotation.ApiCrypto;
import com.ai.extender.crypto.config.CryptoProperties;
import com.ai.extender.crypto.util.HybridCryptoUtil;
import com.ai.extender.crypto.util.SignatureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 解密请求体增强器
 *
 * 功能：
 * 1. 安全请求头验证（防重放、防篡改）- 仅对 @ApiCrypto 注解的接口生效
 * 2. 请求体解密（RSA+AES混合加密）
 *
 * 执行时机：在消息转换器读取请求体之前
 * 触发条件：方法或类上标注 @ApiCrypto 注解且 encryptRequest=true
 *
 * @author Crypto SDK
 * @since 1.1.0
 */
@ControllerAdvice
@Order(-100)
public class CryptoRequestBodyAdvice implements RequestBodyAdvice {

    private static final Logger logger = LoggerFactory.getLogger(CryptoRequestBodyAdvice.class);

    private final CryptoProperties cryptoProperties;
    private final ObjectMapper objectMapper;

    // 用于防重放检查的 Nonce 缓存
    private final Set<String> processedNonces = new HashSet<>();

    public CryptoRequestBodyAdvice(CryptoProperties cryptoProperties, ObjectMapper objectMapper) {
        this.cryptoProperties = cryptoProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断是否需要处理该请求
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果加密功能被禁用，直接跳过
        if (!cryptoProperties.isEnabled()) {
            return false;
        }

        // 获取 @ApiCrypto 注解
        ApiCrypto apiCrypto = getApiCryptoAnnotation(methodParameter);

        return apiCrypto != null && apiCrypto.encryptRequest();
    }

    /**
     * 在读取请求体之前进行解密处理
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                            Type targetType, Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        try {
            // 读取加密的请求体
            String encryptedBody = readInputStream(inputMessage.getBody());

            if (encryptedBody == null || encryptedBody.trim().isEmpty()) {
                return inputMessage;
            }

            // 获取加密配置
            ApiCrypto apiCrypto = getApiCryptoAnnotation(parameter);
            if (apiCrypto == null) {
                return inputMessage;
            }

            // 1. 安全请求头验证（防重放、防篡改）
            if (cryptoProperties.getSignature().isEnabled()) {
                verifySecurityHeaders(inputMessage, encryptedBody);
            }

            // 2. 解析加密数据
            HybridCryptoUtil.CryptoData cryptoData = parseCryptoData(encryptedBody);

            // 3. 解密（RSA+AES混合模式）
            String decryptedBody = HybridCryptoUtil.decrypt(
                    cryptoData,
                    cryptoProperties.getPrivateKey()
            );

            logger.debug("请求体解密成功");

            // 返回新的 HttpInputMessage，包含解密后的请求体
            return new DecryptedHttpInputMessage(inputMessage, decryptedBody);

        } catch (SecurityException e) {
            // 安全验证异常（重放攻击、签名失败等），包装成 IllegalArgumentException
            // 交由业务系统的全局异常处理器处理
            logger.error("安全验证失败: {}", e.getMessage());
            throw new IllegalArgumentException("Security verification failed: " + e.getMessage(), e);
        } catch (Exception e) {
            // 其他解密异常同样包装成 IllegalArgumentException
            logger.error("请求体解密失败", e);
            throw new IllegalArgumentException("Request decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 请求体已读取后的处理（通常不需要处理）
     */
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                 Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    /**
     * 空请求体处理
     */
    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                   Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    /**
     * 验证安全请求头（防重放、防篡改）
     *
     * @param inputMessage HTTP输入消息
     * @param body 请求体内容
     * @throws SecurityException 验证失败时抛出
     */
    private void verifySecurityHeaders(HttpInputMessage inputMessage, String body) throws Exception {
        org.springframework.http.HttpHeaders headers = inputMessage.getHeaders();

        String timestampStr = headers.getFirst("X-Timestamp");
        String nonce = headers.getFirst("X-Nonce");
        String signature = headers.getFirst("X-Signature");

        // 检查必要头信息
        if (timestampStr == null || nonce == null) {
            logger.warn("缺少必要的安全请求头: X-Timestamp 或 X-Nonce");
            throw new SecurityException("Missing required security headers: X-Timestamp or X-Nonce");
        }

        // 防重放检查
        synchronized (processedNonces) {
            if (processedNonces.contains(nonce)) {
                logger.warn("检测到重放攻击: nonce={}", nonce);
                throw new SecurityException("Replay attack detected");
            }
        }

        // 检查时间戳
        try {
            long timestamp = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis();
            long validity = cryptoProperties.getSignature().getTimestampValidity();

            if (Math.abs(currentTime - timestamp) > validity) {
                logger.warn("请求时间戳过期: timestamp={}, currentTime={}, diff={}ms",
                        timestamp, currentTime, Math.abs(currentTime - timestamp));
                throw new SecurityException("Request timestamp expired");
            }
        } catch (NumberFormatException e) {
            logger.warn("无效的时间戳格式: {}", timestampStr);
            throw new SecurityException("Invalid timestamp format");
        }

        // 防篡改检查
        if (cryptoProperties.getSecretKey() != null) {
            if (signature == null) {
                logger.warn("缺少签名请求头: X-Signature");
                throw new SecurityException("Missing signature header: X-Signature");
            }

            long timestamp = Long.parseLong(timestampStr);
            boolean valid = SignatureUtil.verifyBody(body, timestamp, nonce, signature, cryptoProperties.getSecretKey());
            if (!valid) {
                logger.warn("签名验证失败");
                throw new SecurityException("Signature verification failed");
            }
        }

        // 记录已使用的 nonce
        synchronized (processedNonces) {
            processedNonces.add(nonce);
            // 清理过期的 nonce（简单策略：当集合过大时清空）
            if (processedNonces.size() > 10000) {
                processedNonces.clear();
            }
        }

        logger.debug("安全请求头验证通过");
    }

    /**
     * 解析加密数据
     */
    private HybridCryptoUtil.CryptoData parseCryptoData(String encryptedBody) throws Exception {
        return objectMapper.readValue(encryptedBody, HybridCryptoUtil.CryptoData.class);
    }

    /**
     * 读取输入流内容
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 获取方法或类上的 @ApiCrypto 注解
     */
    private ApiCrypto getApiCryptoAnnotation(MethodParameter methodParameter) {
        // 1. 先查找方法上的注解
        Method method = methodParameter.getMethod();
        if (method != null) {
            ApiCrypto apiCrypto = method.getAnnotation(ApiCrypto.class);
            if (apiCrypto != null) {
                return apiCrypto;
            }
        }

        // 2. 查找类上的注解
        Class<?> declaringClass = methodParameter.getDeclaringClass();
        return declaringClass.getAnnotation(ApiCrypto.class);
    }

    /**
     * 解密后的 HttpInputMessage 实现
     */
    private static class DecryptedHttpInputMessage implements HttpInputMessage {
        private final HttpInputMessage original;
        private final String decryptedBody;

        public DecryptedHttpInputMessage(HttpInputMessage original, String decryptedBody) {
            this.original = original;
            this.decryptedBody = decryptedBody;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(decryptedBody.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return original.getHeaders();
        }
    }
}
