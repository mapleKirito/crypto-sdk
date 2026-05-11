@echo off
REM Crypto SDK - 密钥生成工具 (Windows)
REM 生成 RSA 密钥对和 HMAC 签名密钥

setlocal enabledelayedexpansion

echo ========================================
echo Crypto SDK - 密钥生成工具 (Windows)
echo ========================================
echo.

REM 检查 Python 是否可用
where python >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 使用 Python 生成密钥...
    echo.
    python "%~dp0generate-keys.py"
    goto :end
)

REM 检查 Python3 是否可用
where python3 >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 使用 Python3 生成密钥...
    echo.
    python3 "%~dp0generate-keys.py"
    goto :end
)

REM 使用 PowerShell 生成
echo Python 未找到，使用 PowerShell 生成...
echo.

echo ========================================
echo 正在生成密钥...
echo ========================================
echo.

REM 生成 HMAC 签名密钥
for /f "delims=" %%i in ('powershell -NoProfile -Command "[Convert]::ToBase64String((1..32 ^| ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])"') do set HMAC_SECRET=%%i

echo ========================================
echo 生成的密钥
echo ========================================
echo.
echo 【1】HMAC 签名密钥（用于防篡改）：
echo ----------------------------------------
echo %HMAC_SECRET%
echo.

REM 生成 RSA 密钥对
echo 【2】RSA 密钥对：
echo ----------------------------------------

REM 检查 OpenSSL 是否可用
where openssl >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 使用 OpenSSL 生成 RSA 密钥对...
    echo.
    
    REM 生成私钥
    openssl genrsa -out temp_private.pem 2048 2>nul
    
    REM 导出公钥
    openssl rsa -in temp_private.pem -pubout -out temp_public.pem 2>nul
    
    REM 转换为 Base64
    for /f "delims=" %%i in ('powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('temp_private.pem'))"') do set PRIVATE_KEY=%%i
    for /f "delims=" %%i in ('powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('temp_public.pem'))"') do set PUBLIC_KEY=%%i
    
    REM 清理临时文件
    del temp_private.pem temp_public.pem 2>nul
    
    echo RSA 公钥（前80字符）：
    echo !PUBLIC_KEY:~0,80!...
    echo.
    echo RSA 私钥（前80字符）：
    echo !PRIVATE_KEY:~0,80!...
    echo.
) else (
    echo OpenSSL 未找到，使用在线工具生成 RSA 密钥对：
    echo https://www.allkeysgenerator.com/
    echo.
    set PUBLIC_KEY=<RSA公钥>
    set PRIVATE_KEY=<RSA私钥>
)

echo ========================================
echo application.yml 配置模板
echo ========================================
echo.
echo crypto:
echo   enabled: true
echo.
echo   key:
echo     public-key: ^|
echo       %PUBLIC_KEY%
echo     private-key: ^|
echo       %PRIVATE_KEY%
echo     auto-generate: false
echo.
echo   signature:
echo     enabled: true
echo     secret: %HMAC_SECRET%
echo     timestamp-validity: 300000
echo.

:end
echo ========================================
echo 使用说明
echo ========================================
echo.
echo 1. 将上述配置复制到 application.yml
echo 2. 公钥加密，私钥解密
echo 3. HMAC 密钥双方共享
echo.
echo 推荐安装 Python 以获得完整功能：
echo   pip install cryptography
echo   python generate-keys.py
echo.

endlocal
