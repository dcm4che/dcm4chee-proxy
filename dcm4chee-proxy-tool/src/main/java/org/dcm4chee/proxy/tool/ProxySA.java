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
 * Java(TM), hosted at https://github.com/dcm4che.
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

import java.sql.DriverManager;
import java.util.ResourceBundle;

import javax.naming.NamingException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che.conf.ldap.LdapEnv;
import org.dcm4che.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.PreferencesDicomConfigurationExtension;
import org.dcm4che.conf.prefs.audit.PreferencesAuditLoggerConfiguration;
import org.dcm4che.conf.prefs.audit.PreferencesAuditRecordRepositoryConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4chee.proxy.Proxy;
import org.dcm4chee.proxy.conf.ldap.LdapProxyConfigurationExtension;
import org.dcm4chee.proxy.conf.prefs.PreferencesProxyConfigurationExtension;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxySA {

    private static ResourceBundle rb = ResourceBundle.getBundle("org.dcm4chee.proxy.tool.messages");

    public static void main(String[] args) {
        try {
            CommandLine cl = parseComandLine(args);
            if (!cl.hasOption("device")) {
                System.err.println("Missing device name.");
                System.err.println(rb.getString("try"));
                System.exit(2);
            }
            HL7Configuration hl7Config = (useLdapConfiguration(cl)) 
                    ? new LdapHL7Configuration()
                    : new PreferencesHL7Configuration();
            DicomConfiguration dicomConfig = configureDicomConfiguration(cl, hl7Config);
            String deviceName = cl.getOptionValue("device");
            Proxy proxy = new Proxy(dicomConfig, hl7Config, deviceName);
            proxy.start();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static DicomConfiguration configureDicomConfiguration(CommandLine cl, HL7Configuration hl7Config)
            throws NamingException, ConfigurationException {
        if (useLdapConfiguration(cl)) {
            LdapEnv env = new LdapEnv();
            env.setUrl(cl.getOptionValue("ldap-url"));
            env.setUserDN(cl.getOptionValue("ldap-userDN"));
            env.setPassword(cl.getOptionValue("ldap-pwd"));
            return newLdapProxyConfiguration(hl7Config);
        } else if (cl.hasOption("jdbc-backend-url")) {
            if (!DriverManager.getDrivers().hasMoreElements())
                throw new RuntimeException("No jdbc driver in classpath.");

            System.setProperty("java.util.prefs.PreferencesFactory",
                    "org.dcm4che.jdbc.prefs.PreferencesFactoryJDBCImpl");
            System.setProperty("jdbc.prefs.datasource", cl.getOptionValue("jdbc-backend-url"));
            System.setProperty("jdbc.prefs.connection.username", cl.getOptionValue("jdbc-user-name"));
            System.setProperty("jdbc.prefs.connection.password", cl.getOptionValue("jdbc-user-pwd"));
        }
        return newPreferencesProxyConfiguration(hl7Config);
    }

    private static DicomConfiguration newLdapProxyConfiguration(HL7Configuration hl7Config)
            throws ConfigurationException {
        LdapDicomConfiguration config = new LdapDicomConfiguration();
        config.addDicomConfigurationExtension(new LdapHL7Configuration());
        config.addDicomConfigurationExtension(new LdapProxyConfigurationExtension());
        config.addDicomConfigurationExtension(new LdapAuditLoggerConfiguration());
        config.addDicomConfigurationExtension(new LdapAuditRecordRepositoryConfiguration());
        config.addDicomConfigurationExtension((LdapDicomConfigurationExtension) hl7Config);
        return config;
    }

    private static DicomConfiguration newPreferencesProxyConfiguration(HL7Configuration hl7Config) {
        PreferencesDicomConfiguration config = new PreferencesDicomConfiguration();
        config.addDicomConfigurationExtension(new PreferencesHL7Configuration());
        config.addDicomConfigurationExtension(new PreferencesProxyConfigurationExtension());
        config.addDicomConfigurationExtension(new PreferencesAuditLoggerConfiguration());
        config.addDicomConfigurationExtension(new PreferencesAuditRecordRepositoryConfiguration());
        config.addDicomConfigurationExtension((PreferencesDicomConfigurationExtension) hl7Config);
        return config;
    }

    private static boolean useLdapConfiguration(CommandLine cl) {
        return cl.hasOption("ldap-url") && cl.hasOption("ldap-userDN") && cl.hasOption("ldap-pwd");
    }

    private static CommandLine parseComandLine(String[] args) throws ParseException {
        Options opts = new Options();
        addCommonOptions(opts);
        addDicomConfig(opts);
        addTLSOptions(opts);
        addLDAPOptions(opts);
        addAuditLogOptions(opts);
        addOracleOptions(opts);
        return parseComandLine(args, opts, rb, ProxySA.class);
    }

    @SuppressWarnings("static-access")
    private static void addOracleOptions(Options opts) {
        opts.addOption(OptionBuilder.hasArg().withArgName("url").withDescription(rb.getString("jdbc-backend-url"))
                .withLongOpt("jdbc-backend-url").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("name").withDescription(rb.getString("jdbc-user-name"))
                .withLongOpt("jdbc-user-name").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("password").withDescription(rb.getString("jdbc-user-pwd"))
                .withLongOpt("jdbc-user-pwd").create(null));
    }

    @SuppressWarnings("static-access")
    private static void addDicomConfig(Options opts) {
        opts.addOption(OptionBuilder.hasArg().withArgName("name").withDescription(rb.getString("device"))
                .withLongOpt("device").create(null));
    }

    @SuppressWarnings("static-access")
    private static void addLDAPOptions(Options opts) {
        opts.addOption(OptionBuilder.hasArg().withArgName("url").withDescription(rb.getString("ldap-url"))
                .withLongOpt("ldap-url").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("dn").withDescription(rb.getString("ldap-userDN"))
                .withLongOpt("ldap-userDN").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("password").withDescription(rb.getString("ldap-pwd"))
                .withLongOpt("ldap-pwd").create(null));
    }

    @SuppressWarnings("static-access")
    private static void addTLSOptions(Options opts) {
        opts.addOption(OptionBuilder.hasArg().withArgName("file|url").withDescription(rb.getString("key-store"))
                .withLongOpt("key-store").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("storetype").withDescription(rb.getString("key-store-type"))
                .withLongOpt("key-store-type").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("password").withDescription(rb.getString("key-store-pwd"))
                .withLongOpt("key-store-pwd").create(null));
        opts.addOption(OptionBuilder.hasArg().withArgName("password").withDescription(rb.getString("key-pwd"))
                .withLongOpt("key-pwd").create(null));
    }

    @SuppressWarnings("static-access")
    private static void addAuditLogOptions(Options opts) {
        opts.addOption(OptionBuilder.hasArg().withArgName("integer").withDescription(rb.getString("log-interval"))
                .withLongOpt("log-interval").create(null));
    }

    public static void addCommonOptions(Options opts) {
        opts.addOption("h", "help", false, rb.getString("help"));
        opts.addOption("V", "version", false, rb.getString("version"));
    }

    public static CommandLine parseComandLine(String[] args, Options opts, ResourceBundle rb2, Class<?> clazz)
            throws ParseException {
        CommandLineParser parser = new PosixParser();
        CommandLine cl = parser.parse(opts, args);
        if (cl.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(rb2.getString("usage"), rb2.getString("description"), opts, rb2.getString("example"));
            System.exit(0);
        }
        if (cl.hasOption("V")) {
            Package p = clazz.getPackage();
            String s = p.getName();
            System.out.println(s.substring(s.lastIndexOf('.') + 1) + ": " + p.getImplementationVersion());
            System.exit(0);
        }
        return cl;
    }

}
