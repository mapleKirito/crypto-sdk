@echo off
REM Crypto SDK Install Script for Windows
REM Usage: install-local.bat [version]

setlocal enabledelayedexpansion

set SDK_VERSION=%1
if "%SDK_VERSION%"=="" set SDK_VERSION=1.0.0

echo ========================================
echo Crypto SDK Installer for Windows
echo ========================================
echo Version: %SDK_VERSION%
echo.

where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven is not installed or not in PATH
    echo Please install Maven first: https://maven.apache.org/install.html
    exit /b 1
)

echo [INFO] Checking Java version...
java -version 2>&1 | findstr "version"
echo.

echo [INFO] Building and installing Crypto SDK...
echo.

call mvn clean install ^
    -DskipTests ^
    -Dmaven.javadoc.skip=true ^
    -Dmaven.source.skip=true

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Installation Successful!
    echo ========================================
    echo.
    echo To use in your business system, add this dependency to pom.xml:
    echo.
    echo   ^<dependency^>
    echo       ^<groupId^>com.ai.extender^</groupId^>
    echo       ^<artifactId^>crypto-sdk^</artifactId^>
    echo       ^<version^>%SDK_VERSION%^</version^>
    echo   ^</dependency^>
    echo.
) else (
    echo.
    echo ========================================
    echo Installation Failed!
    echo ========================================
    echo Please check the error messages above.
    exit /b 1
)

endlocal
