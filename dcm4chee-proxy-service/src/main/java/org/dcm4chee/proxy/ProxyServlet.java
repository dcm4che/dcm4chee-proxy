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

package org.dcm4chee.proxy;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Properties;

import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che.net.Device;
import org.dcm4che.util.SafeClose;
import org.dcm4che.util.StringUtils;
import org.dcm4chee.proxy.conf.ldap.LdapProxyConfigurationExtension;
import org.dcm4chee.proxy.conf.prefs.PreferencesProxyConfigurationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
@Path("servlet")
/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyServlet extends HttpServlet {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProxyServlet.class);

    private ObjectInstance mbean;
    private DicomConfiguration dicomConfig;
    private HL7Configuration hl7Config;
    private Proxy proxy;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
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
                LdapDicomConfiguration ldapConfig = new LdapDicomConfiguration(p);
                LdapHL7Configuration hl7Config = new LdapHL7Configuration();
                ldapConfig.addDicomConfigurationExtension(hl7Config);
                LdapProxyConfigurationExtension proxyConfig = new LdapProxyConfigurationExtension();
                ldapConfig.addDicomConfigurationExtension(proxyConfig);
                hl7Config.addHL7ConfigurationExtension(proxyConfig);
                // ldapConfig.addDicomConfigurationExtension(
                // new LdapAuditLoggerConfiguration());
                // ldapConfig.addDicomConfigurationExtension(
                // new LdapAuditRecordRepositoryConfiguration());
                dicomConfig = ldapConfig;
                this.hl7Config = hl7Config;
            } catch (FileNotFoundException e) {
                LOG.info("Could not find " + ldapPropertiesURL + " - use Java Preferences as Configuration Backend");
                PreferencesDicomConfiguration prefsConfig = new PreferencesDicomConfiguration();
                PreferencesHL7Configuration hl7Config = new PreferencesHL7Configuration();
                prefsConfig.addDicomConfigurationExtension(hl7Config);
                PreferencesProxyConfigurationExtension proxyConfig = new PreferencesProxyConfigurationExtension();
                prefsConfig.addDicomConfigurationExtension(proxyConfig);
                hl7Config.addHL7ConfigurationExtension(proxyConfig);
                // prefsConfig.addDicomConfigurationExtension(
                // new PreferencesAuditLoggerConfiguration());
                // prefsConfig.addDicomConfigurationExtension(
                // new PreferencesAuditRecordRepositoryConfiguration());
                dicomConfig = prefsConfig;
                this.hl7Config = hl7Config;
            } finally {
                SafeClose.close(ldapConf);
            }
            Device device = dicomConfig.findDevice(deviceName);
            //TODO: add negotiate and onClose handler
            proxy = new Proxy(dicomConfig, hl7Config, device);
            proxy.start();
            mbean = ManagementFactory.getPlatformMBeanServer().registerMBean(proxy, new ObjectName(jmxName));
        } catch (Exception e) {
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

    @POST
    @Path("/reload/{name}")
    public void reloadConfiguration(@PathParam("name") String name) {
        try {
            ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("dcm4che:service=" + name),
                    "reloadConfiguration", null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
