Introduction
============

The dcm4chee-proxy is a DICOM proxy application, that provides a vehicle for 
rule based distribution of DICOM data accross a network. Example use-cases are
i) forwarding of DICOM objects to multiple AETs, ii) forwarding of DICOM objects
based on receive time schedules, iii) forwarding to target AETs based on send time schedule,
iv) forwarding based on attribute evaluation, etc.

The application can be run on JBoss AS7 or as a standalone command line tool.

Tracker: http://www.dcm4che.org/jira/browse/PRX 

Source: https://github.com/dcm4che/dcm4chee-proxy 

Building
========

dcm4che library
---------------

Before building the proxy, check out and build the [dcm4che-3.x DICOM Toolkit] (http://github.com/dcm4che/dcm4che).

dcm4chee-proxy
--------------

To build the dcm4chee-proxy, run `mvn clean install -P standard` in the root directory.
On success, a JBoss AS7 deployable file can be found in dcm4chee-proxy-war/target/dcm4chee-proxy-war-<version>.war
and the command line proxy-version can be found in dcm4chee-proxy-tool/target/dcm4chee-proxy-tool-<version>.zip.
The standard build includes configuration support for LDAP and Java Preferences.

To build the proxy with a dependency for use with dcm4che-jdbc-prefs, run `mvn clean install -P jdbc-prefs` or `mvn clean install` (default profile is `jdbc-prefs`).

Configuration
=============

LDAP Schema Import
------------------

In order to store configuration data for dcm4chee-proxy, new schema files
have to be imported into the LDAP server instance.
The folder `/dcm4chee-proxy/dcm4chee-proxy-conf/src/main/config/ldap` contains
subfolders with the required schema files for the supported LDAP servers:
```
./apacheds
./apacheds/partition-nodomain.ldif
./apacheds/dcm4chee-proxy.ldif
./opendj
./opendj/12-dcm4chee-proxy.ldif
./slapd
./slapd/dcm4chee-proxy.ldif
```

Sample Config
-------------

dcm4chee-proxy provides a sample configuration for LDAP and Java Preferences.

**LDAP**

The LDAP sample configuration can be found at `/dcm4chee-proxy/dcm4chee-proxy-conf/src/main/config/ldap`:
```
./init.ldif
./init-config.ldif
./sample-config.ldif
```
After importing the LDAP specific schema file (see step *LDAP Schema Import*), 
import the ldif files in the above order into the LDAP server.

**Java Preferences**

A Java Preferences sample configuration can be found at `dcm4chee-proxy/dcm4chee-proxy-conf/src/main/config/prefs`:
```
./sample-config.xml
```
To import the sample config, use the `xml2prefs` tool provided by the dcm4che 3.0 library (https://github.com/dcm4che/dcm4che).

*Note:* If planned to use the SQL backend for storing configuration data, the dcm4che-jdbc-prefs project 
provides a tool `xmlPrefs2jdbc` for importing the Java Preferences sample configuration.
Please check the dcm4che-jdbc-prefs project for further information.

JBoss Setup
-----------

**Dependencies**

To run dcm4chee-proxy within JBoss AS7 requires dcm4che-jboss-modules to be installed,
which can be found in the dcm4che-3.x DICOM Toolkit (https://github.com/dcm4che/dcm4che).
Unpack `dcm4che-jboss-modules-<version>.zip` into the JBoss AS7 folder.

**Container Configuration**

Create a directory `dcm4chee-proxy` inside the container configuration directory 
(e.g. `<jbossDir>/standalone/configuration/dcm4chee-proxy`)
and copy all files from `dcm4chee-proxy-conf/src/main/config/conf/` into it.

If planned to use Java Preferences as configuration backend, delete the file
`ldap.properties` from `<jbossDir>/standalone/configuration/dcm4chee-proxy/`.

If planned to use a LDAP configuration backend, edit the file
`<jbossDir>/standalone/configuration/dcm4chee-proxy/ldap.properties`
and set the connection and authentication parameters according
to the LDAP server configuration.

**Deployment**

To run dcm4chee-proxy in a JBoss AS7 instance, deploy
`dcm4chee-proxy/dcm4chee-proxy-war/target/dcm4chee-proxy-war-<version>.war`
via the JBoss command line interface or by copying it into e.g. `<jbossDir>/standalone/deployments/`.

*Example:* 

i) make sure the JBoss instance is running

ii) start the command line interface: `<jbossDir>/bin/jboss-cli.sh -c`

iii) call the deploy procedure: `deploy <buildPath>/dcm4chee-proxy-war-<version>.war`

**Device Configuration**

The dcm4chee-proxy is using a LDAP configuration,
compliant to the *DICOM Application Configuration Management Profile*,
specified in [DICOM 2011, Part 15][1], Annex H.

[1]: ftp://medical.nema.org/medical/dicom/2011/11_15pu.pdf

On start-up, the dcm4chee-proxy application needs to load a proxy device configuration
from the configuration backend. The device to be loaded can be set via  

i) JBoss AS7 system property `org.dcm4chee.proxy.deviceName` in the JBoss container configuration 

*Example:* Edit `<jbossDir>standalone/configuration/standalone.xml`:
```xml
<?xml version='1.0' encoding='UTF-8'?>

<server xmlns="urn:jboss:domain:1.2">
    <extensions>
    ...
    </extensions>
    <system-properties>
        <property name="org.dcm4chee.proxy.deviceName" value="dcm4chee-proxy"/>
    </system-properties>
    ...
</server>
```
or

ii) by editing the file `/WEB-INF/web.xml` within the war file

*Example:*
```xml
<?xml version="1.0" encoding="UTF-8"?>
<webapp...>
  ...
  <servlet>
    ...
    <init-param>
      <param-name>deviceName</param-name>
      <param-value>dcm4chee-proxy</param-value>
    </init-param>
    ...
  </servlet>
</web-app>
```

**JBoss Logging**

For all audit log messages to appear in a separate log file (e.g. dcm4chee-proxy-audit.log), 
add to the according container configuration (e.g. standalone.xml):

```xml
<profile>
  ...
  <subsystem xmlns="urn:jboss:domain:logging:1.1">
    ...
    <periodic-rotating-file-handler name="PROXYAUDITLOG">
      <formatter>
        <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>
      </formatter>
      <file relative-to="jboss.server.log.dir" path="dcm4chee-proxy-audit.log"/>
      <suffix value=".yyyy-MM-dd"/>
      <append value="true"/>
    </periodic-rotating-file-handler>
    <logger category="org.dcm4chee.proxy.conf.AuditLog">
      <level name="INFO"/>
      <handlers>
        <handler name="PROXYAUDITLOG"/>
      </handlers>
    </logger>
    ...
  <subsystem>
  ...
</profile>
```

**JDBC Preferences**

If the preferences data is supposed to be read via the dcm4che-jdbc-prefs project
from a database, specify the following system property in the container configuration (e.g. standalone.xml):
```xml
<system-properties>
    <property name="java.util.prefs.PreferencesFactory" value="org.dcm4che.jdbc.prefs.PreferencesFactoryImpl"/>
</system-properties>
```

Standalone Application
----------------------
The dcm4chee-proxy can be run as a standlone application from the command line.
After building the project, the command line version can be found in
`dcm4chee-proxy-tool/target/dcm4chee-proxy-tool-1.1.0-SNAPSHOT-bin.zip`.
The standalone app can be started by executing `./bin/proxysa` (or `proxysa.bat`)
and specifying the device name to be loaded from the configuration backend (LDAP or Java Preferences).

*Example:*
```
proxysa --device dcm4chee-proxy 
--ldap-url ldap://localhost:1389/dc=example,dc=com 
--ldap-userDN "<userDN>" --ldap-pwd <pwd>` 
```
This will start the proxy with a DICOM configuration
retrieved from the specified LDAP.

Try `proxysa --help` for more information.
