Build
=====

JBoss7 EJB Package
------------------

* Preferences: mvn install -P prefs
* LDAP: mvn install -Pldap -D ldap

Standalone Application
----------------------
* mvn install -P tool

Library
-------

* Preferences: mvn install -P lib-prefs
* LDAP: mvn install -Pldap -D lib-ldap

JBoss Configuration
===================

For all audit log messages to appear in a separate log file (e.g. dcm4chee-proxy-audit.log), add to the according container configuration (e.g. standalone.xml):

```xml
<profile>
  .
  .
  <subsystem xmlns="urn:jboss:domain:logging:1.1">
    .
    .
    <periodic-rotating-file-handler name="PROXYAUDITLOG">
      <formatter>
        <pattern-formatter pattern="%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%E%n"/>
      </formatter>
      <file relative-to="jboss.server.log.dir" path="dcm4chee-proxy-audit.log"/>
      <suffix value=".yyyy-MM-dd"/>
      <append value="true"/>
    </periodic-rotating-file-handler>
    <logger category="org.dcm4chee.proxy.net.AuditLog">
      <level name="INFO"/>
      <handlers>
        <handler name="PROXYAUDITLOG"/>
      </handlers>
    </logger>
    .
    .
  <subsystem>
  .
  .
</profile>
```
