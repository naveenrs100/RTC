rem Login 
set USER=Admin_CC
set PWD=admc0504
rem File which will contain the login cookies
set COOKIES=cookies.txt
 
rem First pass to request the authentication cookie
curl.exe -k -c %COOKIES% https://rtc-dev:9443/ccm/authenticated/identity
rem Second pass to login
curl.exe -k -L -b %COOKIES% -c %COOKIES% -d j_username=%USER% -d j_password=%PWD% https://rtc-dev:9443/ccm/authenticated/j_security_check
 
rem Request the url using the authentication
curl.exe -k -b %COOKIES% https://rtc-dev:9443/ccm/oslc/workitems/catalog
