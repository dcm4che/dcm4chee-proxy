Build
=====

* Preferences: `mvn install -P {prefs|prefs-jdbc}`
* LDAP: `mvn install -P ldap [-D ldap={slapd|opendj|apacheds}]`

Configuration
=============

Set device name in `web.xml` or via system property `proxy.device.name`.

JBoss Logging
-------------

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
    <logger category="org.dcm4chee.proxy.conf.AuditLog">
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
