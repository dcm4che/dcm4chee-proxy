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

package org.dcm4chee.proxy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.ldap.LdapDicomConfiguration;
import org.dcm4che3.conf.ldap.audit.LdapAuditLoggerConfiguration;
import org.dcm4che3.conf.ldap.audit.LdapAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che3.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che3.conf.prefs.audit.PreferencesAuditLoggerConfiguration;
import org.dcm4che3.conf.prefs.audit.PreferencesAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.prefs.cdi.PrefsFactory;
import org.dcm4che3.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.conf.ldap.LdapProxyConfigurationExtension;
import org.dcm4chee.proxy.conf.prefs.PreferencesProxyConfigurationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@SuppressWarnings("serial")
public class ProxyServlet extends HttpServlet {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServlet.class);

    private ObjectInstance mbean;
    private DicomConfiguration dicomConfig;
    private HL7Configuration hl7Config;
    private Proxy proxy;

    private static String[] JBOSS_PROPERITIES = {
        "jboss.home",
        "jboss.modules",
        "jboss.server.base",
        "jboss.server.config",
        "jboss.server.data",
        "jboss.server.deploy",
        "jboss.server.log",
        "jboss.server.temp",
    };
    
    private static void addJBossDirURLSystemProperties() {
        for (String key : JBOSS_PROPERITIES) {
            String url = new File(System.getProperty(key + ".dir")).toURI().toString();
            System.setProperty(key + ".url", url.substring(0, url.length() - 1));
        }
    }

    /**
* Allows for custom Preferences implementations to be used, like jdbc-prefs
*/
    @Inject
    Instance<PrefsFactory> prefsFactoryInstance;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            addJBossDirURLSystemProperties();
            String ldapPropertiesURL = StringUtils.replaceSystemProperties(
                    System.getProperty("org.dcm4chee.proxy.ldapPropertiesURL",
                            config.getInitParameter("ldapPropertiesURL"))).replace('\\', '/');
            String deviceName = System.getProperty("org.dcm4chee.proxy.deviceName",
                    config.getInitParameter("deviceName"));
            String jmxName = System.getProperty("org.dcm4chee.proxy.jmxName", config.getInitParameter("jmxName"));
            InputStream ldapConf = null;
            try {
                ldapConf = new URL(ldapPropertiesURL).openStream();
                Properties p = new Properties();
                p.load(ldapConf);
                LOG.info("Using LDAP Configuration Backend");
                LdapDicomConfiguration ldapConfig = new LdapDicomConfiguration(p);
                LdapHL7Configuration hl7Conf = new LdapHL7Configuration();
                ldapConfig.addDicomConfigurationExtension(hl7Conf);
                ldapConfig.addDicomConfigurationExtension(new LdapProxyConfigurationExtension());
                ldapConfig.addDicomConfigurationExtension(new LdapAuditLoggerConfiguration());
                ldapConfig.addDicomConfigurationExtension(new LdapAuditRecordRepositoryConfiguration());
                dicomConfig = ldapConfig;
                this.hl7Config = hl7Conf;
            } catch (FileNotFoundException e) {
                // check if there is an implementation of PrefsFactory provided and construct DicomConfiguration accordingly
                PreferencesDicomConfiguration prefsConfig;
                if (!prefsFactoryInstance.isUnsatisfied()) {
                    Preferences prefs = prefsFactoryInstance.get().getPreferences();
                    LOG.info("Using custom Preferences implementation {}", prefs.getClass().toString());
                    prefsConfig = new PreferencesDicomConfiguration(prefs);
                } else {
                    prefsConfig = new PreferencesDicomConfiguration();
                    LOG.info("Using default Preferences implementation {}", prefsConfig.getRootPrefs().getClass().toString());
                }
                
                PreferencesHL7Configuration hl7Conf = new PreferencesHL7Configuration();
                prefsConfig.addDicomConfigurationExtension(hl7Conf);
                prefsConfig.addDicomConfigurationExtension(new PreferencesProxyConfigurationExtension());
                prefsConfig.addDicomConfigurationExtension(new PreferencesAuditLoggerConfiguration());
                prefsConfig.addDicomConfigurationExtension(new PreferencesAuditRecordRepositoryConfiguration());
                dicomConfig = prefsConfig;
                this.hl7Config = hl7Conf;
            } finally {
                SafeClose.close(ldapConf);
            }
            proxy = new Proxy(dicomConfig, hl7Config, deviceName);
            proxy.start();
            ProxyDeviceExtension proxyDev = proxy.getDevice().getDeviceExtension(ProxyDeviceExtension.class);
            mbean = ManagementFactory.getPlatformMBeanServer().registerMBean(proxy, new ObjectName(jmxName));
        } catch (Exception e) {
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            destroy();
            throw new ServletException(e);
        }
    }

    @Override
    public void destroy() {
        if (mbean != null)
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbean.getObjectName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        if (proxy != null)
            proxy.stop();
        if (dicomConfig != null)
            dicomConfig.close();
    }

}
