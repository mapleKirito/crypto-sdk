# Crypto SDK - API加解密SDK

## 项目简介

一套完整的API接口加解密解决方案，专为JDK 8业务系统设计，支持零侵入式集成。

### 核心特性

- **JDK 8完全兼容**
- **零侵入**：只需添加 `@ApiCrypto` 注解即可启用加解密
- **RSA + AES 混合加密**：安全高效
- **防重放攻击**：时间戳 + Nonce 验证
- **防篡改**：HMAC-SHA256 签名验证
- **Spring Boot 自动配置**
- **高兼容性**：支持统一响应包装类（R、Result等）

---

## 快速开始

### 1. 安装到本地Maven仓库

```bash
cd crypto-sdk
mvn clean install
```

### 2. 业务系统添加依赖

```xml
<dependency>
    <groupId>com.ai.extender</groupId>
    <artifactId>crypto-sdk</artifactId>
    <version>1.1.1</version>
</dependency>
```

### 3. 生成密钥

```bash
# Windows
scripts\generate-keys.bat

# Linux/Mac
chmod +x scripts/generate-keys.sh
./scripts/generate-keys.sh

# Python（推荐，跨平台）
pip install cryptography
python scripts/generate-keys.py
```

### 4. 配置密钥

```yaml
crypto:
  enabled: true

  key:
    # 公钥：用于加密数据（Base64编码，单行）
    public-key: MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
    # 私钥：用于解密数据（Base64编码，单行）
    private-key: MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ...
    auto-generate: false

  signature:
    enabled: true
    secret: <HMAC签名密钥>
    timestamp-validity: 300000
```

> **注意**：密钥建议使用单行写法，避免 `|` 多行写法带来的换行符问题。

### 5. 使用注解

```java
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @PostMapping("/create")
    @ApiCrypto  // 自动加解密
    public R<Order> createOrder(@RequestBody OrderRequest request) {
        return R.ok(orderService.create(request));
    }
}
```

---

## 配置说明

### 完整配置项

```yaml
crypto:
  # 全局开关
  enabled: true

  # RSA密钥配置
  # 使用原则：公钥加密，私钥解密
  key:
    public-key: <Base64编码的公钥>
    private-key: <Base64编码的私钥>
    auto-generate: false  # 生产环境必须false

  # 签名配置（防重放、防篡改）
  signature:
    enabled: true
    secret: <至少32字符的密钥>
    timestamp-validity: 300000  # 5分钟
```

### 密钥配置灵活性

| 配置方式 | 说明 | 适用场景 |
|----------|------|----------|
| **共用一套密钥** | 公钥和私钥是一对 | 内网通信、可信环境 |
| **各自密钥** | 私钥是自己的，公钥是对方的 | 高安全要求、需要身份认证 |

---

## 注解说明

### @ApiCrypto

```java
@ApiCrypto(
    encryptRequest = true,   // 解密请求（默认true）
    encryptResponse = true   // 加密响应（默认true）
)
```

### 组合效果

| encryptRequest | encryptResponse | 效果 |
|----------------|-----------------|------|
| `true` | `true` | 请求解密 + 响应加密 + 安全验证 ✅ |
| `true` | `false` | 请求解密 + 响应明文 + 安全验证 |
| `false` | `true` | 请求明文 + 响应加密 + 安全验证 |
| `false` | `false` | 请求明文 + 响应明文 + 安全验证 |

> **注意**：只要加了 `@ApiCrypto` 注解，安全验证（防重放、防篡改）始终生效。

---

## 加密原理

### RSA+AES 混合加密

```
加密流程：
1. 生成随机 AES 密钥（256位）
2. 生成随机 IV（96位）
3. AES-GCM 加密数据
4. RSA 公钥加密 AES 密钥
5. 返回：IV + 密文 + 加密的AES密钥

解密流程：
1. RSA 私钥解密得到 AES 密钥
2. 分离 IV 和密文
3. AES-GCM 解密数据
```

### IV 的作用

IV（初始化向量）让相同的明文每次加密产生不同的密文，防止攻击者通过分析密文模式推断明文。

---

## 安全机制

### 防重放攻击

请求头必须包含：
- `X-Timestamp`：时间戳（毫秒）
- `X-Nonce`：随机字符串

服务端验证：
- 时间戳有效期：默认5分钟
- Nonce 缓存：防止同一请求重复使用

### 防篡改

请求头包含：
- `X-Signature`：HMAC-SHA256 签名

签名内容：`请求体|时间戳|Nonce`

---

## 项目结构

```
crypto-sdk/
├── pom.xml
├── README.md
├── scripts/
│   ├── generate-keys.py    # Python密钥生成工具（推荐）
│   ├── generate-keys.bat   # Windows密钥生成工具
│   ├── generate-keys.sh    # Linux/Mac密钥生成工具
│   ├── install-local.bat   # Windows安装脚本
│   └── install-local.sh    # Linux/Mac安装脚本
├── src/main/java/com/ai/extender/crypto/
│   ├── annotation/
│   │   ├── ApiCrypto.java
│   │   └── CryptoMode.java
│   ├── advice/
│   │   ├── CryptoRequestBodyAdvice.java   # 请求解密+安全验证
│   │   └── CryptoResponseBodyAdvice.java  # 响应加密
│   ├── aspect/
│   │   └── CryptoAspect.java              # 工具方法
│   ├── config/
│   │   ├── CryptoProperties.java
│   │   └── CryptoAutoConfiguration.java
│   └── util/
│       ├── AESUtil.java
│       ├── RSAUtil.java
│       ├── HybridCryptoUtil.java
│       └── SignatureUtil.java
├── src/main/resources/
│   ├── META-INF/spring.factories
│   └── application-crypto-example.yml
└── examples/
    ├── client-java/         # Java后端调用方示例
    └── client-vue/          # Vue前端调用方示例
```

---

## 调用方集成

### Java后端调用方

```java
// 配置 RestTemplate
@Bean
public RestTemplate cryptoRestTemplate() {
    RestTemplate rt = new RestTemplate();
    rt.getInterceptors().add(new CryptoRequestInterceptor(props));
    return rt;
}

// 调用加密接口
Order result = cryptoRestTemplate.postForObject(url, request, Order.class);
```

### Vue前端调用方

```typescript
// 安装依赖
npm install jsencrypt@3.0.0-beta.1 crypto-js

// 配置 Axios
CryptoAxiosPlugin.install(axios, {
  serverPublicKey: '...',
  clientPrivateKey: '...',
  signatureSecret: '...'
})

// 调用加密接口
const result = await cryptoAxios.post('/api/order/create', data)
```

---

## 兼容性说明

### v1.1.0 解决的问题

第三方项目使用统一响应包装类（如 `R<T>`、`Result<T>`）时，原AOP切面会导致类型转换异常。

### 解决方案

使用 `ResponseBodyAdvice` 和 `RequestBodyAdvice` 替代 AOP 切面：

| 组件 | 职责 |
|------|------|
| `CryptoRequestBodyAdvice` | 请求体解密 + 安全验证 |
| `CryptoResponseBodyAdvice` | 响应体加密 |

**优势**：在消息转换器层面介入，不改变返回类型，完美兼容统一响应包装类。

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.1.1 | 2026-05-11 | 修复异常类型：将安全验证异常改为 IllegalArgumentException，由业务系统全局异常处理器统一处理 |
| 1.1.0 | 2026-05-09 | 引入 ResponseBodyAdvice 解决兼容性问题；简化加密模式为 RSA+AES 混合；新增调用方示例 |
| 1.0.0 | 2026-05-08 | 初始版本 |

---

## 依赖说明

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

**最后更新**: 2026-05-09
