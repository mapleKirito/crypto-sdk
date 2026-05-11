package com.example.client.config;

import com.example.client.interceptor.CryptoRequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端加密RestTemplate配置
 *
 * 自动配置：
 * 1. RestTemplate Bean
 * 2. 添加加密拦截器
 * 3. 配置超时等参数
 */
@Configuration
@EnableConfigurationProperties(CryptoClientProperties.class)
public class CryptoRestTemplateConfig {

    private final CryptoClientProperties cryptoClientProperties;

    public CryptoRestTemplateConfig(CryptoClientProperties cryptoClientProperties) {
        this.cryptoClientProperties = cryptoClientProperties;
    }

    /**
     * 配置支持加密的RestTemplate
     * 所有通过此RestTemplate的请求会自动：
     * - 加密请求体
     * - 添加安全请求头（Timestamp、Nonce、Signature）
     * - 解密加密的响应
     */
    @Bean(name = "cryptoRestTemplate")
    public RestTemplate cryptoRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // 配置超时
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setConnectTimeout(cryptoClientProperties.getTimeout());
        ((org.springframework.http.client.SimpleClientHttpRequestFactory) restTemplate.getRequestFactory())
                .setReadTimeout(cryptoClientProperties.getTimeout());

        // 添加加密拦截器
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new CryptoRequestInterceptor(cryptoClientProperties));
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }

    /**
     * 普通RestTemplate（不加密）
     * 用于调用不需要加密的接口
     */
    @Bean(name = "normalRestTemplate")
    public RestTemplate normalRestTemplate() {
        return new RestTemplate();
    }
}
