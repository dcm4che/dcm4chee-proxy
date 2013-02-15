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

package org.dcm4chee.proxy.conf.ldap;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;

import org.dcm4che.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che.conf.ldap.LdapUtils;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ForwardRule.conversionType;
import org.dcm4chee.proxy.conf.ForwardSchedule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.conf.Retry;
import org.dcm4chee.proxy.conf.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapProxyConfigurationExtension extends LdapDicomConfigurationExtension {

    @Override
    protected void storeTo(Device device, Attributes attrs) {
        ProxyDeviceExtension proxyDev = device.getDeviceExtension(ProxyDeviceExtension.class);
        if (proxyDev == null)
            return;

        attrs.get("objectClass").add("dcmProxyDevice");
        LdapUtils.storeNotNull(attrs, "dcmSchedulerInterval", proxyDev.getSchedulerInterval());
        LdapUtils.storeNotNull(attrs, "dcmForwardThreads", proxyDev.getForwardThreads());
        LdapUtils.storeNotDef(attrs, "dcmProxyConfigurationStaleTimeout", proxyDev.getConfigurationStaleTimeout(), 0);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAEE == null)
            return;
        
        attrs.get("objectClass").add("dcmProxyNetworkAE");
        LdapUtils.storeNotNull(attrs, "dcmSpoolDirectory", proxyAEE.getSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmAcceptDataOnFailedAssociation", proxyAEE.isAcceptDataOnFailedAssociation());
        LdapUtils.storeNotNull(attrs, "dcmEnableAuditLog", proxyAEE.isEnableAuditLog());
        LdapUtils.storeNotNull(attrs, "hl7ProxyPIXConsumerApplication", proxyAEE.getProxyPIXConsumerApplication());
        LdapUtils.storeNotNull(attrs, "hl7RemotePIXManagerApplication", proxyAEE.getRemotePIXManagerApplication());
        LdapUtils.storeNotNull(attrs, "dcmDeleteFailedDataWithoutRetryConfiguration",
                proxyAEE.isDeleteFailedDataWithoutRetryConfiguration());
        LdapUtils.storeNotNull(attrs, "dcmDestinationAETitle", proxyAEE.getFallbackDestinationAET());
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmProxyDevice"))
            return;

        ProxyDeviceExtension proxyDev = new ProxyDeviceExtension();
        device.addDeviceExtension(proxyDev);
        proxyDev.setSchedulerInterval(LdapUtils.intValue(attrs.get("dcmSchedulerInterval"),
                ProxyDeviceExtension.DEFAULT_SCHEDULER_INTERVAL));
        proxyDev.setForwardThreads(LdapUtils.intValue(attrs.get("dcmForwardThreads"),
                ProxyDeviceExtension.DEFAULT_FORWARD_THREADS));
        proxyDev.setConfigurationStaleTimeout(LdapUtils.intValue(attrs.get("dcmProxyConfigurationStaleTimeout"), 0));
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmProxyNetworkAE"))
            return;

        ProxyAEExtension proxyAEE = new ProxyAEExtension();
        ae.addAEExtension(proxyAEE);
        proxyAEE.setSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmSpoolDirectory"), null));
        proxyAEE.setAcceptDataOnFailedAssociation(LdapUtils.booleanValue(attrs.get("dcmAcceptDataOnFailedAssociation"),
                Boolean.FALSE));
        proxyAEE.setEnableAuditLog(LdapUtils.booleanValue(attrs.get("dcmEnableAuditLog"), Boolean.FALSE));
        proxyAEE.setProxyPIXConsumerApplication(LdapUtils.stringValue(attrs.get("hl7ProxyPIXConsumerApplication"), null));
        proxyAEE.setRemotePIXManagerApplication(LdapUtils.stringValue(attrs.get("hl7RemotePIXManagerApplication"), null));
        proxyAEE.setDeleteFailedDataWithoutRetryConfiguration(LdapUtils.booleanValue(
                attrs.get("dcmDeleteFailedDataWithoutRetryConfiguration"), Boolean.FALSE));
        proxyAEE.setFallbackDestinationAET(LdapUtils.stringValue(attrs.get("dcmDestinationAETitle"), null));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAEE == null)
            return;

        loadRetries(proxyAEE, aeDN);
        loadForwardSchedules(proxyAEE, aeDN);
        loadForwardRules(proxyAEE, aeDN);
        config.load(proxyAEE.getAttributeCoercions(), aeDN);
    }

    private void loadForwardRules(ProxyAEExtension proxyAEE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(aeDN, "(objectclass=dcmForwardRule)");
        try {
            List<ForwardRule> rules = new ArrayList<ForwardRule>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ForwardRule rule = new ForwardRule();
                rule.setDimse(Arrays.asList(dimseArray(attrs.get("dcmForwardRuleDimse"))));
                rule.setSopClass(Arrays.asList(LdapUtils.stringArray(attrs.get("dcmSOPClass"))));
                rule.setCallingAET(LdapUtils.stringValue(attrs.get("dcmCallingAETitle"), null));
                rule.setDestinationURIs(Arrays.asList(LdapUtils.stringArray(attrs.get("labeledURI"))));
                rule.setUseCallingAET(LdapUtils.stringValue(attrs.get("dcmUseCallingAETitle"), null));
                rule.setExclusiveUseDefinedTC(LdapUtils.booleanValue(attrs.get("dcmExclusiveUseDefinedTC"),Boolean.FALSE));
                rule.setCommonName(LdapUtils.stringValue(attrs.get("cn"), null));
                Schedule schedule = new Schedule();
                schedule.setDays(LdapUtils.stringValue(attrs.get("dcmScheduleDays"), null));
                schedule.setHours(LdapUtils.stringValue(attrs.get("dcmScheduleHours"), null));
                rule.setReceiveSchedule(schedule);
                rule.setConversion(conversionTypeValue(attrs.get("dcmConversion")));
                rule.setConversionUri(LdapUtils.stringValue(attrs.get("dcmConversionUri"), null));
                rule.setRunPIXQuery(LdapUtils.booleanValue(attrs.get("dcmPIXQuery"), Boolean.FALSE));
                rule.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                rules.add(rule);
            }
            proxyAEE.setForwardRules(rules);
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    protected static conversionType conversionTypeValue(Attribute attr) throws NamingException {
        return attr != null ? ForwardRule.conversionType.valueOf((String) attr.get()) : null;
    }

    private Dimse[] dimseArray(Attribute attr) throws NamingException {
        if (attr == null)
            return new Dimse[]{};

        Dimse[] dimse = new Dimse[attr.size()];
        for (int i = 0; i < dimse.length; i++)
            dimse[i] = Dimse.valueOf((String) attr.get(i));
        return dimse;
    }

    private void loadForwardSchedules(ProxyAEExtension proxyAEE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(aeDN, "(objectclass=dcmForwardSchedule)");
        try {
            HashMap<String, ForwardSchedule> fwdSchedules = new HashMap<String, ForwardSchedule>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ForwardSchedule fwdSchedule = new ForwardSchedule();
                fwdSchedule.setDestinationAET(LdapUtils.stringValue(attrs.get("dcmDestinationAETitle"), null));
                fwdSchedule.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                Schedule schedule = new Schedule();
                schedule.setDays(LdapUtils.stringValue(attrs.get("dcmScheduleDays"), null));
                schedule.setHours(LdapUtils.stringValue(attrs.get("dcmScheduleHours"), null));
                fwdSchedule.setSchedule(schedule);
                fwdSchedules.put(fwdSchedule.getDestinationAET(), fwdSchedule);
            }
            proxyAEE.setForwardSchedules(fwdSchedules);
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadRetries(ProxyAEExtension proxyAEE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(aeDN, "(objectclass=dcmRetry)");
        try {
            List<Retry> retries = new ArrayList<Retry>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                Retry retry = new Retry(
                        RetryObject.valueOf(
                                LdapUtils.stringValue(attrs.get("dcmRetryObject"), null)), 
                                LdapUtils.intValue(attrs.get("dcmRetryDelay"),60), 
                                LdapUtils.intValue(attrs.get("dcmRetryNum"), 10),
                                LdapUtils.booleanValue(attrs.get("dcmDeleteAfterFinalRetry"), false));
                retries.add(retry);
            }
            proxyAEE.setRetries(retries);
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAEE == null)
            return;
        
        storeRetries(proxyAEE.getRetries(), aeDN);
        storeForwardSchedules(proxyAEE.getForwardSchedules().values(), aeDN);
        storeForwardRules(proxyAEE.getForwardRules(), aeDN);
        config.store(proxyAEE.getAttributeCoercions(), aeDN);
    }

    private void storeForwardRules(List<ForwardRule> forwardRules, String parentDN) throws NamingException {
        for (ForwardRule rule : forwardRules)
            config.createSubcontext(dnOfForwardRule(rule, parentDN),
                    storeToForwardRule(rule, new BasicAttributes(true)));
    }

    private String dnOfForwardRule(ForwardRule rule, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("cn=").append(rule.getCommonName());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    private Attributes storeToForwardRule(ForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmForwardRule");
        storeForwardRuleDimse(attrs, rule.getDimse());
        LdapUtils.storeNotEmpty(attrs, "dcmSOPClass", rule.getSopClass().toArray(new String[rule.getSopClass().size()]));
        LdapUtils.storeNotNull(attrs, "dcmCallingAETitle", rule.getCallingAET());
        LdapUtils.storeNotEmpty(attrs, "labeledURI", rule.getDestinationURI().toArray(new String[rule.getDestinationURI().size()]));
        LdapUtils.storeNotNull(attrs, "dcmUseCallingAETitle", rule.getUseCallingAET());
        LdapUtils.storeBoolean(attrs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC());
        LdapUtils.storeNotNull(attrs, "cn", rule.getCommonName());
        LdapUtils.storeNotNull(attrs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        LdapUtils.storeNotNull(attrs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        LdapUtils.storeNotNull(attrs, "dcmConversion", rule.getConversion());
        LdapUtils.storeNotNull(attrs, "dcmConversionUri", rule.getConversionUri());
        LdapUtils.storeBoolean(attrs, "dcmPIXQuery", rule.isRunPIXQuery());
        LdapUtils.storeNotNull(attrs, "dicomDescription", rule.getDescription());
        return attrs;
    }

    private void storeForwardRuleDimse(BasicAttributes attrs, List<Dimse> dimseList) {
        if (!dimseList.isEmpty()) {
            Attribute attr = new BasicAttribute("dcmForwardRuleDimse");
            for (Dimse dimse : dimseList)
                attr.add(dimse.toString());
            attrs.put(attr);
        }
    }

    private void storeRetries(List<Retry> retries, String parentDN) throws NamingException {
        for (Retry retry : retries)
            config.createSubcontext(dnOfRetry(retry, parentDN), storeToRetry(retry, new BasicAttributes(true)));
    }

    private void storeForwardSchedules(Collection<ForwardSchedule> fwdSchedules, String parentDN) throws NamingException {
        for (ForwardSchedule forwardSchedule : fwdSchedules)
            config.createSubcontext(dnOfForwardSchedule(forwardSchedule, parentDN), 
                    storeToForwardSchedule(forwardSchedule, new BasicAttributes(true)));
    }

    private Attributes storeToRetry(Retry retry, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRetry");
        LdapUtils.storeNotNull(attrs, "dcmRetryObject", retry.getRetryObject().toString());
        LdapUtils.storeNotNull(attrs, "dcmRetryDelay", retry.getDelay());
        LdapUtils.storeNotNull(attrs, "dcmRetryNum", retry.getNumberOfRetries());
        LdapUtils.storeNotNull(attrs, "dcmDeleteAfterFinalRetry", retry.isDeleteAfterFinalRetry());
        return attrs;
    }

    private Attributes storeToForwardSchedule(ForwardSchedule forwardSchedule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmForwardSchedule");
        LdapUtils.storeNotNull(attrs, "dcmDestinationAETitle", forwardSchedule.getDestinationAET());
        LdapUtils.storeNotNull(attrs, "dcmScheduleDays", forwardSchedule.getSchedule().getDays());
        LdapUtils.storeNotNull(attrs, "dcmScheduleHours", forwardSchedule.getSchedule().getHours());
        LdapUtils.storeNotNull(attrs, "dicomDescription", forwardSchedule.getDescription());
        return attrs;
    }

    private String dnOfRetry(Retry retry, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmRetryObject=").append(retry.getRetryObject().toString());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    private String dnOfForwardSchedule(ForwardSchedule fwdSchedule, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmDestinationAETitle=").append(fwdSchedule.getDestinationAET());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    @Override
    protected void storeDiffs(ApplicationEntity a, ApplicationEntity b, List<ModificationItem> mods) {
        ProxyAEExtension pa = a.getAEExtension(ProxyAEExtension.class);
        ProxyAEExtension pb = b.getAEExtension(ProxyAEExtension.class);
        if (pa == null || pb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmSpoolDirectory", pa.getSpoolDirectory(), pb.getSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmAcceptDataOnFailedAssociation", pa.isAcceptDataOnFailedAssociation(),
                pb.isAcceptDataOnFailedAssociation());
        LdapUtils.storeDiff(mods, "dcmEnableAuditLog", pa.isEnableAuditLog(), pb.isEnableAuditLog());
        LdapUtils.storeDiff(mods, "hl7ProxyPIXConsumerApplication", pa.getProxyPIXConsumerApplication(),
                pb.getProxyPIXConsumerApplication());
        LdapUtils.storeDiff(mods, "hl7RemotePIXManagerApplication", pa.getRemotePIXManagerApplication(),
                pb.getRemotePIXManagerApplication());
        LdapUtils.storeDiff(mods, "dcmDeleteFailedDataWithoutRetryConfiguration",
                pa.isDeleteFailedDataWithoutRetryConfiguration(), pb.isDeleteFailedDataWithoutRetryConfiguration());
        LdapUtils.storeDiff(mods, "dcmDestinationAETitle", pa.getFallbackDestinationAET(),
                pb.getFallbackDestinationAET());
    }

    @Override
    protected void storeDiffs(Device a, Device b, List<ModificationItem> mods) {
        ProxyDeviceExtension pa = a.getDeviceExtension(ProxyDeviceExtension.class);
        ProxyDeviceExtension pb = b.getDeviceExtension(ProxyDeviceExtension.class);
        if (pa == null || pb == null)
            return;
        
        LdapUtils.storeDiff(mods, "dcmSchedulerInterval", pa.getSchedulerInterval(), pb.getSchedulerInterval());
        LdapUtils.storeDiff(mods, "dcmForwardThreads", pa.getForwardThreads(), pb.getForwardThreads());
        LdapUtils.storeDiff(mods, "dcmProxyConfigurationStaleTimeout",
                pa.getConfigurationStaleTimeout(),
                pb.getConfigurationStaleTimeout(),
                0);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN) throws NamingException {
        ProxyAEExtension pprev = prev.getAEExtension(ProxyAEExtension.class);
        ProxyAEExtension pae = ae.getAEExtension(ProxyAEExtension.class);
        if (pprev == null || pae == null)
            return;
        
        config.merge(pprev.getAttributeCoercions(), pae.getAttributeCoercions(), aeDN);
        mergeRetries(pprev.getRetries(), pae.getRetries(), aeDN);
        mergeForwardSchedules(pprev.getForwardSchedules().values(), pae.getForwardSchedules().values(), aeDN);
        mergeForwardRules(pprev.getForwardRules(), pae.getForwardRules(), aeDN);
    }

    private void mergeForwardRules(List<ForwardRule> prevs, List<ForwardRule> rules, String parentDN) throws NamingException {
        for (ForwardRule prev : prevs)
            if (!rules.contains(prev))
                config.destroySubcontext(dnOfForwardRule(prev, parentDN));
        for (ForwardRule rule : rules) {
            String dn = dnOfForwardRule(rule, parentDN);
            Integer indexOfPrevRule = prevs.indexOf(rule);
            if (indexOfPrevRule == -1)
                config.createSubcontext(dn, storeToForwardRule(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeForwardRuleDiffs(prevs.get(indexOfPrevRule), rule, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeForwardRuleDiffs(ForwardRule ruleA, ForwardRule ruleB,
            ArrayList<ModificationItem> mods) {
        List<String> dimseA = new ArrayList<String>();
        for (Dimse dimse : ruleA.getDimse())
            dimseA.add(dimse.toString());
        List<String> dimseB = new ArrayList<String>();
        for (Dimse dimse : ruleB.getDimse())
            dimseB.add(dimse.toString());
        LdapUtils.storeDiff(mods, "dcmForwardRuleDimse", dimseA.toArray(new String[dimseA.size()]),
                dimseB.toArray(new String[dimseB.size()]));
        LdapUtils.storeDiff(mods, "dcmSOPClass", ruleA.getSopClass().toArray(new String[ruleA.getSopClass().size()]),
                ruleB.getSopClass().toArray(new String[ruleB.getSopClass().size()]));
        LdapUtils.storeDiff(mods, "dcmCallingAETitle", ruleA.getCallingAET(), ruleB.getCallingAET());
        LdapUtils.storeDiff(mods, "labeledURI",
                ruleA.getDestinationURI().toArray(new String[ruleA.getDestinationURI().size()]), ruleB
                        .getDestinationURI().toArray(new String[ruleB.getDestinationURI().size()]));
        LdapUtils.storeDiff(mods, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(),
                ruleB.isExclusiveUseDefinedTC());
        LdapUtils.storeDiff(mods, "cn", ruleA.getCommonName(), ruleB.getCommonName());
        LdapUtils.storeDiff(mods, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        LdapUtils.storeDiff(mods, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB.getReceiveSchedule()
                .getDays());
        LdapUtils.storeDiff(mods, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB.getReceiveSchedule()
                .getHours());
        LdapUtils.storeDiff(mods, "dcmConversion", ruleA.getConversion(), ruleB.getConversion());
        LdapUtils.storeDiff(mods, "dcmConversionUri", ruleA.getConversionUri(), ruleB.getConversionUri());
        LdapUtils.storeDiff(mods, "dcmPIXQuery", ruleA.isRunPIXQuery(), ruleB.isRunPIXQuery());
        LdapUtils.storeDiff(mods, "dicomDescription", ruleA.getDescription(), ruleB.getDescription());
        return mods;
    }

    private void mergeRetries(List<Retry> prevs, List<Retry> retries, String parentDN) throws NamingException {
        for (Retry prev : prevs)
            if (!retries.contains(prev))
                config.destroySubcontext(dnOfRetry(prev, parentDN));
        for (Retry retry : retries) {
            String dn = dnOfRetry(retry, parentDN);
            Integer indexOfPrevRetry = prevs.indexOf(retry);
            if (indexOfPrevRetry == -1)
                config.createSubcontext(dn, storeToRetry(retry, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeRetryDiffs(prevs.get(indexOfPrevRetry), retry, new ArrayList<ModificationItem>()));
        }
    }

    private void mergeForwardSchedules(Collection<ForwardSchedule> prevSchedules,
            Collection<ForwardSchedule> currSchedules, String parentDN) throws NamingException {
        List<ForwardSchedule> prevList = new ArrayList<ForwardSchedule>(prevSchedules);
        List<ForwardSchedule> currList = new ArrayList<ForwardSchedule>(currSchedules);
        for (ForwardSchedule prev : prevList)
            if (!currList.contains(prev))
                config.destroySubcontext(dnOfForwardSchedule(prev, parentDN));
        for (ForwardSchedule forwardSchedule : currList) {
            String dn = dnOfForwardSchedule(forwardSchedule, parentDN);
            Integer indexOfPrevSchedule = prevList.indexOf(forwardSchedule);
            if (indexOfPrevSchedule == -1)
                config.createSubcontext(dn, storeToForwardSchedule(forwardSchedule, new BasicAttributes(true)));
            else
                config.modifyAttributes(
                        dn,
                        storeScheduleDiffs(prevList.get(indexOfPrevSchedule), forwardSchedule,
                                new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeScheduleDiffs(ForwardSchedule a, ForwardSchedule b,
            ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmScheduleDays", a.getSchedule().getDays(), b.getSchedule().getDays());
        LdapUtils.storeDiff(mods, "dcmScheduleHours", a.getSchedule().getHours(), b.getSchedule().getHours());
        LdapUtils.storeDiff(mods, "dcmDestinationAETitle", a.getDestinationAET(), b.getDestinationAET());
        LdapUtils.storeDiff(mods, "dicomDescription", a.getDescription(), b.getDescription());
        return mods;
    }

    private List<ModificationItem> storeRetryDiffs(Retry prev, Retry ac, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmRetryDelay", prev.getDelay(), ac.getDelay());
        LdapUtils.storeDiff(mods, "dcmRetryNum", prev.getNumberOfRetries(), ac.getNumberOfRetries());
        LdapUtils.storeDiff(mods, "dcmDeleteAfterFinalRetry", prev.isDeleteAfterFinalRetry(), ac.isDeleteAfterFinalRetry());
        return mods;
    }
}
