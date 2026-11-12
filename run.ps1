Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   CampusCare - Campus Complaint Priority System (Java 21)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

Write-Host "[1/2] Compiling Java backend source files..." -ForegroundColor Yellow
javac -d out src/com/campuscare/*.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "[2/2] Compilation successful!" -ForegroundColor Green
    Write-Host "Launching CampusCare web portal at: http://localhost:8080" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C in this terminal window to stop the server anytime." -ForegroundColor DarkGray
    Write-Host ""
    Start-Process "http://localhost:8080"
    java -cp out com.campuscare.CampusCareServer
} else {
    Write-Host "[ERROR] Java compilation failed! Check your Java JDK installation." -ForegroundColor Red
}
