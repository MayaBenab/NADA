@echo off
rem Scalability experiment: runs NADA on the synthetic policies in bench\ and
rem writes bench\results.csv. Double-click this file, or run it from a terminal.
cd /d "%~dp0"
echo ============================================
echo   NADA - scalability benchmark
echo ============================================
echo.
echo Compiling...
call mvn -q compile > bench\benchmark_log.txt 2>&1
if errorlevel 1 (
    echo.
    echo ============================================
    echo   Compilation failed. The full error is in
    echo   bench\benchmark_log.txt
    echo ============================================
    type bench\benchmark_log.txt
    echo.
    pause
    exit /b 1
)
echo Running the benchmark, this takes a few minutes...
call mvn -q exec:java -Dexec.mainClass=com.example.ngac.support.Benchmark -Dexec.args="bench/synth100/policy.json bench/synth200/policy.json bench/synth500/policy.json bench/synth1000/policy.json bench/synth2000/policy.json bench/synth5000/policy.json" 1> bench\results.csv 2>> bench\benchmark_log.txt
if errorlevel 1 (
    echo.
    echo ============================================
    echo   The run failed. The full error is in
    echo   bench\benchmark_log.txt
    echo ============================================
    type bench\benchmark_log.txt
    echo.
    pause
    exit /b 1
)
echo.
echo Results written to bench\results.csv
echo.
type bench\results.csv
echo.
echo Machine and JDK:
powershell -NoProfile -Command "Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, MaxClockSpeed | Format-List"
powershell -NoProfile -Command "'RAM (GB): ' + [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB)"
java -version
echo.
pause
