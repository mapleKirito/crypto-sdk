package com.ai.extender.crypto.annotation;

import java.lang.annotation.*;

/**
 * API加密解密注解
 *
 * 用于标注Controller或方法，自动启用请求解密和响应加密功能
 *
 * 使用方式：
 * - 标注在类上：所有方法默认加解密
 * - 标注在方法上：仅当前方法加解密
 * - 方法级别会覆盖类级别配置
 *
 * 加密模式：统一使用 RSA+AES 混合加密
 *
 * @author Crypto SDK
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiCrypto {

    /**
     * 是否加密请求（解密请求体）
     * @default true
     */
    boolean encryptRequest() default true;

    /**
     * 是否加密响应（加密响应体）
     * @default true
     */
    boolean encryptResponse() default true;

    /**
     * 是否跳过空请求体
     * @default true
     */
    boolean skipEmptyBody() default true;
}
