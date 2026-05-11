package com.ai.extender.crypto.config;

import com.ai.extender.crypto.advice.CryptoRequestBodyAdvice;
import com.ai.extender.crypto.advice.CryptoResponseBodyAdvice;
import com.ai.extender.crypto.aspect.CryptoAspect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密自动配置类
 * Spring Boot自动配置入口
 *
 * 组件说明：
 * 1. CryptoRequestBodyAdvice - 请求体解密 + 安全请求头验证（防重放、防篡改）
 * 2. CryptoResponseBodyAdvice - 响应体加密
 * 3. CryptoAspect - 工具组件，提供安全请求头生成方法
 *
 * 注意：所有功能仅对标注了 @ApiCrypto 注解的接口生效，不会影响其他接口
 *
 * JDK 8兼容版本
 *
 * @author Crypto SDK
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass({CryptoAspect.class, CryptoRequestBodyAdvice.class})
@EnableConfigurationProperties(CryptoProperties.class)
@ConditionalOnProperty(prefix = "crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CryptoAutoConfiguration {

    /**
     * 配置 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * 配置 CryptoAspect
     * 提供安全请求头生成工具方法
     */
    @Bean
    public CryptoAspect cryptoAspect(CryptoProperties cryptoProperties) {
        return new CryptoAspect(cryptoProperties);
    }

    /**
     * 配置 CryptoResponseBodyAdvice
     * 处理响应体加密
     *
     * 使用 ResponseBodyAdvice 替代 AOP 切面处理响应加密，解决：
     * 1. 与统一响应包装类（如 R、Result 等）的类型冲突问题
     * 2. 提高与第三方项目的兼容性
     *
     * @since 1.1.0
     */
    @Bean
    @ConditionalOnWebApplication
    public CryptoResponseBodyAdvice cryptoResponseBodyAdvice(CryptoProperties cryptoProperties,
                                                              ObjectMapper objectMapper) {
        return new CryptoResponseBodyAdvice(cryptoProperties, objectMapper);
    }

    /**
     * 配置 CryptoRequestBodyAdvice
     * 处理请求体解密 + 安全请求头验证
     *
     * @since 1.1.0
     */
    @Bean
    @ConditionalOnWebApplication
    public CryptoRequestBodyAdvice cryptoRequestBodyAdvice(CryptoProperties cryptoProperties,
                                                            ObjectMapper objectMapper) {
        return new CryptoRequestBodyAdvice(cryptoProperties, objectMapper);
    }
}
