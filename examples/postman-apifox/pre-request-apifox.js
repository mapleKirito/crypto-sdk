/**
 * Apifox 专用请求前置脚本（v6）
 *
 * 依赖说明：
 *   - 所有加解密操作统一使用 node-forge（通过 pm.sendRequest 从 CDN 加载）
 *     - RSA 加解密：forge.pki.rsa
 *     - AES-GCM 加解密：forge.cipher
 *     - HMAC-SHA256：forge.hmac
 *   - 注意：crypto-js 不支持 GCM 模式，jsrsasign 不支持原始字节 RSA 加密
 *
 * 使用方法：
 *   1. 在 Apifox 的「前置操作」->「自定义脚本」中粘贴此代码
 *   2. 配置环境变量：SERVER_PUBLIC_KEY、SIGNATURE_SECRET
 *   3. 需要联网（首次执行会从 CDN 加载 node-forge）
 *
 * 数据流（与 Java HybridCryptoUtil 完全一致）：
 *   1. 生成 32 字节随机 AES 密钥（原始字节）
 *   2. AES-GCM 加密原始数据 → Base64(IV + 密文 + TAG)
 *   3. RSA 加密 AES 密钥的原始字节 → Base64
 *   4. 签名：HmacSHA256(加密后JSON + "|" + timestamp + "|" + nonce, secret)
 */

// ==================== 配置区域 ====================
var SERVER_PUBLIC_KEY = pm.environment.get("SERVER_PUBLIC_KEY");
var SIGNATURE_SECRET = pm.environment.get("SIGNATURE_SECRET");
var ENABLE_CRYPTO = pm.environment.get("ENABLE_CRYPTO") !== "false";

// ==================== 工具函数 ====================

/**
 * 格式化公钥为 PEM 格式
 */
function formatPublicKey(key) {
    if (key.indexOf('-----BEGIN') !== -1) {
        return key;
    }
    return '-----BEGIN PUBLIC KEY-----\n' + key + '\n-----END PUBLIC KEY-----';
}

/**
 * RSA 公钥加密原始字节（使用 forge）
 * 与 Java RSAUtil.encryptBytesByPublicKey 完全对应：
 *   Java: byte[] → RSA/ECB/PKCS1Padding → byte[]
 *   JS:   binary string → RSAES-PKCS1-V1_5 → binary string → Base64
 *
 * @param {string} dataBytes - 原始字节数据（forge 二进制字符串，每字符代表一个字节）
 * @param {string} publicKeyPem - PEM 格式公钥
 * @returns {string} Base64 编码的密文
 */
function rsaEncryptBytes(dataBytes, publicKeyPem, forge) {
    var pemStr = formatPublicKey(publicKeyPem);
    console.log("RSA加密: 公钥PEM前50字符:", pemStr.substring(0, 50));
    
    try {
        var publicKey = forge.pki.publicKeyFromPem(pemStr);
        console.log("RSA加密: 公钥解析成功");
        
        // 使用公钥对象的 encrypt 方法
        var encrypted = publicKey.encrypt(dataBytes, 'RSAES-PKCS1-V1_5');
        console.log("RSA加密: 加密成功");
        
        return forge.util.encode64(encrypted);
    } catch (e) {
        console.error("RSA加密失败:", e.message);
        throw e;
    }
}

/**
 * AES-256-GCM 加密（使用 forge）
 *
 * 输出格式与 Java AESUtil.encryptWithIV 兼容：
 *   Base64( IV[12字节] + 密文 + GCM_TAG[16字节] )
 *
 * @param {string} plaintext - 明文字符串
 * @param {string} keyBytes - 原始 32 字节 AES 密钥（forge 二进制字符串）
 * @returns {string} Base64 编码的 IV+密文+TAG
 */
function aesGcmEncrypt(plaintext, keyBytes, forge) {
    var iv = forge.random.getBytesSync(12);

    var cipher = forge.cipher.createCipher('AES-GCM', keyBytes);
    cipher.start({ iv: iv, tagLength: 128 });
    cipher.update(forge.util.createBuffer(forge.util.encodeUtf8(plaintext)));
    cipher.finish();

    var encrypted = cipher.output.getBytes();
    var tag = cipher.mode.tag.getBytes();
    var combined = iv + encrypted + tag;

    return forge.util.encode64(combined);
}

/**
 * HMAC-SHA256 签名（与 Java SignatureUtil.sign 保持一致）
 */
function hmacSHA256(message, secret, forge) {
    var hmac = forge.hmac.create();
    hmac.start('SHA256', secret);
    hmac.update(message);
    return forge.util.encode64(hmac.digest().getBytes());
}

/**
 * 生成 16 字节随机 nonce，返回 Base64 编码
 * 混合 timestamp + Math.random() 确保每次请求都不同
 */
function generateNonce(forge) {
    var timestamp = Date.now();
    var randomBytes = '';
    for (var i = 7; i >= 0; i--) {
        randomBytes += String.fromCharCode((timestamp >> (i * 8)) & 0xFF);
    }
    for (var i = 0; i < 8; i++) {
        randomBytes += String.fromCharCode(Math.floor(Math.random() * 256));
    }
    return forge.util.encode64(randomBytes);
}

// ==================== 主逻辑 ====================

function doEncrypt(forge) {
    if (!ENABLE_CRYPTO) {
        console.log("加密已禁用，跳过加密");
        return;
    }

    if (!SERVER_PUBLIC_KEY) {
        console.log("未配置 SERVER_PUBLIC_KEY，跳过加密");
        return;
    }

    try {
        // 1. 获取原始请求体
        var originalBody = pm.request.body ? pm.request.body.raw : '{}';
        if (typeof originalBody === 'object') {
            originalBody = JSON.stringify(originalBody);
        }
        console.log("原始请求体:", originalBody);

        // 2. 生成安全请求头
        var timestamp = Date.now().toString();
        var nonce = generateNonce(forge);

        // 3. 生成 32 字节随机 AES 密钥（原始字节）
        var aesKeyBytes = forge.random.getBytesSync(32);

        // 4. AES-GCM 加密数据
        var encryptedDataBase64 = aesGcmEncrypt(originalBody, aesKeyBytes, forge);

        // 5. RSA 加密 AES 密钥（加密原始 32 字节，与 Java encryptBytesByPublicKey 对应）
        var encryptedKey = rsaEncryptBytes(aesKeyBytes, SERVER_PUBLIC_KEY, forge);

        // 6. 构建加密后的请求体
        var encryptedBody = {
            encryptedData: encryptedDataBase64,
            encryptedKey: encryptedKey
        };
        var encryptedJsonBody = JSON.stringify(encryptedBody);

        // 7. 生成签名（对加密后的 JSON 签名）
        var signature = '';
        if (SIGNATURE_SECRET) {
            var dataToSign = encryptedJsonBody + "|" + timestamp + "|" + nonce;
            signature = hmacSHA256(dataToSign, SIGNATURE_SECRET, forge);
        }

        // 8. 更新请求体
        pm.request.body.update(encryptedJsonBody);

        // 9. 添加安全请求头
        pm.request.headers.add({ key: 'X-Timestamp', value: timestamp });
        pm.request.headers.add({ key: 'X-Nonce', value: nonce });
        pm.request.headers.add({ key: 'X-Signature', value: signature });
        pm.request.headers.add({ key: 'X-Encrypted', value: 'true' });
        pm.request.headers.add({ key: 'Content-Type', value: 'application/json' });

        console.log("请求加密成功");

    } catch (error) {
        console.error("请求加密失败:", error.message);
    }
}

// ==================== 加载依赖并执行 ====================

if (typeof window === 'undefined') {
    window = {
        crypto: {
            getRandomValues: function(arr) {
                for (var i = 0; i < arr.length; i++) {
                    arr[i] = Math.floor(Math.random() * 256);
                }
                return arr;
            }
        }
    };
}

function loadForge(callback) {
    if (typeof forge !== 'undefined') {
        callback(forge);
        return;
    }

    pm.sendRequest({
        url: "https://cdn.jsdelivr.net/npm/node-forge@1.3.1/dist/forge.min.js",
        method: "GET"
    }, function (err, response) {
        if (err) {
            console.error("加载 node-forge 失败:", err);
            return;
        }
        try {
            var _exports = (typeof exports !== 'undefined') ? exports : undefined;
            var _module = (typeof module !== 'undefined') ? module : undefined;
            exports = undefined;
            module = undefined;

            eval(response.text());

            if (_exports !== undefined) exports = _exports;
            if (_module !== undefined) module = _module;

            var forgeObj = window.forge;
            if (!forgeObj) {
                throw new Error('forge 对象未正确初始化');
            }

            console.log("node-forge 加载成功");
            callback(forgeObj);
        } catch (e) {
            console.error("执行 node-forge 脚本失败:", e.message);
        }
    });
}

console.log("正在加载 node-forge（支持 AES-GCM 模式）...");
loadForge(function(forge) {
    doEncrypt(forge);
});
