/**
 * Apifox 专用响应后置脚本（v4）
 *
 * 依赖说明：
 *   - RSA 解密：使用 node-forge（与 pre-request 脚本保持一致）
 *   - AES-GCM 解密：使用 node-forge
 *
 * 使用方法：
 *   1. 在 Apifox 的「后置操作」->「自定义脚本」中粘贴此代码
 *   2. 配置环境变量：CLIENT_PRIVATE_KEY
 *   3. 需要联网（首次执行会从 CDN 加载 node-forge）
 */

// ==================== 配置区域 ====================
var CLIENT_PRIVATE_KEY = pm.environment.get("CLIENT_PRIVATE_KEY");
var ENABLE_CRYPTO = pm.environment.get("ENABLE_CRYPTO") !== "false";

// ==================== 工具函数 ====================

/**
 * 格式化私钥为 PEM 格式
 * 支持 PKCS#1 (RSA PRIVATE KEY) 和 PKCS#8 (PRIVATE KEY) 格式
 */
function formatPrivateKey(key) {
    if (key.indexOf('-----BEGIN') !== -1) {
        return key;
    }
    // 默认使用 PKCS#8 格式（更通用）
    return '-----BEGIN PRIVATE KEY-----\n' + key + '\n-----END PRIVATE KEY-----';
}

/**
 * RSA 私钥解密（使用 node-forge）
 * @param {string} encryptedDataBase64 - Base64 编码的密文
 * @param {string} privateKeyPem - PEM 格式私钥
 * @param {object} forge - forge 对象
 * @returns {string} 解密后的明文（Base64 编码的 AES 密钥）
 */
function rsaDecrypt(encryptedDataBase64, privateKeyPem, forge) {
    var pemStr = formatPrivateKey(privateKeyPem);
    console.log("RSA解密: 私钥PEM前50字符:", pemStr.substring(0, 50));
    
    try {
        // 解析私钥
        var privateKey = forge.pki.privateKeyFromPem(pemStr);
        console.log("RSA解密: 私钥解析成功");
        
        // Base64 解码密文
        var encryptedBytes = forge.util.decode64(encryptedDataBase64);
        
        // 使用私钥解密（PKCS#1 v1.5 填充，与 Java 端一致）
        var decryptedBytes = privateKey.decrypt(encryptedBytes, 'RSAES-PKCS1-V1_5');
        console.log("RSA解密: 解密成功，长度:", decryptedBytes.length);
        
        // 返回 Base64 编码的 AES 密钥
        return forge.util.encode64(decryptedBytes);
    } catch (e) {
        console.error("RSA解密失败:", e.message);
        throw e;
    }
}

/**
 * AES-256-GCM 解密（使用 node-forge）
 *
 * 输入格式与 Java AES/GCM/NoPadding 兼容：
 *   Base64( IV[12字节] + 密文 + GCM_TAG[16字节] )
 *
 * @param {string} encryptedDataBase64 - Base64 编码的 IV+密文+TAG
 * @param {string} keyBase64 - Base64 编码的 32 字节 AES 密钥
 * @param {object} forge - forge 对象
 * @returns {string} 解密后的明文字符串
 */
function aesGcmDecrypt(encryptedDataBase64, keyBase64, forge) {
    // 解码
    var combined = forge.util.decode64(encryptedDataBase64);
    var keyBytes = forge.util.decode64(keyBase64);

    console.log("AES解密: combined长度:", combined.length, "字节");
    console.log("AES解密: keyBytes长度:", keyBytes.length, "字节");

    // 前 12 字节是 IV
    var iv = combined.substring(0, 12);

    // 剩余部分是 密文 + TAG（最后 16 字节是 TAG）
    var ciphertextWithTag = combined.substring(12);
    var tag = ciphertextWithTag.substring(ciphertextWithTag.length - 16);
    var ciphertext = ciphertextWithTag.substring(0, ciphertextWithTag.length - 16);

    console.log("AES解密: IV长度:", iv.length, "密文长度:", ciphertext.length, "TAG长度:", tag.length);

    // 创建 decipher
    var decipher = forge.cipher.createDecipher('AES-GCM', keyBytes);
    // 必须显式设置 tag
    decipher.start({
        iv: iv,
        tagLength: 128,
        tag: tag
    });
    decipher.update(forge.util.createBuffer(ciphertext));
    var success = decipher.finish();

    if (!success) {
        throw new Error('AES-GCM 解密失败：TAG 验证不通过');
    }

    return forge.util.decodeUtf8(decipher.output.getBytes());
}

// ==================== 主逻辑 ====================

function doDecrypt(forge) {
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
        var responseBody = pm.response.json();

        // 检查是否是加密响应
        if (!responseBody.data || !responseBody.key) {
            console.log("响应未加密，跳过解密");
            return;
        }

        console.log("加密响应（已收到）");

        // 1. RSA 解密获取 AES 密钥
        var aesKeyBase64 = rsaDecrypt(responseBody.key, CLIENT_PRIVATE_KEY, forge);
        console.log("AES密钥解密成功，长度:", aesKeyBase64.length);

        // 2. AES-GCM 解密数据
        var decryptedText = aesGcmDecrypt(responseBody.data, aesKeyBase64, forge);

        console.log("解密后的响应:", decryptedText);

        // 3. 设置到环境变量，方便查看
        pm.environment.set("DECRYPTED_RESPONSE", decryptedText);

        // 4. 使用 Visualizer 展示解密后的响应（在响应体的 Visualize 标签页显示）
        var displayContent = decryptedText;
        var isJson = false;
        try {
            var decryptedData = JSON.parse(decryptedText);
            displayContent = JSON.stringify(decryptedData, null, 2);
            isJson = true;
            console.log("解密后的 JSON 数据:");
            console.log(displayContent);
        } catch (e) {
            console.log("解密后的数据（非JSON）:");
            console.log(decryptedText);
        }

        // 可视化模板
        var template = `
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Consolas, Monaco, monospace; padding: 16px; background: #1e1e1e; color: #d4d4d4; }
        .header { color: #4ec9b0; font-size: 14px; margin-bottom: 12px; border-bottom: 1px solid #404040; padding-bottom: 8px; }
        .content { background: #252526; padding: 12px; border-radius: 4px; white-space: pre-wrap; word-break: break-all; }
        .json { color: #ce9178; }
    </style>
</head>
<body>
    <div class="header">🔓 解密后的响应内容</div>
    <div class="content {{type}}">{{content}}</div>
</body>
</html>`;

        pm.visualizer.set(template, {
            content: displayContent,
            type: isJson ? 'json' : ''
        });

        // 5. 测试断言
        pm.test("响应解密成功", function () {
            pm.expect(decryptedText).to.not.be.empty;
        });

    } catch (error) {
        console.error("响应解密失败:", error.message);
        pm.test("响应解密失败: " + error.message, function () {
            pm.expect.fail(error.message);
        });
    }
}

// ==================== 加载依赖并执行 ====================

/**
 * 模拟浏览器环境对象（node-forge UMD 打包需要）
 * 注意：必须使用全局变量 window，确保 forge 能找到 window.crypto
 */
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

/**
 * 加载 node-forge 并返回 forge 对象
 * UMD 格式会根据环境选择挂载位置：window.forge / module.exports
 */
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
            // 临时保存 CommonJS 变量，强制 forge 走浏览器分支
            var _exports = (typeof exports !== 'undefined') ? exports : undefined;
            var _module = (typeof module !== 'undefined') ? module : undefined;
            exports = undefined;
            module = undefined;

            // 执行 forge 代码，它会检测到 window 存在，挂在到 window.forge
            eval(response.text());

            // 恢复 CommonJS 变量
            if (_exports !== undefined) exports = _exports;
            if (_module !== undefined) module = _module;

            // 从 window.forge 获取
            var forgeObj = window.forge;

            if (!forgeObj) {
                throw new Error('forge 对象未正确初始化，window.forge 不存在');
            }

            console.log("node-forge 加载成功");
            callback(forgeObj);
        } catch (e) {
            console.error("执行 node-forge 脚本失败:", e.message);
        }
    });
}

// 执行加载
console.log("正在加载 node-forge（支持 AES-GCM 模式）...");
loadForge(function(forge) {
    doDecrypt(forge);
});
