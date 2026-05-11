# Java后端调用方示例

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.ai.extender</groupId>
    <artifactId>crypto-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 2. 配置密钥

```yaml
crypto-client:
  enabled: true
  
  # 公钥：用于加密请求（服务端给的）
  key:
    public-key: |
      MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
  
  # 私钥：用于解密响应（自己的）
    private-key: |
      MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ...
  
  # 签名密钥（与服务端共享）
  signature:
    secret: your-32-char-signature-secret-key
    enabled: true
```

### 3. 配置 RestTemplate

```java
@Configuration
@EnableConfigurationProperties(CryptoClientProperties.class)
public class CryptoConfig {

    @Bean(name = "cryptoRestTemplate")
    public RestTemplate cryptoRestTemplate(CryptoClientProperties props) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new CryptoRequestInterceptor(props));
        return restTemplate;
    }
}
```

### 4. 调用加密接口

```java
@Service
public class OrderService {

    @Autowired
    @Qualifier("cryptoRestTemplate")
    private RestTemplate cryptoRestTemplate;

    public Order createOrder(OrderRequest request) {
        // 请求自动加密，响应自动解密
        return cryptoRestTemplate.postForObject(
            "http://server:8080/api/order/create",
            request,
            Order.class
        );
    }
}
```

## 功能说明

| 功能 | 说明 |
|------|------|
| 自动加密请求 | 使用配置的公钥加密 |
| 自动解密响应 | 使用配置的私钥解密 |
| 自动添加安全头 | X-Timestamp、X-Nonce、X-Signature |
| 防重放攻击 | 每次请求生成唯一 Nonce |
| 防篡改 | HMAC-SHA256 签名验证 |

## 密钥配置

### 共用一套密钥

客户端和服务端使用同一对密钥：
- `public-key`：同一对密钥的公钥
- `private-key`：同一对密钥的私钥

### 各自密钥

- `public-key`：服务端的公钥（用于加密请求）
- `private-key`：客户端自己的私钥（用于解密响应）
