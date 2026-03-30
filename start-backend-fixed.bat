@echo off
chcp 65001 >nul
echo ========================================
echo 浜戠墽鏅烘劅 - Java 鍚庣鍚姩鑴氭湰
echo ========================================
echo.

echo [1/3] 妫€鏌?Java 鐜...
java -version >nul 2>&1
if errorlevel 1 (
    echo [閿欒] 鏈壘鍒?Java锛岃鍏堝畨瑁?JDK 11+
    pause
    exit /b 1
)
echo [瀹屾垚] Java 鐜姝ｅ父

echo.
echo [2/3] 妫€鏌?Maven...
where mvn >nul 2>&1
if errorlevel 1 (
    echo [閿欒] 鏈壘鍒?Maven锛岃鍏堝畨瑁?Maven
    pause
    exit /b 1
)
echo [瀹屾垚] Maven 鐜姝ｅ父

echo.
echo [3/3] 缂栬瘧骞跺惎鍔?Java 鍚庣...
cd /d "%~dp0yunmu-backend"

echo 姝ｅ湪缂栬瘧...
call mvn clean compile -DskipTests

if errorlevel 1 (
    echo [閿欒] 缂栬瘧澶辫触
    pause
    exit /b 1
)

echo.
echo 姝ｅ湪鍚姩...
echo Java 鍚庣灏嗗湪 http://localhost:8080 鍚姩
echo H2 鏁版嵁搴撴帶鍒跺彴锛歨ttp://localhost:8080/h2-console
echo Swagger 鏂囨。锛歨ttp://localhost:8080/swagger-ui.html
echo.

start "浜戠墽鏅烘劅-Java 鍚庣" cmd /k "mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"

echo [瀹屾垚] Java 鍚庣宸插惎鍔?echo.
echo ========================================
echo 鍚姩瀹屾垚!
echo - 鏈嶅姟鍦板潃锛歨ttp://localhost:8080
echo - H2 鎺у埗鍙帮細http://localhost:8080/h2-console
echo - JDBC URL: jdbc:h2:mem:yunmu_dev
echo - 鐢ㄦ埛鍚嶏細sa
echo - 瀵嗙爜锛?绌?
echo ========================================
echo.
pause
