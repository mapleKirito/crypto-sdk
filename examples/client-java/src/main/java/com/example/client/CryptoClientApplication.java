package com.example.client;

import com.example.client.config.CryptoClientProperties;
import com.example.client.util.ClientCryptoUtil;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Java客户端示例启动类
 *
 * 演示如何使用加密客户端调用加密接口
 */
@SpringBootApplication
@EnableConfigurationProperties(CryptoClientProperties.class)
public class CryptoClientApplication {

    @Autowired
    private CryptoClientProperties cryptoProperties;

    public static void main(String[] args) {
        SpringApplication.run(CryptoClientApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        // 手动创建带加密拦截器的RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(
                new com.example.client.interceptor.CryptoRequestInterceptor(cryptoProperties));
        return restTemplate;
    }

    /**
     * 示例：使用加密客户端
     */
    @Bean
    public CommandLineRunner demoRunner(RestTemplate restTemplate) {
        return args -> {
            System.out.println("==============================================");
            System.out.println("Crypto SDK Client Java Example");
            System.out.println("==============================================");

            // 示例1：生成密钥对
            System.out.println("\n[1] 生成客户端密钥对:");
            try {
                String[] keyPair = ClientCryptoUtil.generateRSAKeyPair();
                System.out.println("  客户端公钥: " + keyPair[0].substring(0, 50) + "...");
                System.out.println("  客户端私钥: " + keyPair[1].substring(0, 50) + "...");
            } catch (Exception e) {
                System.out.println("  生成失败: " + e.getMessage());
            }

            // 示例2：加密数据
            System.out.println("\n[2] 加密请求数据:");
            String testData = "{\"orderId\":\"123456\",\"amount\":100.00}";
            System.out.println("  原始数据: " + testData);
            try {
                ClientCryptoUtil.EncryptResult result =
                        ClientCryptoUtil.encryptHybrid(testData, cryptoProperties.getServerPublicKey());
                System.out.println("  加密模式: " + result.getMode());
                System.out.println("  加密数据: " + result.getEncryptedData().substring(0, 50) + "...");
                System.out.println("  加密密钥: " + result.getEncryptedKey().substring(0, 50) + "...");
            } catch (Exception e) {
                System.out.println("  加密失败: " + e.getMessage());
            }

            // 示例3：生成签名
            System.out.println("\n[3] 生成请求签名:");
            try {
                String[] headers = ClientCryptoUtil.generateSecurityHeaders();
                String timestamp = headers[0];
                String nonce = headers[1];
                String signature = ClientCryptoUtil.generateSignature(
                        testData, Long.parseLong(timestamp), nonce, cryptoProperties.getSignatureSecret());
                System.out.println("  时间戳: " + timestamp);
                System.out.println("  Nonce: " + nonce);
                System.out.println("  签名: " + signature.substring(0, 50) + "...");
            } catch (Exception e) {
                System.out.println("  生成失败: " + e.getMessage());
            }

            // 示例4：调用加密接口（需要服务端运行）
            System.out.println("\n[4] 调用加密接口:");
            System.out.println("  (需要服务端运行才能实际调用)");
            System.out.println("  URL: http://localhost:8080/api/order/create");

            System.out.println("\n==============================================");
            System.out.println("配置检查:");
            System.out.println("  加密启用: " + cryptoProperties.isEnabled());
            System.out.println("  签名启用: " + cryptoProperties.isSignatureEnabled());
            System.out.println("  服务端公钥已配置: " +
                    (cryptoProperties.getServerPublicKey() != null && !cryptoProperties.getServerPublicKey().isEmpty()));
            System.out.println("  客户端私钥已配置: " +
                    (cryptoProperties.getClientPrivateKey() != null && !cryptoProperties.getClientPrivateKey().isEmpty()));
            System.out.println("==============================================");
        };
    }
}
