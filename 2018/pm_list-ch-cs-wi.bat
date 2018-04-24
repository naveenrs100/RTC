setlocal ENABLEDELAYEDEXPANSION
@echo off

REM Objet : Script liste des cs d un wi
REM Auteur : Philippe MOTTIER
REM Date création : 30/03/2018
REM Mise à jour : 11/04/2018
REM Mise à jour : 

Echo ==== Lancement Script liste des CS d un wi pm_list-cs-wi.bat =============
Echo Version du 11/04/2018
Echo Date lancement traitement  : %date%
Echo Heure lancement traitement : %time%
Echo Serveur de build           : %computername%
Echo User Name RTC              : %username%

REM =============================================================================================================
REM Affectation des variables avec les parametres passés au script de build
SET WI_DT=%1

Echo =============================================================================================================


REM Positionnement variables environnement de dev
call D:\IBM\scripts\build_contexte_dev.bat
set repsandNAS=F:\Vues_com\sandboxs_env

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

Echo =============================================================================================================
:connexion
Echo Connexion a RTC  : %date% - %time%
%repscm%\scm login -r "%refrtc%" -n "connexionrtc" -u "%userrtc%" -P "%pswrtc%" 
Echo Code retour : %ERRORLEVEL%

:liste_cs 
Echo =============================================================================================================

Echo liste des cs du wi %WI_DT% par commande scm
%repscm%\scm ls cs -W %WI_DT% -r "connexionrtc" 

Echo liste des cs du wi boucle
for /f "skip=2 tokens=1" %%a in ('%repscm%\scm ls cs -W %WI_DT% -r "connexionrtc"') do (
call :cs %%a
)

Echo Code retour : %ERRORLEVEL%

Echo =============================================================================================================
echo === Liste des entites promues
type %reptra%\list_promo_e_i.txt

echo === Arborescence WI DT
type %reptra%\list_promo_e_i_arborescence.txt

Echo Fin de traitement normale  : %date% - %time%
:Fin
Echo =============================================================================================================
Echo Date/Heure  de fin traitement  : %date%   %time%
Echo Traitement termine sans erreur

Echo *** Fin Script transfert stream dev E1 vers stream env E pm_list-ch-cs-wi.bat ********
EXIT /B 0

REM *******************************                Sous-fonctions             ***********************
:CONV_VAR_to_MAJ
FOR %%z IN (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0
:CONV_VAR_to_min
FOR %%z IN (a b c d e f g h i j k l m n o p q r s t u v w x y z) DO CALL set %~1=%%%~1:%%z=%%z%%
EXIT /B 0 


:cs
set cs=%~1
set numcs=%cs:~1,4%

echo -- Changeset : %numcs% >> %reptra%\list_promo_e_i_arborescence.txt

@echo mon numero de cs est : %numcs% et la liste des changes par commande scm :
%repscm%\scm ls ch -r "connexionrtc" %numcs%

%repscm%\scm ls ch -r "connexionrtc" %numcs% > %reptra%\list_ch.txt

echo liste des change du changeset %numcs% boucle sont : 
for /f  "skip=4 tokens=2,4" %%i in (%reptra%\list_ch.txt) do (
@echo %%i %%j
set var1=%%i
set var2=%%j
@echo var1 : !var1! var2 : !var2!
set car=!var1:~0,1!
@echo car : !car!
set ent=!var2:~-10!
@echo ent : !ent!
if !car! == ( (echo !ent! >> %reptra%\list_promo_e_i.txt
echo ------- Change : !var1! Fichier : !ent! >> %reptra%\list_promo_e_i_arborescence.txt
)
)

exit /B 0

endlocal



