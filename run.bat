@echo off
title CampusCare - Campus Complaint Priority System
echo ======================================================================
echo    CampusCare - Campus Complaint Priority System (Java 21)
echo ======================================================================
echo.

if not exist out mkdir out
echo [1/2] Compiling Java backend source files...
javac -d out src\com\campuscare\*.java

if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed. Please ensure JDK 17+ is in your PATH.
    pause
    exit /b %errorlevel%
)

echo [2/2] Compilation successful!
echo.
echo Launching CampusCare web portal at: http://localhost:8080
echo Press Ctrl+C in this terminal window to stop the server anytime.
echo.

start http://localhost:8080
java -cp out com.campuscare.CampusCareServer
pause
