@echo off
REM MCAI Multi-Version Build Script
REM Branches: master (1.21 / 1.21.11 Yarn), mc-26.1.2 (Mojang)
REM Usage:
REM   build.bat 1.21      - Minecraft 1.21
REM   build.bat 1.21.11   - Minecraft 1.21.11
REM   build.bat 26.1.2    - Minecraft 26.1.2
REM   build.bat all       - Build all three versions

setlocal enabledelayedexpansion

if "%1"=="" (
    echo Usage: build.bat ^<version^>
    echo   build.bat 1.21    - Minecraft 1.21     ^(master branch^)
    echo   build.bat 1.21.11 - Minecraft 1.21.11  ^(mc-1.21.11 branch^)
    echo   build.bat 26.1.2  - Minecraft 26.1.2   ^(mc-26.1.2 branch^)
    echo   build.bat all     - Build all three versions
    exit /b 1
)

REM Build all versions
if "%1"=="all" (
    echo ========================================
    echo Building ALL versions...
    echo ========================================
    call :build_one 1.21
    if !ERRORLEVEL! NEQ 0 exit /b 1
    call :build_one 1.21.11
    if !ERRORLEVEL! NEQ 0 exit /b 1
    call :build_one 26.1.2
    if !ERRORLEVEL! NEQ 0 exit /b 1
    echo ========================================
    echo ALL BUILDS SUCCESSFUL!
    echo ========================================
    exit /b 0
)

call :build_one %1
exit /b %ERRORLEVEL%

:build_one
set VERSION=%1
echo.
echo ========================================
echo Building MC %VERSION%
echo ========================================

REM Determine branch and JDK
if "%VERSION%"=="26.1.2" (
    set BRANCH=mc-26.1.2
    set JAVA_HOME_DIR=D:\Program Files\Microsoft\jdk-25.0.2.10-hotspot
    set TASK=jar
    set JAR_NAME=mcai-26.1.2.jar
) else (
    set BRANCH=master
    set JAVA_HOME_DIR=C:\tools\jdk21\jdk-21.0.7+6
    set TASK=remapJar
    set JAR_NAME=mcai-%VERSION%.jar
)

echo Branch: !BRANCH!
echo JDK: !JAVA_HOME_DIR!

REM Switch branch
echo Switching to branch !BRANCH!...
git checkout -- . 2>nul
git checkout !BRANCH! 2>nul
if !ERRORLEVEL! NEQ 0 (
    echo Error: Failed to switch to branch !BRANCH!
    exit /b 1
)

REM Copy version properties
if not exist "gradle-%VERSION%.properties" (
    echo Error: No gradle-%VERSION%.properties found
    exit /b 1
)
copy /Y "gradle-%VERSION%.properties" "gradle.properties" > nul
echo Properties: gradle-%VERSION%.properties

REM Build
echo Running gradlew !TASK!...
set JAVA_HOME=!JAVA_HOME_DIR!
set PATH=!JAVA_HOME!\bin;%PATH%
call gradlew.bat !TASK! --no-daemon

if !ERRORLEVEL! EQU 0 (
    echo ^> MC %VERSION% SUCCESS
    if exist "build\libs\mcai-1.0.0.jar" (
        copy /Y "build\libs\mcai-1.0.0.jar" "build\libs\!JAR_NAME!" > nul
        echo ^> Saved as build\libs\!JAR_NAME!
    )
) else (
    echo ^> MC %VERSION% FAILED
    exit /b 1
)

goto :eof
