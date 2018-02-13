echo off
cls 

Echo Script scm create snapshots
Echo Nom script : create_snapshot_rtc.bat 
Echo Date creation : le 06 12 2016 
Echo Auteur : xxx

REM Positionnement repertoire outils scmtools
SET repscm="C:\IBM\SDP\scmtools\eclipse\"

Echo -------------------------------------------------------- 
Echo Debut de traitement %date% - %time% 

Echo -------------------------------------------------------- 
Echo Création snapshot batch 
Echo -------------------------------------------------------- 
call %repscm%\scm create snapshot -r "https://monrtc:9443/ccm/"; "mon stream" -u "xxx" -P "xxx" -n "xxx mon snap batch 2" 

Echo -------------------------------------------------------- 
Echo Fin de traitement %date% - %time%

