echo off
rem Login 
set USER=Admin_CC
set PWD=admc0504
rem File which will contain the login cookies
set COOKIES=cookies.txt
 
Echo First pass to request the authentication cookie
curl.exe -k -c %COOKIES% https://rtc-dev:9443/ccm/authenticated/identity
Echo Second pass to login
curl.exe -k -L -b %COOKIES% -c %COOKIES% -d j_username=%USER% -d j_password=%PWD% https://rtc-dev:9443/ccm/authenticated/j_security_check
 
 
Echo creat wi ====================================
curl.exe -k -b %COOKIES% -H "Content-Type: application/x-oslc-cm-change-request+xml" -H "Accept: text/xml" -X POST -d @newtask.xml https://rtc-dev:9443/ccm/oslc/contexts/_fNE3QAXfEeWWeuXawK3zmQ/workitems/task


