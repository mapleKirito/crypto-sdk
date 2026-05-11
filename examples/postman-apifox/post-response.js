/**
 * Postman / Apifox 响应后置脚本
 * 功能：自动解密响应体
 * 
 * 使用方法：
 * 1. 在 Postman 的 Tests 中粘贴此代码
 * 2. 在 Apifox 的「后置操作」->「自定义脚本」中粘贴此代码
 * 3. 配置环境变量：CLIENT_PRIVATE_KEY
 */

// ==================== 配置区域 ====================
const CLIENT_PRIVATE_KEY = pm.environment.get("CLIENT_PRIVATE_KEY");
const ENABLE_CRYPTO = pm.environment.get("ENABLE_CRYPTO") !== "false";

// ==================== 解密工具类 ====================

/**
 * Base64 编码
 */
function base64Encode(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

/**
 * Base64 解码
 */
function base64Decode(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}

/**
 * RSA 解密（使用 JSEncrypt 库）
 * 注意：Postman/Apifox 需要额外引入 JSEncrypt
 */
function rsaDecrypt(encryptedData, privateKey) {
    // 如果环境中有 JSEncrypt 库
    if (typeof JSEncrypt !== 'undefined') {
        const decrypt = new JSEncrypt();
        decrypt.setPrivateKey(privateKey);
        return decrypt.decrypt(encryptedData);
    }
    
    // 如果没有 JSEncrypt，提示用户
    throw new Error('JSEncrypt library not found. Please import JSEncrypt in your environment.');
}

/**
 * AES-GCM 解密
 */
async function aesDecrypt(encryptedData, key, iv) {
    const cryptoKey = await crypto.subtle.importKey(
        'raw',
        key,
        { name: 'AES-GCM' },
        false,
        ['decrypt']
    );
    
    const decrypted = await crypto.subtle.decrypt(
        { name: 'AES-GCM', iv: iv },
        cryptoKey,
        encryptedData
    );
    
    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
}

// ==================== 主逻辑 ====================

async function decryptResponse() {
    if (!ENABLE_CRYPTO) {
        console.log("解密已禁用，跳过解密");
        return;
    }
    
    if (!CLIENT_PRIVATE_KEY) {
        console.log("未配置 CLIENT_PRIVATE_KEY，跳过解密");
        return;
    }
    
    try {
        // 获取响应体
        const responseBody = pm.response.json();
        
        // 检查是否是加密响应
        if (!responseBody.data || !responseBody.key) {
            console.log("响应未加密，跳过解密");
            return;
        }
        
        console.log("加密响应:", JSON.stringify(responseBody));
        
        // 1. RSA 解密获取 AES 密钥
        const encryptedKey = responseBody.key;
        const aesKeyBase64 = rsaDecrypt(encryptedKey, CLIENT_PRIVATE_KEY);
        
        if (!aesKeyBase64) {
            throw new Error("RSA 解密失败");
        }
        
        const aesKey = base64Decode(aesKeyBase64);
        
        // 2. 解析加密数据（分离 IV 和密文）
        const encryptedData = base64Decode(responseBody.data);
        const iv = encryptedData.slice(0, 12);
        const cipherText = encryptedData.slice(12);
        
        // 3. AES 解密
        const decryptedText = await aesDecrypt(cipherText, aesKey, iv);
        
        console.log("解密后的响应:", decryptedText);
        
        // 4. 将解密后的数据设置到环境变量中，方便查看
        const decryptedData = JSON.parse(decryptedText);
        pm.environment.set("DECRYPTED_RESPONSE", decryptedText);
        
        // 5. 在 Postman 控制台打印格式化后的数据
        console.log("解密后的 JSON 数据:");
        console.log(JSON.stringify(decryptedData, null, 2));
        
        // 6. 设置测试断言（可选）
        pm.test("响应解密成功", function () {
            pm.expect(decryptedData).to.be.an('object');
        });
        
    } catch (error) {
        console.error("响应解密失败:", error);
        pm.test("响应解密失败: " + error.message, function () {
            pm.expect.fail(error.message);
        });
    }
}

// 执行解密
decryptResponse();
