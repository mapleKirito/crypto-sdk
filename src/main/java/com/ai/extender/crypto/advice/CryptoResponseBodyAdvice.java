package com.ai.extender.crypto.advice;

import com.ai.extender.crypto.annotation.ApiCrypto;
import com.ai.extender.crypto.config.CryptoProperties;
import com.ai.extender.crypto.util.HybridCryptoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 加密响应体增强器
 *
 * 使用 ResponseBodyAdvice 替代 AOP 切面处理响应加密，解决以下问题：
 * 1. 避免与统一响应包装类（如 R、Result 等）的类型冲突
 * 2. 提高与第三方项目的兼容性
 * 3. 在消息转换器写入前介入，不影响 Controller 返回类型链路
 *
 * 执行时机：在消息转换器写入响应体之前
 * 触发条件：方法或类上标注 @ApiCrypto 注解且 encryptResponse=true
 *
 * 加密模式：RSA+AES 混合加密
 *
 * @author Crypto SDK
 * @since 1.1.0
 */
@ControllerAdvice
@Order(-100)
public class CryptoResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger logger = LoggerFactory.getLogger(CryptoResponseBodyAdvice.class);

    private final CryptoProperties cryptoProperties;
    private final ObjectMapper objectMapper;

    // 使用 ThreadLocal 存储当前请求的加密配置
    private final ThreadLocal<ApiCrypto> currentApiCrypto = new ThreadLocal<>();

    public CryptoResponseBodyAdvice(CryptoProperties cryptoProperties, ObjectMapper objectMapper) {
        this.cryptoProperties = cryptoProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断是否需要处理该响应
     *
     * @param returnType 方法返回类型
     * @param converterType 消息转换器类型
     * @return true表示需要处理，false表示跳过
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果加密功能被禁用，直接跳过
        if (!cryptoProperties.isEnabled()) {
            return false;
        }

        // 获取方法上的 @ApiCrypto 注解
        ApiCrypto apiCrypto = getApiCryptoAnnotation(returnType);

        if (apiCrypto != null) {
            // 存储到 ThreadLocal 供 beforeBodyWrite 使用
            currentApiCrypto.set(apiCrypto);
            return apiCrypto.encryptResponse();
        }

        return false;
    }

    /**
     * 在响应体写入前进行加密处理
     *
     * @param body 原始响应体
     * @param returnType 方法返回类型
     * @param selectedContentType 选中的内容类型
     * @param selectedConverterType 选中的转换器类型
     * @param request 当前请求
     * @param response 当前响应
     * @return 加密后的响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        try {
            // 获取当前请求的加密配置
            ApiCrypto apiCrypto = currentApiCrypto.get();

            if (apiCrypto == null || body == null) {
                return body;
            }

            // 执行加密
            return encryptResponseBody(body);

        } catch (Exception e) {
            logger.error("响应加密失败", e);
            // 加密失败时返回原始响应，避免影响业务
            return body;
        } finally {
            // 清理 ThreadLocal
            currentApiCrypto.remove();
        }
    }

    /**
     * 加密响应体
     *
     * @param body 原始响应体
     * @return 加密后的数据结构
     */
    private Object encryptResponseBody(Object body) throws Exception {
        // 将响应体转换为 JSON 字符串
        String plaintext;
        if (body instanceof String) {
            plaintext = (String) body;
        } else if (body instanceof byte[]) {
            plaintext = new String((byte[]) body, "UTF-8");
        } else {
            plaintext = objectMapper.writeValueAsString(body);
        }

        // 获取公钥（用于加密响应）
        String publicKey = cryptoProperties.getPublicKey();
        if (publicKey == null || publicKey.trim().isEmpty()) {
            logger.warn("公钥未配置，响应将不会被加密");
            return body;
        }

        // 执行加密（RSA+AES混合模式）
        HybridCryptoUtil.CryptoData cryptoData = HybridCryptoUtil.encrypt(
                plaintext,
                publicKey
        );

        logger.debug("响应加密成功");

        // 返回加密数据结构（Map形式，兼容各种JSON序列化）
        Map<String, Object> encryptedResponse = new HashMap<>();
        encryptedResponse.put("encrypted", true);
        encryptedResponse.put("data", cryptoData.getEncryptedData());
        encryptedResponse.put("key", cryptoData.getEncryptedKey());

        return encryptedResponse;
    }

    /**
     * 获取方法或类上的 @ApiCrypto 注解
     * 优先级：方法 > 类
     *
     * @param methodParameter 方法参数
     * @return ApiCrypto注解，如果不存在返回null
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
}
