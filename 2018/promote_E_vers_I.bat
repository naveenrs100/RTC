setlocal ENABLEDELAYEDEXPANSION
rem @echo off

REM Objet : Script de promotion du stream E vers le stream I
REM Auteur : Philippe MOTTIER
REM Date cr�ation : 28/03/2018
REM Mise � jour : 24/04/2018
REM Mise � jour : 

Echo ==== Lancement Script promotion stream E vers stream I promote_E_vers_I.bat =============
Echo Version du 24/04/2018
Echo Date lancement traitement  : %date%
Echo Heure lancement traitement : %time%
Echo Serveur de build           : %computername%
Echo User Name RTC              : %username%


REM =============================================================================================================
REM Affectation des variables avec les parametres pass�s au script de build
SET WI_DT=%1

Echo =============================================================================================================

Echo Positionnement variables environnement de dev
call D:\IBM\scripts\build_contexte_dev.bat

Echo positoinnement variables compl�mentaires pour rtc scm
set repsandNAS=F:\Vues_com\sandboxs_env

Echo positoinnement variables compl�mentaires pour curl
set repcurl=d:\ibm\curl
set URLWI=https://rtc-dev:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/
set HEADER="Accept: application/x-oslc-cm-change-request+xml"
set COOKIES=%reptra%\cookies.txt

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
Echo --WI de type DT           : %WI_DT%
Echo Les variables calculees sont :
Echo --RefRTC                  : %refrtc%
Echo --UserRTC                 : %userrtc%
Echo --PswRTC                  : ******
Echo --Fichier param de build  : %ficparbui%

Echo =============================================================================================================
Echo Debut de traitement %date% - %time%

if exist %reptra%\list_promo_e_i.txt del %reptra%\list_promo_e_i.txt
echo Work Item DT : %WI_DT% > %reptra%\list_promo_e_i_arborescence.txt

Echo ================================== TRAITEMENTS CURL DE WORK ITEM =============================================

Echo Requete cookie
d:\ibm\curl\curl.exe -k -c %COOKIES% https://rtc-dev:9443/ccm/authenticated/identity
Echo Login
%repcurl%\curl.exe -k -L -b %COOKIES% -c %COOKIES% -d j_username=%userrtc% -d j_password=%pswrtc% https://rtc-dev:9443/ccm/authenticated/j_security_check
 
Echo Requete WI
%repcurl%\curl.exe -k -b %COOKIES% https://rtc-dev:9443/ccm/oslc/workitems/catalog

Echo Acces work item DT
d:\ibm\curl\curl.exe -k -b %COOKIES% -H %HEADER% %URLWI%%WI_DT%?oslc_cm.properties=rtc_cm:state rdf:resource > %reptra%/WI_%WI_DT%_DT.txt

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

Echo ========================================= TRAITEMENT RTC SCM ================================================
:connexion
Echo Connexion a RTC - Etape 1 : %date% - %time%
%repscm%\scm login -r "%refrtc%" -n "connexionrtc" -u "%userrtc%" -P "%pswrtc%" 
Echo Code retour : %ERRORLEVEL%
If %ERRORLEVEL% EQU 0 GOTO :accept_I
SET messerr=Erreur de connexion RTC
Goto :Erreur

:accept_I
Echo =============================================================================================================
Echo Lancement commande RTC SCM accept sandbox I  - Etape 2 : %date% - %time%
%repscm%\scm accept -v -r "connexionrtc" -d %repsandNAS%\ENV-8X00-I --overwrite-uncommitted
Echo Fin de traitement RTC accept %date% - %time%

:liste_fic_WI
Echo =============================================================================================================
Echo Lancement commandes RTC SCM pour avoir la liste des fichiers d un WI  - Etape 3 : %date% - %time%

Echo liste des cs du wi %WI_DT% par commande scm
%repscm%\scm ls cs -W %WI_DT% -r "connexionrtc" 

Echo liste des cs du wi boucle
for /f "skip=2 tokens=1" %%a in ('%repscm%\scm ls cs -W %WI_DT% -r "connexionrtc"') do (
call :cs %%a
)

Echo =============================================================================================================
echo === Liste des entites promues
type %reptra%\list_promo_e_i.txt
echo === Arborescence WI DT
type %reptra%\list_promo_e_i_arborescence.txt

:compil
Echo =============================================================================================================
Echo Lancement compilation sur integration I par appel script  - Etape 4 : %date% - %time%
Echo A completer
ECho si retour KO faire un restore de stream et rws avec dernier snapshot du stream
Echo et changement statut WI pour retour a l etat preview

:maj_wi_dt
Echo =============================================================================================================
Echo Mise a jour statut wi dt a en integration - Etape 5 : %date% - %time%
Echo A completer

:lib_promo_i
Echo =============================================================================================================
Echo Liberation de l objet promotion vers I - Etape 6 : %date% - %time%
Echo A valider et le cas echeant a completer


goto :Fin

Echo Traitement termin� en  erreur - Etape 8 : %date% - %time%
:Erreur
Echo ===============================================================================================
Echo :                                                                                             :
Echo :                                                                                             :
Echo :                                                                                             :
Echo :        Traitement termin� en erreur le %date% � %time%              
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


:Fin
Echo =============================================================================================================
Echo Fin de traitement normale - Etape 7 : %date% - %time%
Echo Traitement termine sans erreur

Echo *** Fin Script promotion stream E vers stream I promote_E_vers_I.bat ********
EXIT /B 0

REM *******************************                Sous-fonctions             ***********************

:CONV_VAR_to_MAJ
FOR %%z IN (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0
:CONV_VAR_to_min
FOR %%z IN (a b c d e f g h i j k l m n o p q r s t u v w x y z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0 

:cs
Echo =============================================================================================================
Echo Traitement d un change set

set cs=%~1
set numcs=%cs:~1,4%

echo -- Changeset : %numcs% >> %reptra%\list_promo_e_i_arborescence.txt

@echo mon numero de cs est : %numcs% et la liste des changes par commande scm :
%repscm%\scm ls ch -r "connexionrtc" %numcs%

%repscm%\scm ls ch -r "connexionrtc" %numcs% > %reptra%\list_ch.txt

for /f  "skip=4 tokens=2,4" %%i in (%reptra%\list_ch.txt) do (
set var1=%%i
echo var1 : !var1!
set var2=%%j
echo var2 : !var2!
set car=!var1:~0,1!
echo car : !car!

if !car! == ( (
set typ=!var2:~-3!
echo type : !typ!

SET premier=%Source:~0,1%
Echo Premiere lettre source a transferer : %premier%
REM deuxieme lettre du nom du source a transferer
SET deux=%Source:~1,1%
Echo Deuxiemelettre source a transferer : %deux%
REM Troisieme lettre du nom du source a transferer
SET trois=%Source:~2,1%



if !typ! EQU cbl goto cbl
if !typ! EQU ksh goto ksh
Echo A completer pour les autres types

:cbl
Echo type de source cobol
set ent=!var2:~-10!
echo ent : !ent!
SET premier=%ent:~0,1%
Echo Premiere lettre source a promouvoir : %premier%
REM Alim repertoire source sandbox E
SET repsource=\ENV-8X00-E\ENV-8X_SRC1\8X_SRC1_BAS\%premier%%premier%%premier%
Echo A completer pour les autres cbl qui ne sont pas de type BAS
goto suite

:ksh
Echo type de source ksh
set ent=!var2:~-12!
echo ent : !ent!
SET premier=%ent:~0,1%
Echo Premiere lettre source a promouvoir : %premier%
REM deuxieme lettre du nom du source a transferer
SET deux=%ent:~1,1%
Echo Deuxiemelettre source a promouvoir : %deux%
REM Troisieme lettre du nom du source a promouvoir 
SET trois=%ent:~2,1%
Echo Troisieme lettre source a transferer : %trois%
REM Alim repertoire source sandbox E
SET repsource=\ENV-8X00-E\ENV-8X_SRC4\8X_SRC4_SHL\%premier%%deux%%trois%
goto suite

Echo Type de source non trait�)

:suite
if !car! == ( (echo !repsource! !ent! >> %reptra%\list_promo_e_i.txt
echo ------- Change : !var1! Fichier : !ent! >> %reptra%\list_promo_e_i_arborescence.txt
)
)

exit /B 0

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
set lietat=Publi�
goto suitewidt

:S3d
set lietat=Preview OK
goto suitewidt

:S4d
set lietat=En Int�gration
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

endlocal
