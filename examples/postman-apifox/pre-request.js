/**
 * Postman / Apifox 请求前置脚本
 * 功能：自动加密请求体
 * 
 * 使用方法：
 * 1. 在 Postman 的 Pre-request Script 中粘贴此代码
 * 2. 在 Apifox 的「前置操作」->「自定义脚本」中粘贴此代码
 * 3. 配置环境变量：SERVER_PUBLIC_KEY、SIGNATURE_SECRET
 */

// ==================== 配置区域 ====================
const SERVER_PUBLIC_KEY = pm.environment.get("SERVER_PUBLIC_KEY");
const SIGNATURE_SECRET = pm.environment.get("SIGNATURE_SECRET");
const ENABLE_CRYPTO = pm.environment.get("ENABLE_CRYPTO") !== "false";

// ==================== JSEncrypt 库加载 ====================

/**
 * 动态加载 JSEncrypt 库
 * 支持 Postman 和 Apifox 环境
 */
async function loadJSEncrypt() {
    // 检查是否已加载
    if (typeof JSEncrypt !== 'undefined') {
        return true;
    }
    
    // JSEncrypt CDN 地址
    const jsencryptUrl = 'https://cdn.jsdelivr.net/npm/jsencrypt@3.3.2/bin/jsencrypt.min.js';
    
    return new Promise((resolve, reject) => {
        // 使用 pm.sendRequest 加载脚本
        pm.sendRequest({
            url: jsencryptUrl,
            method: 'GET'
        }, function (err, response) {
            if (err) {
                console.error('加载 JSEncrypt 失败:', err);
                reject(err);
                return;
            }
            
            try {
                // 执行加载的脚本
                const scriptContent = response.text();
                
                // 在全局环境中执行
                if (typeof eval !== 'undefined') {
                    eval(scriptContent);
                } else if (typeof Function !== 'undefined') {
                    new Function(scriptContent)();
                }
                
                // 验证是否加载成功
                if (typeof JSEncrypt !== 'undefined') {
                    console.log('JSEncrypt 库加载成功');
                    resolve(true);
                } else {
                    reject(new Error('JSEncrypt 加载后未定义'));
                }
            } catch (e) {
                console.error('执行 JSEncrypt 脚本失败:', e);
                reject(e);
            }
        });
    });
}

// ==================== 加密工具类 ====================

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
    return bytes.buffer;
}

/**
 * 生成随机字节
 */
function randomBytes(length) {
    const array = new Uint8Array(length);
    for (let i = 0; i < length; i++) {
        array[i] = Math.floor(Math.random() * 256);
    }
    return array;
}

/**
 * 生成 UUID
 */
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

/**
 * HMAC-SHA256 签名
 */
async function hmacSHA256(message, secret) {
    const encoder = new TextEncoder();
    const keyData = encoder.encode(secret);
    const messageData = encoder.encode(message);
    
    const cryptoKey = await crypto.subtle.importKey(
        'raw',
        keyData,
        { name: 'HMAC', hash: 'SHA-256' },
        false,
        ['sign']
    );
    
    const signature = await crypto.subtle.sign('HMAC', cryptoKey, messageData);
    return base64Encode(signature);
}

/**
 * AES-GCM 加密
 */
async function aesEncrypt(plaintext, key, iv) {
    const encoder = new TextEncoder();
    const data = encoder.encode(plaintext);
    
    const cryptoKey = await crypto.subtle.importKey(
        'raw',
        key,
        { name: 'AES-GCM' },
        false,
        ['encrypt']
    );
    
    const encrypted = await crypto.subtle.encrypt(
        { name: 'AES-GCM', iv: iv },
        cryptoKey,
        data
    );
    
    return new Uint8Array(encrypted);
}

/**
 * RSA 加密（使用 JSEncrypt 库）
 */
function rsaEncrypt(data, publicKey) {
    if (typeof JSEncrypt === 'undefined') {
        throw new Error('JSEncrypt library not found');
    }
    
    const encrypt = new JSEncrypt();
    encrypt.setPublicKey(publicKey);
    const result = encrypt.encrypt(data);
    
    if (!result) {
        throw new Error('RSA encryption failed');
    }
    
    return result;
}

/**
 * 组合 Uint8Array
 */
function concatArrays(arrays) {
    let totalLength = 0;
    for (const arr of arrays) {
        totalLength += arr.length;
    }
    
    const result = new Uint8Array(totalLength);
    let offset = 0;
    for (const arr of arrays) {
        result.set(arr, offset);
        offset += arr.length;
    }
    
    return result;
}

// ==================== 主逻辑 ====================

async function encryptRequest() {
    if (!ENABLE_CRYPTO) {
        console.log("加密已禁用，跳过加密");
        return;
    }
    
    if (!SERVER_PUBLIC_KEY) {
        console.log("未配置 SERVER_PUBLIC_KEY，跳过加密");
        return;
    }
    
    try {
        // 1. 加载 JSEncrypt 库
        console.log("正在加载 JSEncrypt 库...");
        await loadJSEncrypt();
        console.log("JSEncrypt 库加载完成");
        
        // 2. 获取原始请求体
        let requestBody = pm.request.body?.raw || '{}';
        
        // 如果请求体是对象，转换为 JSON 字符串
        if (typeof requestBody === 'object') {
            requestBody = JSON.stringify(requestBody);
        }
        
        console.log("原始请求体:", requestBody);
        
        // 3. 生成安全请求头（防重放）
        const timestamp = Date.now().toString();
        const nonce = generateUUID().replace(/-/g, '');
        
        // 4. 生成 AES 密钥（32字节 = 256位）
        const aesKey = randomBytes(32);
        
        // 5. 生成 IV（12字节 = 96位）
        const iv = randomBytes(12);
        
        // 6. AES 加密数据
        const encryptedData = await aesEncrypt(requestBody, aesKey, iv);
        
        // 7. 组合 IV 和密文
        const combined = concatArrays([iv, encryptedData]);
        const encryptedDataBase64 = base64Encode(combined);
        
        // 8. RSA 加密 AES 密钥
        const aesKeyBase64 = base64Encode(aesKey);
        const encryptedKey = rsaEncrypt(aesKeyBase64, SERVER_PUBLIC_KEY);
        
        // 9. 生成签名
        let signature = '';
        if (SIGNATURE_SECRET) {
            const signContent = requestBody + "|" + timestamp + "|" + nonce;
            signature = await hmacSHA256(signContent, SIGNATURE_SECRET);
        }
        
        // 10. 构建加密后的请求体
        const encryptedBody = {
            data: encryptedDataBase64,
            key: encryptedKey
        };
        
        // 11. 更新请求
        pm.request.body.update(JSON.stringify(encryptedBody));
        
        // 12. 添加安全请求头
        pm.request.headers.add({ key: 'X-Timestamp', value: timestamp });
        pm.request.headers.add({ key: 'X-Nonce', value: nonce });
        pm.request.headers.add({ key: 'X-Signature', value: signature });
        pm.request.headers.add({ key: 'X-Encrypted', value: 'true' });
        pm.request.headers.add({ key: 'Content-Type', value: 'application/json' });
        
        console.log("请求加密成功");
        console.log("加密后请求体:", JSON.stringify(encryptedBody));
        
    } catch (error) {
        console.error("请求加密失败:", error);
        console.error("错误详情:", error.message);
        console.error("错误堆栈:", error.stack);
    }
}

// 执行加密
encryptRequest();
