Echo off

Cls

set rep=D:\IBM\TeamConcertBuild\scmtools\eclipse\

Echo Script mise à jour de la sandbox de build stream ENV-8X00-E
Echo Nom script : accept_ENV-8X00-E.bat
Echo Date creation : 26/02/2018
Echo Date mise a jour : 19/04/2018
Echo Auteur : phmo

Echo Debut de traitement %date% - %time%

PATH = %PATH%;D:\IBM\TeamConcertBuild\scmtools\eclipse

Echo Connexion RTC %date% - %time%
%rep%scm login -r "https://rtc-dev.appli:9443/ccm/" -n "pmrtc" -u "Admin_CC" -P "admc0504" 

Echo Lancement mise à jour sandbox de build ENV E - %date% - %time%
%rep%scm accept -v -r "pmrtc" -d "F:\Vues_com\sandboxs_env\ENV-8X00-E" --overwrite-uncommitted

Echo Fin de traitement %date% - %time%

 