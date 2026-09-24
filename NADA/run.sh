#!/bin/sh
# Lance NADA sur macOS/Linux. Double-cliquer ne marche pas toujours pour un script shell
# selon la configuration du systeme - dans ce cas, ouvrir un terminal dans ce dossier et taper:
#   sh run.sh
cd "$(dirname "$0")"
echo "============================================"
echo "  NADA - lancement"
echo "============================================"
echo
mvn -q compile exec:java "-Dexec.mainClass=com.example.ngac.NADA"
status=$?
if [ $status -ne 0 ]; then
    echo
    echo "============================================"
    echo "  Une erreur s'est produite au lancement."
    echo "  Verifie que Maven est installe et que la"
    echo "  commande \"mvn -v\" fonctionne dans un"
    echo "  terminal."
    echo "============================================"
fi
echo
echo "Appuie sur Entree pour fermer..."
read dummy
