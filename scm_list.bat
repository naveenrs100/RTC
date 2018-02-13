echo off
cls

Rem Nom script  : scmlist.bat
Rem Objet       : Listage des fichiers dans les components d un stream, etc.
Rem Auteur      : xxx
Rem Creation    : 11/09/2015
Rem Mise a jour : 14/09/2015

Echo --------------------------------------------------------
Echo Debut de traitement %date% - %time%

Echo --------------------------------------------------------
Echo Mise à jour Path pout l acces a scm
PATH = %PATH%;C:\Program Files\IBM\SDP502\scmtools\eclipse

Echo --------------------------------------------------------
Echo Connexion RTC de dev 
scm login -r "https://localhost:9443/ccm" -n "xxrtc" -u "xxx" -P "xxx" 

Echo --------------------------------------------------------
Echo Affichage liste des streams
call lscm -a y list streams -r "xxrtc"

Echo --------------------------------------------------------
Echo Affichage liste des PA
call lscm ls pa -r "xxrtc"

Echo --------------------------------------------------------
Echo Affichage liste des component
call lscm ls components --maximum 100 -r "xxrtc"

Echo --------------------------------------------------------
Echo Affichage liste des remotefile
call lscm ls remotefiles --depth - -w "Espace de travail test" "mon component" "/" -r "xxrtc"


Echo --------------------------------------------------------
Echo Affichage des preferences
call scm list preferences 

Echo --------------------------------------------------------
Echo Fin de traitement %date% - %time%
