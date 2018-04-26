setlocal ENABLEDELAYEDEXPANSION
@echo off

REM Objet : Script de transfert du stream de developpement E1 vers le stream environnement E sources rpp
REM Auteur : Philippe MOTTIER
REM Date création : 26/02/2018
REM Mise à jour : 25/04/2018
REM Mise à jour : 

Echo ==== Lancement Script transfert stream dev E1 vers stream env E build_transfert-E1_E_natif.bat =============
Echo Version du 25/04/2018
Echo Date lancement traitement  : %date%
Echo Heure lancement traitement : %time%
Echo Serveur de build           : %computername%
Echo User Name RTC              : %username%


REM =============================================================================================================
REM Affectation des variables avec les parametres passés au script de build
SET Type=%1
SET Source=%2
SET WI_Activite=%3
SET WI_DT=%4

Echo =============================================================================================================

Echo Positionnement variables environnement de dev
call D:\IBM\scripts\build_contexte_dev.bat

Echo positoinnement variables complémentaires pour rtc scm
set repsandNAS=F:\Vues_com\sandboxs_env

Echo positoinnement variables complémentaires pour curl
set repcurl=d:\ibm\curl
set URLWI=https://rtc-dev:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/
set HEADER="Accept: application/x-oslc-cm-change-request+xml"
set COOKIES=%reptra%\cookies.txt

REM Premiere lettre du nom du source a transferer
SET premier=%Source:~0,1%
Echo Premiere lettre source a transferer : %premier%

REM deuxieme lettre du nom du source a transferer
SET deux=%Source:~1,1%
Echo Deuxiemelettre source a transferer : %deux%

REM Troisieme lettre du nom du source a transferer
SET trois=%Source:~2,1%
Echo Troisieme lettre source a transferer : %trois%

REM Appel conversion en minuscule lettres
CALL :CONV_VAR_to_min premier
CALL :CONV_VAR_to_min deux
CALL :CONV_VAR_to_min trois

REM Alim extension, rep source, rep cible et type rep SRCx
if %Type%==BAS (
Echo type BAS
set typerep=SRC1
echo typerep : !typerep!
echo  -- 8X_!typerep!_TRA
set nomsource=!Source!.cbl

REM Alim repertoire source sandbox E1
SET repsoue1=!repsand!\8X00_E1_manu\8X_!typerep!_TRA\8X_!typerep!_!Type!\!premier!!premier!!premier!
Echo repsoue1 : !repsoue1!

REM Alim repertoire cible sandbox E
SET repcibe=!repsandNAS!\ENV-8X00-E\ENV-8X_!typerep!\8X_!typerep!_!Type!\!premier!!premier!!premier!
Echo repcibe : !repcibe!)


if %Type%==SCR (
set typerep=SRC2
set nomsource=!Source!.scr)

if %Type%==SRS (
set typerep=SRC2
set nomsource=!Source!.cbl)

if %Type%==SHL (
Echo type SHL
set typerep=SRC4
set nomsource=!Source!.ksh
echo typerep : !typerep!
echo  -- 8X_!typerep!_TRA

Echo Alim repertoire source sandbox E1
SET repsoue1=!repsand!\8X00_E1_manu\8X_!typerep!_TRA\8X_!typerep!_!Type!\!premier!!deux!!trois!
Echo repsoue1 : !repsoue1!

Echo Alim repertoire cible sandbox E
SET repcibe=!repsandNAS!\ENV-8X00-E\ENV-8X_!typerep!\8X_!typerep!_!Type!\!premier!!deux!!trois!
Echo repcibe : !repcibe!)




Echo A completer pour les sources src3, src4, src5, etc .....






:affic
REM Affichage des variables

Echo Les variables d environnement sont :
Echo --Lecteur Clearcase       : %RATIONAL_VIEWROOT_PATH% 
Echo --Repertoire de travail   : %reptra%
Echo --Repertoire de log       : %replog%
Echo --Repertoire des sandboxs : %repsand%
Echo --Repertoire workspaces   : %repworkspace%
Echo --Projet                  : %projet%
Echo --Repertoire SCM          : %repscm%
Echo Les parametres en entree du build RTC sont :
Echo --Type technique source   : %Type%
Echo --Source a transferer     : %Source%
Echo --WI de type Activite     : %WI_Activite%
Echo --WI de type DT           : %WI_DT%
Echo Les variables calculees sont :
Echo --RefRTC                  : %refrtc%
Echo --UserRTC                 : %userrtc%
Echo --PswRTC                  : ******
Echo --Fichier param de build  : %ficparbui%
Echo --Type repertoire source  : %typerep%
Echo --Nom complet source      : %nomsource%
Echo --Rep sandbox source      : %repsoue1%
Echo --Rep sandbox cible       : %repcibe%

Echo =============================================================================================================
Echo Debut de traitement %date% - %time%

Echo ================================== TRAITEMENTS CURL DE WORK ITEM =============================================

Echo Requete cookie
d:\ibm\curl\curl.exe -k -c %COOKIES% https://rtc-dev:9443/ccm/authenticated/identity
Echo Login
%repcurl%\curl.exe -k -L -b %COOKIES% -c %COOKIES% -d j_username=%userrtc% -d j_password=%pswrtc% https://rtc-dev:9443/ccm/authenticated/j_security_check
 
Echo Requete WI
%repcurl%\curl.exe -k -b %COOKIES% https://rtc-dev:9443/ccm/oslc/workitems/catalog

Echo Acces work item DT
d:\ibm\curl\curl.exe -k -b %COOKIES% -H %HEADER% %URLWI%%WI_DT%?oslc_cm.properties=rtc_cm:state rdf:resource > %reptra%/WI_%WI_DT%_DT.txt

Echo Acces work item activite
d:\ibm\curl\curl.exe -k -b %COOKIES% -H %HEADER% %URLWI%%WI_Activite%?oslc_cm.properties=rtc_cm:state rdf:resource > %reptra%/WI_%WI_Activite%_acti.txt

Echo Traitement etat WI DT ----------------------------------------------
for /f  "tokens=1,2,3" %%i in (%reptra%/WI_%WI_DT%_DT.txt) do (
set var1=%%i
set var2=%%j
set var3=%%k
set retour=!var3:~-5!
set typtmp=!var2:~-14!

if !retour! EQU error (
set messerr=WI DT non trouve
goto :erreur
)

set typlig=!var1:~8,5!

if !typlig! EQU state (

echo Ligne de type etat wi dt

set typwi=!typtmp:~0,2!
echo Type de work item : !typwi!

if !typwi! NEQ dt (
set messerr=Le WI %WI_DT% n'est pas de type DT, trouve : !typwi!
goto :erreur
)

set var2=%%j
set travail=!var2:~-5!
set etat=!travail:~0,2!
call :cnvdt
echo Le WI DT !WI_DT! a pour etat : !lietat!
)
)

Echo Traitement etat WI Activite ---------------------------------------
for /f  "tokens=1,2,3" %%i in (%reptra%/WI_%WI_Activite%_acti.txt) do (
set var1=%%i
set var2=%%j
set var3=%%k
set retour=!var3:~-5!
set typtmp=!var2:~-14!

if !retour! EQU error (
set messerr=WI activite %WI_Activite% non trouve
goto :erreur
)

set typlig=!var1:~8,5!

if !typlig! EQU state (
echo Ligne de type etat wi activite

set typwi=!typtmp:~0,2!
echo Type de work item : !typwi!

if !typwi! NEQ ow (
set messerr=Le WI %WI_Activite% n'est pas de type Activite, trouve : !typwi!
goto :erreur
)

set travail=!var2:~-5!
set etat=!travail:~0,2!
call :cnvact
echo Le WI Activite !WI_Activite! a pour etat : !lietat!
)
)

Echo ========================================= TRAITEMENT RTC SCM ================================================
:connexion
Echo Connexion a RTC - Etape 1 : %date% - %time%
%repscm%\scm login -r "%refrtc%" -n "connexionrtc" -u "%userrtc%" -P "%pswrtc%" 
Echo Code retour : %ERRORLEVEL%
If %ERRORLEVEL% EQU 0 GOTO :verifstreamlibre
SET messerr=Erreur de connexion RTC
Goto :Erreur

:verifstreamlibre 
Echo =============================================================================================================
Echo Verification stream ENV E libre - Etape 2  : %date% - %time%
for /f %%d in ('%repscm%\scm get attributes -w "ENV-8X00-E" --description -r "connexionrtc"') do set StatutStream=%%d
echo Statut du stream ENV E : %StatutStream%
If %StatutStream% EQU Libre GOTO :resastream
SET messerr=Le stream ENV E n est pas libre, son statut est : %StatutStream%
Goto :Erreur

:resastream 
Echo =============================================================================================================
Echo Reservation du stream ENV E - Etape 3 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Reserve Etape 3" -r "connexionrtc"
Echo Code retour : %ERRORLEVEL%

:ctrlWIactiST
Echo =============================================================================================================
Echo Controle statut WI activite non ferme - Etape 5a : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A ne pas faire cf fait par le workflow a confirmer

:ctrlWIDTST
Echo =============================================================================================================
Echo Controle statut WI DT cree - Etape 5b : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A ne pas faire cf fait par le workflow a confirmer

:accept_E1
Echo =============================================================================================================
Echo Lancement commande RTC SCM accept sandbox E1  - Etape 6 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Reserve Etape 6 accept" -r "connexionrtc"

echo debut commande accept === %time%
%repscm%\scm accept -v -r "connexionrtc" -d "%repsand%/8X00_E1_manu" --overwrite-uncommitted
echo fin commande accept === %time%

Echo Fin de traitement RTC accept %date% - %time%

:ctrlsrcdif
Echo =============================================================================================================
Echo Controle sources differents entre E1 et E - Etape 7 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A completer

:copiesand
Echo =============================================================================================================
Echo Copie source sandbox E1 vers sandbox E  - Etape 8 : %date% - %time%
copy /Y %repsoue1%\%nomsource% %repcibe%\%nomsource%
Echo Code retour : %ERRORLEVEL%

REM Test retour, cf si ERRORLEVEl = 1 et pas ZERO Erreur de copie 
IF %ERRORLEVEL% LSS 1 goto snapeavant
SET messerr=Erreur de copie vers sandbox NAS ENV E
goto erreur

:snapeavant
Echo =============================================================================================================
Echo Snapshot stream E avant mise a jour  - Etape 9 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Reserve Etape 9 create ss" -r "connexionrtc"
%repscm%\scm create ss -r "connexionrtc" -n "Snap stream ENV E avant transfert %Source%" "ENV-8X00-E"
Echo Code retour : %ERRORLEVEL%

Echo Infos de debug statut sandbox E avant checkin ***************************************************
%repscm%\scm show status -d %repsandNAS%\ENV-8X00-E -u "Admin_CC" -P "admc0504"

:checkin
Echo =============================================================================================================
Echo Checkin fichier dans sandbox E  - Etape 10 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Reserve Etape 10 checkin" -r "connexionrtc"
REM -N pour ne pas scanner a la recherche de nouveaux changes
REM --complete pour fermer le CS que l on cree et eviter des ajouts ulterieurs

echo debut commande checkin === %time%
%repscm%\scm checkin -N -d %repsandNAS%\ENV-8X00-E --comment "Transfert de E1 dans E" --complete -W %WI_DT% %repcibe%\%nomsource%
echo fin commande checkin === %time%

Echo Infos de debug statut sandbox E apres checkin ***************************************************
%repscm%\scm show status -d %repsandNAS%\ENV-8X00-E -u "Admin_CC" -P "admc0504"

Echo Code retour : %ERRORLEVEL%

:commentCS
Echo =============================================================================================================
Echo Ajout comment CS fichier - Etape 11 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A supprimer !!!!!!!!!!!!!!! Fait lors du checkin

:assowi
Echo =============================================================================================================
Echo Association WI DT au  CS fichier  - Etape 12 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A supprimer !!!!!!!!!!!!!!! Fait lors du checkin

:delivercs
Echo =============================================================================================================
Echo Deliver CS fichier dans stream E  - Etape 13 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Reserve Etape 13 deliver" -r "connexionrtc"

echo debut commande deliver === %time%
%repscm%\scm deliver -W %WI_DT% -d %repsandNAS%\ENV-8X00-E -r "connexionrtc"
echo fin commande deliver === %time%

Echo Infos de debug statut sandbox E apres deliver ***************************************************
%repscm%\scm show status -d %repsandNAS%\ENV-8X00-E -u "Admin_CC" -P "admc0504"

Echo Code retour : %ERRORLEVEL%

:compil
Echo =============================================================================================================
Echo Lancement compilation sur commun etude E par appel script  - Etape 14 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
If %ERRORLEVEL% EQU 0 GOTO :majwidt

Echo restauration du stream E avec le snapshot de debut
%repscm%\scm set component --all ENV-8X00-E  ss "Snap stream ENV E avant transfert %Source%" -r "connexionrtc"

Echo restauration du RWS avec le snapshot de debut
%repscm%\scm set component --all nom du RWS sur E!!!!!!!!!!!!!!!!!!!  ss "Snap stream ENV E avant transfert %Source%" -r "connexionrtc"

SET messerr=Erreur de compilation
Goto :Erreur

:majwidt
Echo =============================================================================================================
Echo Mise a jour statut wi DT a publie  - Etape 15 : %date% - %time%
Echo A completer

:majwiacti 
Echo =============================================================================================================
Echo Mise a jour statut wi Activite a ferme - Etape 16 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A completer

:crealienwi
Echo =============================================================================================================
Echo Creation lien wi Activite et wi DT - Etape 17 : %date% - %time%
Echo Code retour : %ERRORLEVEL%
Echo A completer

:libstream
Echo =============================================================================================================
Echo Liberation du stream - Etape 18 : %date% - %time%
%repscm%\scm set attributes -w "ENV-8X00-E" --description "Libre" -r "connexionrtc"
Echo Code retour : %ERRORLEVEL%

:snapesupp 
Echo =============================================================================================================
Echo Suppression Snapshot stream E  - Etape 19 : %date% - %time%
rem %repscm%\scm delete ss "ENV-8X00-E" "Snap stream ENV E avant transfert %Source%" -r "connexionrtc"
Echo Code retour : %ERRORLEVEL%

goto :Fin

Echo Traitement terminé en  erreur - 21 : %date% - %time%
:Erreur
Echo ===============================================================================================
Echo :                                                                                             :
Echo :                                                                                             :
Echo :                                                                                             :
Echo :        Traitement terminé en erreur le %date% à %time%              
Echo :                                                                                             :
Echo :        Erreur de build                                                                      :
Echo :                                                                                             :
Echo :                                                                                             :
Echo :        Erreur : %messerr%                                                                    
Echo :                                                                                             :
Echo :                                                                                             :
Echo :                                                                                             :
Echo :                                                                                             :
Echo :                                                                                             :
Echo =============================================================================================== 
Exit /B 1

Echo Fin de traitement normale - 20 : %date% - %time%
:Fin
Echo =============================================================================================================
Echo Fin de traitement normale - Etape 21 : %date% - %time%
Echo Traitement termine sans erreur


Echo *** Fin Script transfert stream dev E1 vers stream env E build_transfert-E1_E_natif.bat ********
EXIT /B 0

REM *******************************                Sous-fonctions             ***********************

:CONV_VAR_to_MAJ
FOR %%z IN (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0
:CONV_VAR_to_min
FOR %%z IN (a b c d e f g h i j k l m n o p q r s t u v w x y z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0 

:cnvdt
set lietat=
if !etat! EQU s1 goto :s1d 
if !etat! EQU s2 goto :s2d 
if !etat! EQU s3 goto :s3d 
if !etat! EQU s4 goto :s4d 
if !etat! EQU s5 goto :s5d 
if !etat! EQU s6 goto :s6d 
if !etat! EQU s7 goto :s7d 
if !etat! EQU s8 goto :s8d 
if !etat! EQU s9 goto :s9d 

Echo Erreur sur l etat du WI DT: Etat !etat! Inconnu
goto suitewidt

:S1d
set lietat=En Cours
goto suitewidt

:S2d
set lietat=Publié
goto suitewidt

:S3d
set lietat=Preview OK
goto suitewidt

:S4d
set lietat=En Intégration
goto suitewidt

:S5d
set lietat=Industrialisation
goto suitewidt

:S6d
set lietat=En Validation
goto suitewidt

:S7d
set lietat=Recette
goto suitewidt

:S8d
set lietat=En Production
goto suitewidt

:S9d
set lietat=En attente promotion dans I
goto suitewidt

:suitewidt

exit /B 0



:cnvact
set lietat=
if !etat! EQU s1 goto :s1a 
if !etat! EQU s2 goto :s2a 
if !etat! EQU s3 goto :s3a 
if !etat! EQU s4 goto :s4a 
if !etat! EQU s5 goto :s5a 
if !etat! EQU s6 goto :s6a 

rem Echo Erreur sur l etat du WI activité : Etat !etat! Inconnu
goto suitewiact

:S1a
set lietat=Nouveau
goto suitewiact

:S2a
set lietat=En cours
goto suitewiact

:S3a
set lietat=Terminé
goto suitewiact

:S4a
set lietat=Fermé
goto suitewiact

:S5a
set lietat=Rouvert
goto suitewiact

:S6a
set lietat=Trié
goto suitewiact


:suitewiact

exit /B 0


endlocal