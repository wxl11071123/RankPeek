@echo off
setlocal EnableDelayedExpansion

echo ========================================
echo   RankPeek Build Script (Native Image)
echo ========================================
echo.

:: Record start time
set "START_TIME=%time%"

set "ROOT_DIR=%~dp0"
set "BACKEND_DIR=%ROOT_DIR%rankpeek-backend"
set "FRONTEND_DIR=%ROOT_DIR%rankpeek-frontend"

:: Set GraalVM path
set "GRAALVM_HOME=D:\graalvm-jdk-21.0.10+8.1"
set "JAVA_HOME=%GRAALVM_HOME%"
set "PATH=%GRAALVM_HOME%\bin;%PATH%"

echo [1/4] Checking environment...

if not exist "%GRAALVM_HOME%\bin\java.exe" (
    echo ERROR: GraalVM not found
    echo Please modify the GRAALVM_HOME path in this script
    pause
    exit /b 1
)

where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven not found
    pause
    exit /b 1
)

where node >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Node.js not found
    pause
    exit /b 1
)

echo    GraalVM: %GRAALVM_HOME%
echo    Maven: OK
echo    Node.js: OK
echo [OK] Environment check passed
echo.

echo [2/4] Initializing MSVC environment...

set "VCVARS_FOUND=0"

:: Use vswhere to find VS installation
if exist "%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" (
    for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        if exist "%%i\VC\Auxiliary\Build\vcvars64.bat" (
            call "%%i\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
            set "VCVARS_FOUND=1"
            echo    Using VS: %%i
        )
    )
)

:: Try manual path if vswhere not found
if exist "D:\Program Files\Microsoft Visual Studio\18\Enterprise\VC\Auxiliary\Build\vcvars64.bat" (
    call "D:\Program Files\Microsoft Visual Studio\18\Enterprise\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
    set "VCVARS_FOUND=1"
    echo    Using VS: D:\Program Files\Microsoft Visual Studio\18\Enterprise
)

if "%VCVARS_FOUND%"=="0" (
    echo WARNING: MSVC environment not found, Native Image compilation may fail
    echo Please install Visual Studio Build Tools with C++ desktop workload
) else (
    echo [OK] MSVC environment initialized
)
echo.

echo [3/4] Compiling Native Image...
cd /d "%BACKEND_DIR%"

call mvn clean package -Pnative -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Native Image compilation failed
    echo.
    echo Please ensure you have installed:
    echo   1. GraalVM JDK 21
    echo   2. Visual Studio Build Tools with C++ desktop workload
    echo   3. Correctly set GRAALVM_HOME environment variable
    echo.
    pause
    exit /b 1
)

if not exist "target\rankpeek-native.exe" (
    echo ERROR: rankpeek-native.exe not found
    pause
    exit /b 1
)

:: Calculate file size (MB)
for %%A in ("target\rankpeek-native.exe") do set "NATIVE_SIZE_BYTES=%%~zA"
set /a "NATIVE_SIZE_MB=%NATIVE_SIZE_BYTES%/1024/1024"
echo [OK] Native Image compiled (Size: %NATIVE_SIZE_MB% MB)
echo.

echo [4/4] Building frontend...
cd /d "%FRONTEND_DIR%"

if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo ERROR: Dependency installation failed
        pause
        exit /b 1
    )
)

call npm run electron:build
if %errorlevel% neq 0 (
    echo ERROR: Frontend build failed
    pause
    exit /b 1
)

echo.
echo [OK] Frontend build completed

:: Calculate elapsed time
set "END_TIME=%time%"
call :CalculateTime "%START_TIME%" "%END_TIME%"

echo.
echo ========================================
echo   Build completed! Elapsed: %ELAPSED%
echo ========================================
echo.
echo Output files:
echo   - Installer: %FRONTEND_DIR%\release\RankPeek Setup 1.0.0.exe
echo   - Portable: %FRONTEND_DIR%\release\win-unpacked\
echo.
echo File sizes:
echo   - Backend: %NATIVE_SIZE_MB% MB
echo.

explorer "%FRONTEND_DIR%\release"
pause
exit /b 0

:: Calculate time difference function
:CalculateTime
setlocal
set "START=%~1"
set "END=%~2"

:: Parse start time
for /f "tokens=1-3 delims=:." %%a in ("%START%") do (
    set /a "START_H=%%a"
    set /a "START_M=1%%b-100"
    set /a "START_S=1%%c-100"
)

:: Parse end time
for /f "tokens=1-3 delims=:." %%a in ("%END%") do (
    set /a "END_H=%%a"
    set /a "END_M=1%%b-100"
    set /a "END_S=1%%c-100"
)

:: Calculate total seconds
set /a "START_TOTAL=START_H*3600+START_M*60+START_S"
set /a "END_TOTAL=END_H*3600+END_M*60+END_S"

:: Handle midnight crossover
if %END_TOTAL% LSS %START_TOTAL% set /a "END_TOTAL+=86400"

set /a "ELAPSED_S=END_TOTAL-START_TOTAL"
set /a "ELAPSED_M=ELAPSED_S/60"
set /a "ELAPSED_S=ELAPSED_S%%60"

if %ELAPSED_M% GTR 0 (
    endlocal & set "ELAPSED=%ELAPSED_M% min %ELAPSED_S% sec"
) else (
    endlocal & set "ELAPSED=%ELAPSED_S% sec"
)
goto :eof
