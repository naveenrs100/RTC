echo off
cls 
 
Echo Script test commandes scm 
Echo Nom script : scm_liste_stream.bat 
Echo Date creation : le 06 12 2016 
Echo Auteur : xxx
Echo Infos syntaxe : lscm (options lscm) sous commande (options sous commande)
Echo exemple liste des streams avec alias : uuid affichés
Echo (1111 : _QNWfQCjMEeaq4KaGGXJLFQ) "DEV PRINCIPAL" PA_APP
 
Rem Positionnement repertoire outils scmtools
SET repscm="C:\IBM\SDP\scmtools\eclipse\"

Echo -------------------------------------------------------- 
Echo Debut de traitement %date% - %time% 
Echo -------------------------------------------------------- 
Echo Connexion RTC 
%repscm%\scm login -r "https://mon RTC:9443/ccm/"; -n "xxxrtc" -u "xxx" -P "xxx" 

Echo ===================================================================================================
Echo Liste des streams dont le nom commence par DEV avec affichage alias puis uuid dans un fichier
call %repscm%\lscm --show-alias y --show-uuid y list stream -r "xxrtc" -n "DEV*" > "liste_stream_dev.txt"

Echo ===================================================================================================
Echo Liste des streams dont le nom commence par DEV avec la composition en component dans un fichier
call %repscm%\lscm --show-alias y --show-uuid y -v list stream -r "xxrtc" -n "DEV*" -v > "liste_stream_dev_compo.txt"

Echo ===================================================================================================
Echo Liste des snapshot dans un fichier avec commentaire
call %repscm%\lscm --show-alias y --show-uuid y list snapshot -r "xxrtc" -m 1000 -v > "liste_snapshot.txt"

Echo -------------------------------------------------------- 
Echo Fin de traitement %date% - %time%

