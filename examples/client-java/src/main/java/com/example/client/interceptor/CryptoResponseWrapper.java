package com.example.client.interceptor;

import com.example.client.config.CryptoClientProperties;
import com.example.client.util.ClientCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;

/**
 * 加密响应包装器
 * 用于包装ClientHttpResponse，自动解密加密的响应
 */
public class CryptoResponseWrapper implements HttpInputMessage {

    private static final Logger logger = LoggerFactory.getLogger(CryptoResponseWrapper.class);

    private final HttpInputMessage originalResponse;
    private final CryptoClientProperties properties;
    private String decryptedBody;
    private byte[] bodyBytes;

    public CryptoResponseWrapper(HttpInputMessage originalResponse, CryptoClientProperties properties) {
        this.originalResponse = originalResponse;
        this.properties = properties;

        // 预解密响应体
        try {
            decryptResponse();
        } catch (Exception e) {
            logger.error("响应解密失败", e);
        }
    }

    private void decryptResponse() throws Exception {
        InputStream inputStream = originalResponse.getBody();
        byte[] encryptedBytes = inputStream.readAllBytes();
        String encryptedBody = new String(encryptedBytes, StandardCharsets.UTF_8);

        // 检查是否加密
        if (!isEncryptedResponse(encryptedBody)) {
            this.bodyBytes = encryptedBytes;
            this.decryptedBody = encryptedBody;
            return;
        }

        // 解析加密结构
        // 响应格式: {"code":200,"data":"<encrypted>","key":"<encryptedKey>","iv":"<iv>"}
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(encryptedBody).getAsJsonObject();

        String encryptedData = json.get("data").getAsString();
        String encryptedKey = json.has("key") ? json.get("key").getAsString() : null;
        String iv = json.has("iv") ? json.get("iv").getAsString() : null;

        // 解密数据
        String decrypted;
        if (encryptedKey != null && iv != null) {
            // 混合解密
            decrypted = ClientCryptoUtil.decryptHybrid(
                    encryptedData, encryptedKey, iv, properties.getClientPrivateKey());
        } else {
            // RSA解密
            decrypted = ClientCryptoUtil.decryptRSAOnly(encryptedData, properties.getClientPrivateKey());
        }

        this.bodyBytes = decrypted.getBytes(StandardCharsets.UTF_8);
        this.decryptedBody = decrypted;
    }

    /**
     * 检查响应是否被加密
     */
    private boolean isEncryptedResponse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            return json.has("data") && json.has("key");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream getBody() throws IOException {
        return new java.io.ByteArrayInputStream(bodyBytes);
    }

    @Override
    public org.springframework.http.HttpHeaders getHeaders() {
        return originalResponse.getHeaders();
    }

    /**
     * 获取解密后的响应体
     */
    public String getDecryptedBody() {
        return decryptedBody;
    }

    /**
     * 获取解密后的响应体字节数组
     */
    public byte[] getDecryptedBodyBytes() {
        return bodyBytes;
    }
}
