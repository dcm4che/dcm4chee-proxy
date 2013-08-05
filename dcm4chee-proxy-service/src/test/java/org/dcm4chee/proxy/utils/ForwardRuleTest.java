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

package org.dcm4chee.proxy.utils;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import org.dcm4che.data.UID;
import org.dcm4che.net.Dimse;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.Schedule;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 * 
 */
public class ForwardRuleTest {

    /**
     * Test method for
     * {@link org.dcm4chee.proxy.utils.ForwardRuleUtils#filterForwardRulesByCallingAET(ProxyAEExtension, String)}
     * .
     */
    @Test
    public void testFilterForwardRulesByCallingAET() throws Exception {
        ProxyAEExtension proxyAEE = new ProxyAEExtension();
        List<ForwardRule> fwdRules = new ArrayList<>();

        ForwardRule fwdRule1 = new ForwardRule();
        fwdRule1.setCommonName("Rule1");
        List<String> dest1 = new ArrayList<String>(1);
        dest1.add("aet:DCM4CHEE");
        fwdRule1.setDestinationURIs(dest1);
        List<String> callingAETs1 = new ArrayList<>(1);
        callingAETs1.add("STORESCU");
        fwdRule1.setCallingAETs(callingAETs1);
        fwdRules.add(fwdRule1);

        ForwardRule fwdRule2 = new ForwardRule();
        fwdRule2.setCommonName("Rule2");
        List<String> dest2 = new ArrayList<String>(1);
        dest2.add("aet:STORESCP");
        fwdRule2.setDestinationURIs(dest2);
        fwdRules.add(fwdRule2);

        proxyAEE.setForwardRules(fwdRules);

        List<ForwardRule> result = ForwardRuleUtils.filterForwardRulesByCallingAET(proxyAEE, "STORESCU");
        assertRule1(result);

        result = ForwardRuleUtils.filterForwardRulesByCallingAET(proxyAEE, "OTHERSCU");
        assertRule2(result);

        fwdRules.remove(fwdRule1);
        Schedule rcvSchedule = new Schedule();
        rcvSchedule.setDays("Wed");
        rcvSchedule.setHours("15-18");
        fwdRule1.setReceiveSchedule(rcvSchedule);
        fwdRules.add(fwdRule1);
        proxyAEE.setForwardRules(fwdRules);

        boolean isNow = rcvSchedule.isNow(new GregorianCalendar());
        result = ForwardRuleUtils.filterForwardRulesByCallingAET(proxyAEE, "STORESCU");
        if (isNow)
            assertRule1(result);
        else
            assertRule2(result);
    }

    private void assertRule1(List<ForwardRule> result) {
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(1, result.get(0).getDestinationAETitles().size());
        Assert.assertEquals("DCM4CHEE", result.get(0).getDestinationAETitles().get(0));
    }

    private void assertRule2(List<ForwardRule> result) {
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(1, result.get(0).getDestinationAETitles().size());
        Assert.assertEquals("STORESCP", result.get(0).getDestinationAETitles().get(0));
    }

    @Test
    public void testFilterForwardRulesOnDimseRQ() {
        ProxyAEExtension proxyAEE = new ProxyAEExtension();
        List<ForwardRule> fwdRules = new ArrayList<>();

        ForwardRule fwdRule1 = new ForwardRule();
        fwdRule1.setCommonName("Rule1");
        List<String> dest1 = new ArrayList<String>(1);
        dest1.add("aet:DCM4CHEE");
        fwdRule1.setDestinationURIs(dest1);
        List<Dimse> dimse1 = new ArrayList<>(1);
        dimse1.add(Dimse.C_STORE_RQ);
        fwdRule1.setDimse(dimse1);
        fwdRules.add(fwdRule1);

        ForwardRule fwdRule2 = new ForwardRule();
        fwdRule2.setCommonName("Rule2");
        List<String> dest2 = new ArrayList<String>(1);
        dest2.add("aet:STORESCP");
        fwdRule2.setDestinationURIs(dest2);
        fwdRules.add(fwdRule2);

        proxyAEE.setForwardRules(fwdRules);

        List<ForwardRule> result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, null, Dimse.C_STORE_RQ);
        assertRule1(result);
        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, null, Dimse.C_FIND_RQ);
        assertRule2(result);

        fwdRules.remove(fwdRule1);
        fwdRule1.setDimse(new ArrayList<Dimse>(0));
        List<String> sopClasses = new ArrayList<>(1);
        sopClasses.add(UID.MRImageStorage);
        fwdRule1.setSopClasses(sopClasses);
        fwdRules.add(fwdRule1);
        proxyAEE.setForwardRules(fwdRules);

        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.MRImageStorage, null);
        assertRule1(result);
        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.CTImageStorage, null);
        assertRule2(result);

        fwdRules.remove(fwdRule2);
        List<Dimse> dimse2 = new ArrayList<Dimse>(1);
        dimse2.add(Dimse.C_STORE_RQ);
        fwdRule2.setDimse(dimse2);
        fwdRule2.setSopClasses(sopClasses);
        fwdRules.add(fwdRule2);
        proxyAEE.setForwardRules(fwdRules);

        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.MRImageStorage, null);
        assertRule1(result);
        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.MRImageStorage, Dimse.C_STORE_RQ);
        assertRule2(result);
        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.CTImageStorage, null);
        assertEmpty(result);
        result = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, UID.CTImageStorage, Dimse.C_STORE_RQ);
        assertEmpty(result);
    }

    private void assertEmpty(List<ForwardRule> result) {
        Assert.assertEquals(0, result.size());
    }
}
