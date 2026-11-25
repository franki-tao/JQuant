@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo ============================================================
echo   JQuant Workbench Launcher (Backend :8080 + Frontend :5173)
echo ============================================================
echo.

echo [1/4] Syncing JQuanlib core library into local Maven repo...
call mvn -f "%ROOT%pom.xml" -pl JQuanlib install -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Failed to build JQuanlib. Please check Maven/JDK setup.
    pause
    exit /b 1
)

if not exist "%ROOT%jquant-web\node_modules" (
    echo [2/4] Installing frontend dependencies...
    call npm --prefix "%ROOT%jquant-web" install
    if errorlevel 1 (
        echo [ERROR] npm install failed. Please check Node.js setup.
        pause
        exit /b 1
    )
) else (
    echo [2/4] Frontend dependencies already installed.
)

echo [3/4] Starting Spring Boot backend (port 8080) and Vite frontend (port 5173)...
start "JQuant Backend (Spring Boot :8080)" cmd /k "cd /d "%ROOT%" && mvn -f "%ROOT%pom.xml" -pl jquant-service spring-boot:run"
start "JQuant Frontend (Vite :5173)" cmd /k "cd /d "%ROOT%jquant-web" && npm run dev"

echo [4/4] Waiting for services to come online...
powershell -NoProfile -Command ^
  "$deadline = (Get-Date).AddSeconds(45); " ^
  "while ((Get-Date) -lt $deadline) { " ^
  "  try { $r = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/v1/system/health' -TimeoutSec 2; if ($r.StatusCode -eq 200) { break } } catch {} " ^
  "  Start-Sleep -Seconds 1 " ^
  "}"

echo.
echo   Workbench UI : http://localhost:5173
echo   Swagger Docs : http://localhost:8080/swagger-ui.html
echo.
echo Opening JQuant Workbench in your default browser...
start "" "http://localhost:5173"

echo.
echo Both services are running in their own windows.
echo Close those two terminal windows whenever you want to stop them.
timeout /t 5 >nul
endlocal
