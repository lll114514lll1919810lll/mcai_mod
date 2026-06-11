@echo off
REM MCAI Multi-Version Build Script
REM Usage: build.bat 1.21    - build for MC 1.21
REM Usage: build.bat 1.21.11 - build for MC 1.21.11

setlocal enabledelayedexpansion

if "%1"=="" (
    echo Usage: build.bat ^<version^>
    echo   build.bat 1.21    - Minecraft 1.21
    echo   build.bat 1.21.11 - Minecraft 1.21.11
    exit /b 1
)

if not exist "gradle-%1.properties" (
    echo Error: No config found for version %1
    exit /b 1
)

echo ^> Switching to MC %1...
copy /Y "gradle-%1.properties" "gradle.properties" > nul

echo ^> Building...
set JAVA_HOME=C:\tools\jdk21\jdk-21.0.7+6
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat remapJar --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo ^> Success! JAR: build\libs\mcai-1.0.0.jar
) else (
    echo ^> Build failed for MC %1
)
