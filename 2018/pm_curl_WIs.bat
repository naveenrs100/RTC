setlocal ENABLEDELAYEDEXPANSION
@echo off
set WI_Acti=%1
set WI_DT=%2
set URLWI=https://rtc-dev:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/
set HEADER="Accept: application/x-oslc-cm-change-request+xml"

rem Login 
set USER=Admin_CC
set PWD=admc0504
rem File which will contain the login cookies
set COOKIES=cookies.txt
 
rem First pass to request the authentication cookie
d:\ibm\curl\curl.exe -k -c %COOKIES% https://rtc-dev:9443/ccm/authenticated/identity
rem Second pass to login
d:\ibm\curl\curl.exe -k -L -b %COOKIES% -c %COOKIES% -d j_username=%USER% -d j_password=%PWD% https://rtc-dev:9443/ccm/authenticated/j_security_check
 
rem Request the url using the authentication
d:\ibm\curl\curl.exe -k -b %COOKIES% https://rtc-dev:9443/ccm/oslc/workitems/catalog

Echo ======================================Traitement des Work Item ======================================================

rem work item activite
d:\ibm\curl\curl.exe -k -b %COOKIES% -H %HEADER% %URLWI%%WI_Acti%?oslc_cm.properties=rtc_cm:state rdf:resource > WI_%WI_Acti%_acti.txt

rem work item DT
d:\ibm\curl\curl.exe -k -b %COOKIES% -H %HEADER% %URLWI%%WI_DT%?oslc_cm.properties=rtc_cm:state rdf:resource > WI_%WI_DT%_DT.txt

Echo Traitement etat WI Activite ---------------------------------------
for /f  "tokens=1,2,3" %%i in (WI_%WI_Acti%_acti.txt) do (
set var1=%%i
set var2=%%j
set var3=%%k
set retour=!var3:~-5!
set typtmp=!var2:~-14!

if !retour! EQU error (
set lierr=WI activite %WI_Acti% non trouve
goto :erreur
)

set typlig=!var1:~8,5!

if !typlig! EQU state (
echo Ligne de type etat wi activite

set typwi=!typtmp:~0,2!
echo Type de work item : !typwi!

if !typwi! NEQ ow (
set lierr=Le WI %WI_Acti% n'est pas de type Activite, trouve : !typwi!
goto :erreur
)

set travail=!var2:~-5!
set etat=!travail:~0,2!
call :cnvact
echo Le WI Activite !WI_Acti! a pour etat : !lietat!
)
)


Echo Traitement etat WI DT ----------------------------------------------
for /f  "tokens=1,2,3" %%i in (WI_%WI_DT%_DT.txt) do (
set var1=%%i
set var2=%%j
set var3=%%k
set retour=!var3:~-5!
set typtmp=!var2:~-14!

if !retour! EQU error (
set lierr=WI DT non trouve
goto :erreur
)

set typlig=!var1:~8,5!

if !typlig! EQU state (

echo Ligne de type etat wi dt

set typwi=!typtmp:~0,2!
echo Type de work item : !typwi!

if !typwi! NEQ dt (
set lierr=Le WI %WI_DT% n'est pas de type DT, trouve : !typwi!
goto :erreur
)

set var2=%%j
set travail=!var2:~-5!
set etat=!travail:~0,2!
call :cnvdt
echo Le WI DT !WI_DT! a pour etat : !lietat!
)
)

goto fintrt


:erreur
echo Erreur : !lierr!


:fintrt

exit /B 0


Rem =================== Sous fonctions appellées ======================================

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




endlocal




