pour creer un workitem rtc avec curl il faut d abord recuperer l id interne du pa avec l url suivante : 


https://rtc-dev.appli:9443/ccm/oslc/workitems/catalog



-<oslc_disc:entry>

-<oslc_disc:ServiceProvider>

<dc:title>PA_DEV_APP</dc:title>

<oslc_disc:details rdf:resource="https://rtc-dev.appli:9443/ccm/process/project-areas/_fNE3QAXfEeWWeuXawK3zmQ"/>

<oslc_disc:services rdf:resource="https://rtc-dev.appli:9443/ccm/oslc/contexts/_fNE3QAXfEeWWeuXawK3zmQ/workitems/services.xml"/>

<jfs_proc:consumerRegistry rdf:resource="https://rtc-dev.appli:9443/ccm/process/project-areas/_fNE3QAXfEeWWeuXawK3zmQ/links"/>

</oslc_disc:ServiceProvider>

</oslc_disc:entry>


on voit que pour le PA PA_DEV_APP l id interne est _fNE3QAXfEeWWeuXawK3zmQ



Liste des work items du PA :
____________________________
https://rtc-dev:9443/ccm/oslc/contexts/_fNE3QAXfEeWWeuXawK3zmQ/workitems/


Services d'un PA pour les wi :
_____________________________
https://rtc-dev:9443/ccm/oslc/contexts/_fNE3QAXfEeWWeuXawK3zmQ/workitems/services.xml



Creation d un wi task :
_______________________
curl.exe -k -b %COOKIES% -H "Content-Type: application/x-oslc-cm-change-request+xml" -H "Accept: text/xml" -X POST -d @newtask.xml https://rtc-dev:9443/ccm/oslc/contexts/_fNE3QAXfEeWWeuXawK3zmQ/workitems/task



avec le fichier newtask.xml comme ci-dessous : 
<?xml version="1.0" encoding="UTF-8"?><oslc_cm:ChangeRequest xmlns:oslc_cm="http://open-services.net/xmlns/cm/1.0/" rdf:about="https://rtc-dev:9443/ccm/resource/itemName/com.ibm.team.workitem.WorkItem/207" xmlns:calm="http://jazz.net/xmlns/prod/jazz/calm/1.0/" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:dc="http://purl.org/dc/terms/" xmlns:oslc_pl="http://open-services.net/ns/pl#" xmlns:rtc_cm="http://jazz.net/xmlns/prod/jazz/rtc/cm/1.0/">
<rtc_cm:ownedBy rdf:resource="https://rtc-dev.appli:9443/jts/users/phmo"/>
<dc:description>ma description de tache phmo new</dc:description>
<dc:title>ma tache phmo new</dc:title>
<oslc_cm:severity rdf:resource="https://rtc-dev:9443/ccm/oslc/enumerations/_fNE3QAXfEeWWeuXawK3zmQ/severity/severity.literal.l3"/>
<dc:type rdf:resource="https://rtc-dev:9443/ccm/oslc/types/_fNE3QAXfEeWWeuXawK3zmQ/task"/>
</oslc_cm:ChangeRequest>








