/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.tool;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.ldap.LdapEnv;
import org.dcm4che.net.SSLManagerFactory;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.proxy.conf.AuditLog;
import org.dcm4chee.proxy.conf.ProxyDevice;
import org.dcm4chee.proxy.conf.Scheduler;
import org.dcm4chee.proxy.conf.ldap.LdapProxyConfiguration;
import org.dcm4chee.proxy.conf.prefs.PreferencesProxyConfiguration;
import org.dcm4chee.proxy.service.CEcho;
import org.dcm4chee.proxy.service.CFind;
import org.dcm4chee.proxy.service.CGet;
import org.dcm4chee.proxy.service.CMove;
import org.dcm4chee.proxy.service.CStore;
import org.dcm4chee.proxy.service.Mpps;
import org.dcm4chee.proxy.service.StgCmt;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxySA {
    
    private static ResourceBundle rb =
        ResourceBundle.getBundle("org.dcm4chee.proxy.tool.messages");
    
    public static void main(String[] args) {
        
        try {
            CommandLine cl = parseComandLine(args);
            DicomConfiguration dicomConfig = configureDicomConfiguration(cl);
            ProxyDevice proxyDevice = 
                (ProxyDevice) dicomConfig.findDevice(cl.getOptionValue("device"));
            ExecutorService executorService = Executors.newCachedThreadPool();
            proxyDevice.setExecutor(executorService);
            ScheduledExecutorService scheduledExecutorService = 
                Executors.newSingleThreadScheduledExecutor();
            proxyDevice.setScheduledExecutor(scheduledExecutorService);
            DicomServiceRegistry dcmService = new DicomServiceRegistry();
            dcmService.addDicomService(new CEcho());
            dcmService.addDicomService(new CStore("*"));
            dcmService.addDicomService(new StgCmt());
            dcmService.addDicomService(new CFind("1.2.840.10008.5.1.4.1.2.1.1"));
            dcmService.addDicomService(new CFind("1.2.840.10008.5.1.4.1.2.2.1"));
            dcmService.addDicomService(new CFind("1.2.840.10008.5.1.4.1.2.3.1"));
            dcmService.addDicomService(new CFind("1.2.840.10008.5.1.4.31"));
            dcmService.addDicomService(new CGet("1.2.840.10008.5.1.4.1.2.1.3"));
            dcmService.addDicomService(new CGet("1.2.840.10008.5.1.4.1.2.2.3"));
            dcmService.addDicomService(new CGet("1.2.840.10008.5.1.4.1.2.3.3"));
            dcmService.addDicomService(new CMove("1.2.840.10008.5.1.4.1.2.1.2"));
            dcmService.addDicomService(new CMove("1.2.840.10008.5.1.4.1.2.2.2"));
            dcmService.addDicomService(new CMove("1.2.840.10008.5.1.4.1.2.3.2"));
            dcmService.addDicomService(new Mpps());
            proxyDevice.setDimseRQHandler(dcmService);
            configureKeyManager(cl, proxyDevice);
            configureScheduler(cl, proxyDevice);
            proxyDevice.bindConnections();
        } catch (ConfigurationNotFoundException c) {
            System.err.println("No device configuration found.");
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static DicomConfiguration configureDicomConfiguration(CommandLine cl)
            throws NamingException, ConfigurationException {
        if (!cl.hasOption("device")) {
            System.err.println("Missing device name.");
            System.err.println(rb.getString("try"));
            System.exit(2);
        }
        if (useLdapConfiguration(cl)) {
            LdapEnv env = new LdapEnv();
            env.setUrl(cl.getOptionValue("ldap-url"));
            env.setUserDN(cl.getOptionValue("ldap-userDN"));
            env.setPassword(cl.getOptionValue("ldap-pwd"));
            return new LdapProxyConfiguration(env);
        } else
            return (DicomConfiguration) new PreferencesProxyConfiguration(Preferences.userRoot());
    }

    private static boolean useLdapConfiguration(CommandLine cl) {
        return cl.hasOption("ldap-url") && cl.hasOption("ldap-userDN") && cl.hasOption("ldap-pwd")
                && cl.hasOption("ldap-domain");
    }
    
    private static void configureScheduler(CommandLine cl, ProxyDevice proxyDevice) {
        Scheduler scheduler = new Scheduler(proxyDevice, new AuditLog());
        if(cl.hasOption("log-interval"))
            proxyDevice.setSchedulerInterval(Integer.parseInt(cl.getOptionValue("log-interval")));
        scheduler.start();
    }

    private static void configureKeyManager(CommandLine cl, ProxyDevice proxyDevice) {
        try {
            KeyManager keyMgr;
            try {
                keyMgr = SSLManagerFactory.createKeyManager(
                        cl.getOptionValue("key-store-type", "JKS"), 
                        cl.getOptionValue("key-store", "resource:dcm4chee-proxy-key.jks"), 
                        cl.getOptionValue("key-store-pwd", "secret"), 
                        cl.getOptionValue("key-pwd", "secret"));
                proxyDevice.setKeyManager(keyMgr);
            } catch (UnrecoverableKeyException e) {
                System.err.println("Key for device not found in keystore.");
                System.exit(2);
            } catch (IOException e) {
                System.err.println("Failed to read key-store.");
                System.exit(2);
            }
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Requested cryptographic algorithm is not available.");
            System.exit(2);
        } catch (CertificateException e) {
            System.err.println("Certificate error.");
            System.exit(2);
        } catch (KeyStoreException e) {
            System.err.println("Key-store error.");
            System.exit(2);
        }
    }
    
    private static CommandLine parseComandLine(String[] args) throws ParseException {
        Options opts = new Options();
        addCommonOptions(opts);
        addDicomConfig(opts);
        addTLSOptions(opts);
        addLDAPOptions(opts);
        addAuditLogOptions(opts);
        return parseComandLine(args, opts, rb, ProxySA.class);
    }
    
    @SuppressWarnings("static-access")
    private static void addDicomConfig(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("name")
                .withDescription(rb.getString("device"))
                .withLongOpt("device")
                .create(null));
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
    
    @SuppressWarnings("static-access")
    private static void addAuditLogOptions(Options opts) {
        opts.addOption(OptionBuilder
                .hasArg()
                .withArgName("integer")
                .withDescription(rb.getString("log-interval"))
                .withLongOpt("log-interval")
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
