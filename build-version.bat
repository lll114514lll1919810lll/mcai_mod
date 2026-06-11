@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  MCAI Multi-Version Build Script
REM  Usage: build-version.bat [version]
REM  Versions: 1.21.11 (default), 26.1.2
REM ============================================================

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=1.21.11"

echo.
echo ========================================
echo  MCAI Build - MC %VERSION%
echo ========================================
echo.

REM Check if version directory exists
if not exist "versions\mc-%VERSION%" (
    echo [ERROR] Version directory not found: versions\mc-%VERSION%
    echo Available versions:
    dir /b /ad versions 2>nul | findstr "mc-"
    exit /b 1
)

REM Backup current files
echo [1/5] Backing up current files...
if exist "build.gradle" copy /y "build.gradle" "build.gradle.bak" >nul
if exist "gradle.properties" copy /y "gradle.properties" "gradle.properties.bak" >nul
if exist "gradle\wrapper\gradle-wrapper.properties" copy /y "gradle\wrapper\gradle-wrapper.properties" "gradle\wrapper\gradle-wrapper.properties.bak" >nul

REM Copy version-specific files
echo [2/5] Applying version %VERSION% configuration...
if exist "versions\mc-%VERSION%\build.gradle" copy /y "versions\mc-%VERSION%\build.gradle" "build.gradle" >nul
if exist "versions\mc-%VERSION%\gradle.properties" copy /y "versions\mc-%VERSION%\gradle.properties" "gradle.properties" >nul

REM Copy version-specific source files
echo [3/5] Applying version %VERSION% source code...
for /r "versions\mc-%VERSION%\src" %%f in (*.java) do (
    set "relpath=%%f"
    set "relpath=!relpath:versions\mc-%VERSION%\src\=!"
    set "targetdir=!relpath:\%%~nxf=!"
    if not exist "!targetdir!" mkdir "!targetdir!" 2>nul
    copy /y "%%f" "!relpath!" >nul
)

REM Copy version-specific resources
if exist "versions\mc-%VERSION%\fabric.mod.json" (
    copy /y "versions\mc-%VERSION%\fabric.mod.json" "src\main\resources\fabric.mod.json" >nul
)

REM Build
echo [4/5] Building...
call .\gradlew.bat build
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed!
    goto :restore
)

REM Copy JAR to safe location
echo [5/5] Copying JAR...
if not exist "final_jars" mkdir "final_jars"
copy /y "build\libs\*.jar" "final_jars\mcai-%VERSION%.jar" >nul
echo.
echo ========================================
echo  BUILD SUCCESS: final_jars\mcai-%VERSION%.jar
echo ========================================
echo.

:restore
REM Restore original files
echo Restoring original files...
if exist "build.gradle.bak" (
    move /y "build.gradle.bak" "build.gradle" >nul
) else (
    del "build.gradle" 2>nul
)
if exist "gradle.properties.bak" (
    move /y "gradle.properties.bak" "gradle.properties" >nul
) else (
    del "gradle.properties" 2>nul
)
if exist "gradle\wrapper\gradle-wrapper.properties.bak" (
    move /y "gradle\wrapper\gradle-wrapper.properties.bak" "gradle\wrapper\gradle-wrapper.properties" >nul
)

REM Restore source files (restore from git)
echo Restoring source files...
git checkout -- src/main/java/ src/main/resources/ 2>nul

echo Done.
