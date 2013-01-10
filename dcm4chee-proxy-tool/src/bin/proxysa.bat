@echo off
rem -------------------------------------------------------------------------
rem proxysa  Launcher
rem -------------------------------------------------------------------------

if not "%ECHO%" == ""  echo %ECHO%
if "%OS%" == "Windows_NT"  setlocal

set MAIN_CLASS=org.dcm4chee.proxy.tool.ProxySA
set MAIN_JAR=dcm4chee-proxy-tool-1.0.0-SNAPSHOT.jar

set DCM4CHE_VERSION=3.0.1

set DIRNAME=.\
if "%OS%" == "Windows_NT" set DIRNAME=%~dp0%

rem Read all command line arguments

set ARGS=
:loop
if [%1] == [] goto end
        set ARGS=%ARGS% %1
        shift
        goto loop
:end

if not "%PROXY_HOME%" == "" goto HAVE_PROXY_HOME

set PROXY_HOME=%DIRNAME%..

:HAVE_PROXY_HOME

if not "%JAVA_HOME%" == "" goto HAVE_JAVA_HOME

set JAVA=java

goto SKIP_SET_JAVA_HOME

:HAVE_JAVA_HOME

set JAVA=%JAVA_HOME%\bin\java

:SKIP_SET_JAVA_HOME

set CP=%PROXY_HOME%\etc\
set CP=%CP%;%PROXY_HOME%\lib\%MAIN_JAR%
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-core-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-hl7-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-net-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-net-hl7-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-api-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-api-hl7-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-prefs-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-prefs-hl7-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-ldap-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-conf-ldap-hl7-%DCM4CHE_VERSION%.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4chee-proxy-conf-1.0.0-SNAPSHOT.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4chee-proxy-service-1.0.0-SNAPSHOT.jar
set CP=%CP%;%PROXY_HOME%\lib\dcm4che-jdbc-prefs-tool-1.0.0-SNAPSHOT.jar
set CP=%CP%;%PROXY_HOME%\lib\slf4j-api-1.6.4.jar
set CP=%CP%;%PROXY_HOME%\lib\slf4j-log4j12-1.6.4.jar
set CP=%CP%;%PROXY_HOME%\lib\log4j-1.2.16.jar
set CP=%CP%;%PROXY_HOME%\lib\commons-cli-1.2.jar
set CP=%CP%;%PROXY_HOME%\lib\hibernate-jpa-2.0-api-1.0.1.Final.jar
set CP=%CP%;%PROXY_HOME%\lib\hibernate-entitymanager-4.0.1.Final.jar
set CP=%CP%;%PROXY_HOME%\lib\hibernate-core-4.0.1.Final.jar
set CP=%CP%;%PROXY_HOME%\lib\jboss-logging-3.1.0.GA.jar
set CP=%CP%;%PROXY_HOME%\lib\jta-1.1.jar
set CP=%CP%;%PROXY_HOME%\lib\dom4j-1.6.1.jar
set CP=%CP%;%PROXY_HOME%\lib\hibernate-commons-annotations-4.0.1.Final.jar
set CP=%CP%;%PROXY_HOME%\lib\javassist-3.15.0-GA.jar
set CP=%CP%;%PROXY_HOME%\lib\commons-collections-3.2.1.jar
set CP=%CP%;%PROXY_HOME%\lib\antlr-2.7.7.jar
set CP=%CP%;%PROXY_HOME%\lib\ojdbc6.jar

"%JAVA%" %JAVA_OPTS% -cp "%CP%" %MAIN_CLASS% %ARGS%
