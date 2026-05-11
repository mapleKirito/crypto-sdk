package com.ai.extender.crypto.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * 签名工具类
 * 提供HMAC-SHA256签名和验证功能
 * 用于防篡改校验
 * JDK 8兼容版本
 */
public class SignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String MD5_ALGORITHM = "MD5";

    /**
     * 生成HMAC-SHA256签名
     *
     * @param data      待签名数据
     * @param secretKey 密钥
     * @return Base64编码的签名
     */
    public static String sign(String data, String secretKey) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes("UTF-8"), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] signBytes = mac.doFinal(data.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(signBytes);
    }

    /**
     * 验证HMAC-SHA256签名
     *
     * @param data      原始数据
     * @param signature 待验证的签名
     * @param secretKey 密钥
     * @return 验证结果
     */
    public static boolean verify(String data, String signature, String secretKey) throws Exception {
        String computedSignature = sign(data, secretKey);
        return computedSignature.equals(signature);
    }

    /**
     * 构建待签名字符串（对参数按key排序后拼接）
     *
     * @param params 参数Map
     * @return 待签名字符串
     */
    public static String buildSignString(Map<String, Object> params) {
        // 使用TreeMap按key排序
        TreeMap<String, Object> sortedParams = new TreeMap<>();
        if (params != null) {
            sortedParams.putAll(params);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // 跳过空值和签名字段
            if (value != null && !"signature".equals(key) && !"sign".equals(key)) {
                sb.append(key).append("=").append(value).append("&");
            }
        }

        // 移除最后一个&
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * 生成请求签名（包含时间戳和nonce防重放）
     *
     * @param params    业务参数
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @return 签名
     */
    public static String signRequest(Map<String, Object> params, long timestamp, String nonce, String secretKey) throws Exception {
        String signString = buildSignString(params);
        String dataToSign = signString + "|" + timestamp + "|" + nonce;
        return sign(dataToSign, secretKey);
    }

    /**
     * 验证请求签名
     *
     * @param params    业务参数
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param signature 待验证的签名
     * @param secretKey 密钥
     * @return 验证结果
     */
    public static boolean verifyRequest(Map<String, Object> params, long timestamp, String nonce, String signature, String secretKey) throws Exception {
        String computedSignature = signRequest(params, timestamp, nonce, secretKey);
        return computedSignature.equals(signature);
    }

    /**
     * 生成MD5摘要（用于简单校验）
     *
     * @param data 原始数据
     * @return MD5摘要（小写）
     */
    public static String md5(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance(MD5_ALGORITHM);
        byte[] digest = md.digest(data.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 生成请求体的签名
     *
     * @param body      请求体JSON字符串
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @return 签名
     */
    public static String signBody(String body, long timestamp, String nonce, String secretKey) throws Exception {
        String dataToSign = body + "|" + timestamp + "|" + nonce;
        return sign(dataToSign, secretKey);
    }

    /**
     * 验证请求体签名
     *
     * @param body      请求体JSON字符串
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param signature 待验证的签名
     * @param secretKey 密钥
     * @return 验证结果
     */
    public static boolean verifyBody(String body, long timestamp, String nonce, String signature, String secretKey) throws Exception {
        String computedSignature = signBody(body, timestamp, nonce, secretKey);
        return computedSignature.equals(signature);
    }
}
