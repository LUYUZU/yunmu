@echo off
echo ========================================
echo Cloud牧 Sensing - Java Backend Starter
echo ========================================
echo.

echo [1/3] Checking Java environment...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 11+
    pause
    exit /b 1
)
echo [OK] Java environment is ready

echo.
echo [2/3] Checking Maven...
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found. Please install Maven
    pause
    exit /b 1
)
echo [OK] Maven environment is ready

echo.
echo [3/3] Compiling and starting Java backend...
cd /d "%~dp0yunmu-backend"

echo Compiling...
call mvn clean compile -DskipTests

if errorlevel 1 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)

echo.
echo Starting backend...
echo Java backend will start at http://localhost:8080
echo H2 Console: http://localhost:8080/h2-console
echo Swagger: http://localhost:8080/swagger-ui.html
echo.

start "Cloud牧 Sensing-Java Backend" cmd /k "mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"

echo [OK] Java backend started
echo.
echo ========================================
echo Startup Complete!
echo - Service URL: http://localhost:8080
echo - H2 Console: http://localhost:8080/h2-console
echo - JDBC URL: jdbc:h2:mem:yunmu_dev
echo - Username: sa
echo - Password: (empty)
echo ========================================
echo.
pause
