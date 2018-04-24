Echo off

Cls

Echo Script de chargement initial par load de la sandbox de build stream ENV-8X00-E
Echo Nom script : load_ENV-8X00-E.bat
Echo Date creation : 20/02/2018
Echo Date mise a jour : 19/04/2018
Echo Auteur : phmo

Echo Debut de traitement %date% - %time%

PATH = %PATH%;D:\IBM\TeamConcertBuild\scmtools\eclipse

Echo Connexion RTC %date% - %time%
scm login -r "https://rtc-dev.appli:9443/ccm/" -n "pmrtc" -u "Admin_CC" -P "admc0504" 

Echo Lancement chargement initial sandbox de build %date% - %time%
scm load -r "pmrtc" "RWS_Admin_CC_ENV-8X00-E_build" -f -d "F:\Vues_com\sandboxs_env\ENV-8X00-E"

Echo Fin de traitement %date% - %time%
