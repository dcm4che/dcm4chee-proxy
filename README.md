JBoss Configuration
-------------------

For all audit log messages to appear in a separate log file, add to `jboss-logging.xml`:

    <periodic-rotating-file-handler
        file-name="${jboss.server.log.dir}/dcm4chee-proxy-audit.log"
        name="PROXYAUDITLOG"
        autoflush="true"
        append="true"
        suffix=".yyyy-MM-dd">  <!-- To roll over at the top of each hour, use ".yyyy-MM-dd-HH" instead -->

        <error-manager>
            <only-once/>
        </error-manager>

        <formatter>
            <!-- To revert back to simple stack traces without JAR versions, change "%E" to "%e" below. -->
            <!-- Uncomment this to get the class name in the log as well as the category
            <pattern-formatter pattern="%d %-5p [%c] %C{1} (%t) %s%E%n"/>
            -->
            <!-- Uncomment this to log without the class name in the log -->
            <pattern-formatter pattern="%d %-5p [%c] (%t) %s%E%n"/>
        </formatter>
    </periodic-rotating-file-handler>

    <logger category="org.dcm4chee.proxy.mc.net.AuditLog" use-parent-handlers="false">
        <level name="INFO"/>
        <handlers>
            <handler-ref name="PROXYAUDITLOG"/>
        </handlers>
    </logger>
