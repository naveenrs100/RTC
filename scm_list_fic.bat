echo on
cls

Echo Script scm de listage des fichiers dans les components
Echo Nom script : scm_list_fic.bat
Echo Date creation : le 11 09 2015
Echo Auteur : XXX

Echo --------------------------------------------------------
Echo Debut de traitement %date% - %time%

PATH = %PATH%;D:\IBM\TeamConcertBuild\scmtools\eclipse
DEL scm_remotelist.txt

Echo --------------------------------------------------------
Echo Connexion RTC de dev 
scm login -r "https://rtc:9443/ccm" -n "xxrtc" -u "xxx" -P "xxx" 

Echo --------------------------------------------------------
Echo Affichage liste des component
call lscm ls components --maximum 100 -r "xxrtc" > xx_list_compo.txt

FOR /f "tokens=2" %%i IN (xx_list_compo.txt) DO (
echo %%i
call lscm ls remotefiles --depth - -w "RWS_xxx_DEV" "%%i" "/" -r "xxrtc" >> xx_scm_remotelist.txt
)

Echo --------------------------------------------------------
Echo Affichage liste des remotefile
REM call lscm ls remotefiles --depth - -w "RWS_xxx_DEV" "mon component" "/" -r "xxrtc"


 
Echo --------------------------------------------------------
Echo Fin de traitement %date% - %time%
