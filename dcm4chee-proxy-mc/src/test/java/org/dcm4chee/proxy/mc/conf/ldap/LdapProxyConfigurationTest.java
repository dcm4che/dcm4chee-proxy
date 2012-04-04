/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contentsOfthis file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copyOfthe License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is partOfdcm4che, an implementationOfDICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial DeveloperOfthe Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contentsOfthis file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisionsOfthe GPL or the LGPL are applicable instead
 *Ofthose above. If you wish to allow useOfyour versionOfthis file only
 * under the termsOfeither the GPL or the LGPL, and not to allow others to
 * use your versionOfthis file under the termsOfthe MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your versionOfthis file under
 * the termsOfany oneOfthe MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.mc.conf.ldap;

import org.dcm4che.conf.ldap.LdapEnv;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapProxyConfigurationTest {

    private LdapProxyConfiguration config;
    
    @Before
    public void setUp() throws Exception {
        LdapEnv env = new LdapEnv();
        env.setUrl("ldap://localhost:389"); //slapd
        env.setUserDN("cn=admin,dc=nodomain"); //slapd
        env.setPassword("admin");
//        env.setUrl("ldap://localhost:1389"); //OpenDJ
//        env.setUserDN("cn=Directory Manager"); //OpenDJ
//        env.setPassword("admin"); //OpenDJ
//        env.setUrl("ldap://localhost:10389"); //ApchedDS
//        env.setUserDN("uid=admin,ou=system"); //ApacheDS
//        env.setPassword("secret"); //ApacheDS
        config = new LdapProxyConfiguration(env, "dc=nodomain");
//        config.setUserCertificate("userCertificate");
        ProxyConfigurationTestUtils.cleanUp(config);
    }

    @After
    public void tearDown() throws Exception {
        ProxyConfigurationTestUtils.cleanUp(config);
        config.close();
    }

    @Test
    public void testPersist() throws Exception {
        ProxyConfigurationTestUtils.testPersist(config);
    }
}
