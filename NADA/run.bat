@echo off
cd /d "%~dp0"
echo ============================================
echo   NADA - lancement
echo ============================================
echo.
mvn -q compile exec:java "-Dexec.mainClass=com.example.ngac.NADA"
if errorlevel 1 (
    echo.
    echo ============================================
    echo   Une erreur s'est produite au lancement.
    echo   Verifie que Maven est installe et que la
    echo   commande "mvn -v" fonctionne dans une
    echo   invite de commandes.
    echo ============================================
)
echo.
pause
