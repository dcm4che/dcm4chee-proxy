package org.dcm4chee.proxy.tool;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.prefs.Preferences;

import javax.naming.NamingException;
import javax.net.ssl.KeyManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.ldap.LdapEnv;
import org.dcm4che.net.SSLManagerFactory;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.proxy.mc.conf.ldap.LdapProxyConfiguration;
import org.dcm4chee.proxy.mc.conf.prefs.PreferencesProxyConfiguration;
import org.dcm4chee.proxy.mc.net.AuditLog;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.dcm4chee.proxy.mc.net.Scheduler;
import org.dcm4chee.proxy.mc.net.service.CEchoSCPImpl;
import org.dcm4chee.proxy.mc.net.service.CStoreSCPImpl;

public class ProxySA {
    
    private static ResourceBundle rb =
        ResourceBundle.getBundle("org.dcm4chee.proxy.tool.messages");

    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            DicomConfiguration dicomConfig = configureDicomConfiguration(cl);
            ProxyDevice proxyDevice = (ProxyDevice) dicomConfig.findDevice("dcm4chee-proxy");
            ExecutorService executorService = Executors.newCachedThreadPool();
            proxyDevice.setExecutor(executorService);
            ScheduledExecutorService scheduledExecutorService = 
                Executors.newSingleThreadScheduledExecutor();
            proxyDevice.setScheduledExecutor(scheduledExecutorService);
            DicomServiceRegistry dcmService = new DicomServiceRegistry();
            dcmService.addDicomService(new CEchoSCPImpl());
            dcmService.addDicomService(new CStoreSCPImpl("*"));
            proxyDevice.setDimseRQHandler(dcmService);
            configureKeyManager(cl, proxyDevice);
            configureScheduler(cl, proxyDevice);
            proxyDevice.activate();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static DicomConfiguration configureDicomConfiguration(CommandLine cl)
            throws NamingException, ConfigurationException {
        DicomConfiguration dicomConfig;
        if (cl.hasOption("ldap-url") && cl.hasOption("ldap-userDN") && cl.hasOption("ldap-pwd")
                && cl.hasOption("ldap-domain")) {
            LdapEnv env = new LdapEnv();
            env.setUrl(cl.getOptionValue("ldap-url"));
            env.setUserDN(cl.getOptionValue("ldap-userDN"));
            env.setPassword(cl.getOptionValue("ldap-pwd"));
            dicomConfig = new LdapProxyConfiguration(env, cl.getOptionValue("ldap-domain"));
        } else
            dicomConfig =
                    (DicomConfiguration) new PreferencesProxyConfiguration(Preferences.userRoot());
        if (!dicomConfig.configurationExists()) {
            System.err.println("Error in DICOM configuration.");
            System.err.println(rb.getString("try"));
            System.exit(2);
        }
        return dicomConfig;
    }
    
    private static Scheduler configureScheduler(CommandLine cl, ProxyDevice proxyDevice) {
        AuditLog log = new AuditLog();
        if(cl.hasOption("audit-log-delay"))
            log.setDelay(Long.parseLong(cl.getOptionValue("audit-log-delay")));
        else
            log.setDelay(60);
        return new Scheduler(proxyDevice, log);
    }

    private static void configureKeyManager(CommandLine cl, ProxyDevice proxyDevice) {
        if(cl.hasOption("key-store")) {
            try {
                KeyManager keyMgr= SSLManagerFactory.createKeyManager(
                        cl.getOptionValue("key-store-type"), 
                        cl.getOptionValue("key-store"), 
                        cl.getOptionValue("key-store-pwd"), 
                        cl.getOptionValue("key-pwd"));
                proxyDevice.setKeyManager(keyMgr);
                proxyDevice.initTrustManager();
            } catch (Exception e) {
                System.err.println("Keystore error.");
                System.err.println(rb.getString("try"));
                System.exit(2);
            }
        }
    }
    
    private static CommandLine parseComandLine(String[] args) throws ParseException {
        Options opts = new Options();
        addCommonOptions(opts);
        addTLSOptions(opts);
        addLDAPOptions(opts);
        return parseComandLine(args, opts, rb, ProxySA.class);
    }
    
    @SuppressWarnings("static-access")
    private static void addLDAPOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("url")
                .withDescription(rb.getString("ldap-url"))
                .withLongOpt("ldap-url")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("dn")
                .withDescription(rb.getString("ldap-userDN"))
                .withLongOpt("ldap-userDN")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("password")
                .withDescription(rb.getString("ldap-pwd"))
                .withLongOpt("ldap-pwd")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("dc")
                .withDescription(rb.getString("ldap-domain"))
                .withLongOpt("ldap-domain")
                .create(null));
    }

    @SuppressWarnings("static-access")
    private static void addTLSOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("file|url")
                .withDescription(rb.getString("key-store"))
                .withLongOpt("key-store")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("storetype")
                .withDescription(rb.getString("key-store-type"))
                .withLongOpt("key-store-type")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("password")
                .withDescription(rb.getString("key-store-pwd"))
                .withLongOpt("key-store-pwd")
                .create(null));
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("password")
                .withDescription(rb.getString("key-pwd"))
                .withLongOpt("key-pwd")
                .create(null));
    }
    
    public static void addCommonOptions(Options opts) {
        opts.addOption("h", "help", false, rb.getString("help"));
        opts.addOption("V", "version", false, rb.getString("version"));
    }
    
    public static CommandLine parseComandLine(String[] args, Options opts, 
            ResourceBundle rb2, Class<?> clazz) throws ParseException {
        CommandLineParser parser = new PosixParser();
        CommandLine cl = parser.parse(opts, args);
        if (cl.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(
                    rb2.getString("usage"),
                    rb2.getString("description"), opts,
                    rb2.getString("example"));
            System.exit(0);
        }
        if (cl.hasOption("V")) {
            Package p = clazz.getPackage();
            String s = p.getName();
            System.out.println(s.substring(s.lastIndexOf('.')+1) + ": " +
                   p.getImplementationVersion());
            System.exit(0);
        }
        return cl;
    }

}
