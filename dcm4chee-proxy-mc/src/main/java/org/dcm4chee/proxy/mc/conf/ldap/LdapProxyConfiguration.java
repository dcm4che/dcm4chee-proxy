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

package org.dcm4chee.proxy.mc.conf.ldap;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;

import org.dcm4che.conf.ldap.ExtendedLdapDicomConfiguration;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4chee.proxy.mc.net.ForwardRule;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.dcm4chee.proxy.mc.net.Retry;
import org.dcm4chee.proxy.mc.net.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapProxyConfiguration extends ExtendedLdapDicomConfiguration {

    public LdapProxyConfiguration(Hashtable<String, Object> env, String baseDN) throws NamingException {
        super(env, baseDN);
    }

    @Override
    protected Attribute objectClassesOf(Device device, Attribute attr) {
        super.objectClassesOf(device, attr);
        if (device instanceof ProxyDevice)
            attr.add("dcmProxyDevice");
        return attr;
    }

    @Override
    protected Attribute objectClassesOf(ApplicationEntity ae, Attribute attr) {
        super.objectClassesOf(ae, attr);
        if (ae instanceof ProxyApplicationEntity)
            attr.add("dcmProxyNetworkAE");
        return attr;
    }

    @Override
    protected Device newDevice(Attributes attrs) throws NamingException {
        if (!hasObjectClass(attrs, "dcmProxyDevice"))
            return super.newDevice(attrs);
        ProxyDevice device = new ProxyDevice(stringValue(attrs.get("dicomDeviceName")));
        device.setSchedulerInterval(intValue(attrs.get("dcmSchedulerInterval"), 60));
        device.setDicomConf(this);
        return device;
    }

    @Override
    protected ApplicationEntity newApplicationEntity(Attributes attrs) throws NamingException {
        if (!hasObjectClass(attrs, "dcmProxyNetworkAE"))
            return super.newApplicationEntity(attrs);
        return new ProxyApplicationEntity(stringValue(attrs.get("dicomAETitle")));
    }

    @Override
    protected Attributes storeTo(Device device, Attributes attrs) {
        super.storeTo(device, attrs);
        if (!(device instanceof ProxyDevice))
            return attrs;
        ProxyDevice proxyDev = (ProxyDevice) device;
        storeNotNull(attrs, "dcmSchedulerInterval", proxyDev.getSchedulerInterval());
        return attrs;
    }

    @Override
    protected Attributes storeTo(ApplicationEntity ae, String deviceDN, Attributes attrs) {
        super.storeTo(ae, deviceDN, attrs);
        if (!(ae instanceof ProxyApplicationEntity))
            return attrs;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        storeNotNull(attrs, "dcmSpoolDirectory", proxyAE.getSpoolDirectory());
        storeNotNull(attrs, "dcmAcceptDataOnFailedNegotiation", proxyAE.isAcceptDataOnFailedNegotiation());
        storeNotNull(attrs, "dcmEnableAuditLog", proxyAE.isEnableAuditLog());
        storeNotNull(attrs, "dcmAuditDirectory", proxyAE.getAuditDirectory());
        storeNotNull(attrs, "dcmNactionDirectory", proxyAE.getNactionDirectory());
        storeNotNull(attrs, "dcmNeventDirectory", proxyAE.getNeventDirectory());
        storeNotNull(attrs, "dcmDefaultDestinationAETitle", proxyAE.getDefaultDestinationAET());
        return attrs;
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        super.loadFrom(device, attrs);
        if (!(device instanceof ProxyDevice))
            return;
        ProxyDevice proxyDev = (ProxyDevice) device;
        proxyDev.setSchedulerInterval(intValue(attrs.get("dcmSchedulerInterval"), 60));
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        super.loadFrom(ae, attrs);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        proxyAE.setSpoolDirectory(stringValue(attrs.get("dcmSpoolDirectory")));
        proxyAE.setAcceptDataOnFailedNegotiation(booleanValue(attrs.get("dcmAcceptDataOnFailedNegotiation"),
                Boolean.FALSE));
        proxyAE.setEnableAuditLog(booleanValue(attrs.get("dcmEnableAuditLog"), Boolean.FALSE));
        proxyAE.setAuditDirectory(stringValue(attrs.get("dcmAuditDirectory")));
        proxyAE.setNactionDirectory(stringValue(attrs.get("dcmNactionDirectory")));
        proxyAE.setNeventDirectory(stringValue(attrs.get("dcmNeventDirectory")));
        proxyAE.setDefaultDestinationAET(stringValue(attrs.get("dcmDefaultDestinationAETitle")));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        super.loadChilds(ae, aeDN);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        loadRetries(proxyAE, aeDN);
        loadForwardSchedules(proxyAE, aeDN);
        loadForwardRules(proxyAE, aeDN);
        load(proxyAE.getAttributeCoercions(), aeDN);
    }

    private void loadForwardRules(ProxyApplicationEntity proxyAE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = search(aeDN, "(objectclass=dcmForwardRule)");
        try {
            List<ForwardRule> rules = new ArrayList<ForwardRule>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ForwardRule rule = new ForwardRule();
                rule.setDimse(stringValue(attrs.get("dcmDIMSE")));
                rule.setSopClass(stringValue(attrs.get("dicomSOPClass")));
                rule.setCallingAET(stringValue(attrs.get("dcmCallingAETitle")));
                rule.setDestinationURI(stringValue(attrs.get("labeledURI")));
                rule.setUseCallingAET(stringValue(attrs.get("dcmUseCallingAETitle")));
                rule.setExclusiveUseDefinedTC(booleanValue(attrs.get("dcmExclusiveUseDefinedTC"), Boolean.FALSE));
                rule.setCommonName(stringValue(attrs.get("commonName")));
                Schedule schedule = new Schedule();
                schedule.setDays(stringValue(attrs.get("dcmScheduleDays")));
                schedule.setHours(stringValue(attrs.get("dcmScheduleHours")));
                rule.setReceiveSchedule(schedule);
                rules.add(rule);
            }
            proxyAE.setForwardRules(rules);
        } finally {
            safeClose(ne);
        }
    }

    private void loadForwardSchedules(ProxyApplicationEntity proxyAE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = search(aeDN, "(objectclass=dcmForwardSchedule)");
        try {
            HashMap<String, Schedule> forwardSchedules = new HashMap<String, Schedule>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                Schedule schedule = new Schedule();
                schedule.setDays(stringValue(attrs.get("dcmScheduleDays")));
                schedule.setHours(stringValue(attrs.get("dcmScheduleHours")));
                forwardSchedules.put(stringValue(attrs.get("dcmDestinationAETitle")), schedule);
            }
            proxyAE.setForwardSchedules(forwardSchedules);
        } finally {
            safeClose(ne);
        }
    }

    private void loadRetries(ProxyApplicationEntity proxyAE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = search(aeDN, "(objectclass=dcmRetry)");
        try {
            List<Retry> retries = new ArrayList<Retry>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                Retry retry = new Retry(stringValue(attrs.get("dcmRetrySuffix")), intValue(attrs.get("dcmRetryDelay"),
                        60), intValue(attrs.get("dcmRetryNum"), 10));
                retries.add(retry);
            }
            proxyAE.setRetries(retries);
        } finally {
            safeClose(ne);
        }
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        super.storeChilds(aeDN, ae);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        storeRetries(proxyAE.getRetries(), aeDN);
        storeForwardSchedules(proxyAE.getForwardSchedules(), aeDN);
        storeForwardRules(proxyAE.getForwardRules(), aeDN);
        store(proxyAE.getAttributeCoercions(), aeDN);
    }

    private void storeForwardRules(List<ForwardRule> forwardRules, String parentDN) throws NamingException {
        for (ForwardRule rule : forwardRules)
            createSubcontext(dnOfForwardRule(rule, parentDN), storeToForwardRule(rule, new BasicAttributes(true)));
    }

    private String dnOfForwardRule(ForwardRule rule, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("commonName=").append(rule.getCommonName());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    private Attributes storeToForwardRule(ForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmForwardRule");
        storeNotNull(attrs, "dcmDIMSE", rule.getDimse());
        storeNotNull(attrs, "dicomSOPClass", rule.getSopClass());
        storeNotNull(attrs, "dcmCallingAETitle", rule.getCallingAET());
        storeNotNull(attrs, "labeledURI", rule.getDestinationURI());
        storeNotNull(attrs, "dcmUseCallingAET", rule.getUseCallingAET());
        storeBoolean(attrs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC());
        storeNotNull(attrs, "commonName", rule.getCommonName());
        storeNotNull(attrs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        storeNotNull(attrs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        return attrs;
    }

    private void storeRetries(List<Retry> retries, String parentDN) throws NamingException {
        for (Retry retry : retries)
            createSubcontext(dnOfRetry(retry, parentDN), storeToRetry(retry, new BasicAttributes(true)));
    }

    private void storeForwardSchedules(HashMap<String, Schedule> forwardSchedules, String parentDN) throws NamingException {
        for (Entry<String, Schedule> forwardSchedule : forwardSchedules.entrySet())
            createSubcontext(dnOfForwardSchedule(forwardSchedule.getKey(), parentDN), 
                    storeToForwardSchedule(forwardSchedule, new BasicAttributes(true)));
    }

    private Attributes storeToRetry(Retry retry, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRetry");
        storeNotNull(attrs, "dcmRetrySuffix", retry.getSuffix());
        storeNotNull(attrs, "dcmRetryDelay", retry.getDelay());
        storeNotNull(attrs, "dcmRetryNum", retry.getNumberOfRetries());
        return attrs;
    }

    private Attributes storeToForwardSchedule(Entry<String, Schedule> forwardSchedule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmForwardSchedule");
        storeNotNull(attrs, "dcmDestinationAETitle", forwardSchedule.getKey());
        storeNotNull(attrs, "dcmScheduleDays", forwardSchedule.getValue().getDays());
        storeNotNull(attrs, "dcmScheduleHours", forwardSchedule.getValue().getHours());
        return attrs;
    }

    private String dnOfRetry(Retry retry, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmRetrySuffix=").append(retry.getSuffix());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    private String dnOfForwardSchedule(String destinationAET, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmDestinationAETitle=").append(destinationAET);
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    @Override
    protected List<ModificationItem> storeDiffs(ApplicationEntity a, ApplicationEntity b, String deviceDN,
            List<ModificationItem> mods) {
        super.storeDiffs(a, b, deviceDN, mods);
        if (!(a instanceof ProxyApplicationEntity) || !(b instanceof ProxyApplicationEntity))
            return mods;
        ProxyApplicationEntity pa = (ProxyApplicationEntity) a;
        ProxyApplicationEntity pb = (ProxyApplicationEntity) b;
        storeDiff(mods, "dcmSpoolDirectory", pa.getSpoolDirectory(), pb.getSpoolDirectory());
        storeDiff(mods, "dcmAcceptDataOnFailedNegotiation", pa.isAcceptDataOnFailedNegotiation(),
                pb.isAcceptDataOnFailedNegotiation());
        storeDiff(mods, "dcmEnableAuditLog", pa.isEnableAuditLog(), pb.isEnableAuditLog());
        storeDiff(mods, "dcmAuditDirectory", pa.getAuditDirectory(), pb.getAuditDirectory());
        storeDiff(mods, "dcmNactionDirectory", pa.getNactionDirectory(), pb.getNactionDirectory());
        storeDiff(mods, "dcmNeventDirectory", pa.getNeventDirectory(), pb.getNeventDirectory());
        storeDiff(mods, "dcmDefaultDestinationAETitle", pa.getDefaultDestinationAET(), pb.getDefaultDestinationAET());
        return mods;
    }

    @Override
    protected List<ModificationItem> storeDiffs(Device a, Device b, List<ModificationItem> mods) {
        super.storeDiffs(a, b, mods);
        if (!(a instanceof ProxyDevice) || !(b instanceof ProxyDevice))
            return mods;
        ProxyDevice pa = (ProxyDevice) a;
        ProxyDevice pb = (ProxyDevice) b;
        storeDiff(mods, "dcmSchedulerInterval", pa.getSchedulerInterval(), pb.getSchedulerInterval());
        return mods;
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN) throws NamingException {
        super.mergeChilds(prev, ae, aeDN);
        if (!(prev instanceof ProxyApplicationEntity) || !(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity pprev = (ProxyApplicationEntity) prev;
        ProxyApplicationEntity pae = (ProxyApplicationEntity) ae;
        merge(pprev.getAttributeCoercions(), pae.getAttributeCoercions(), aeDN);
        mergeRetries(pprev.getRetries(), pae.getRetries(), aeDN);
        mergeForwardSchedules(pprev.getForwardSchedules(), pae.getForwardSchedules(), aeDN);
        mergeForwardRules(pprev.getForwardRules(), pae.getForwardRules(), aeDN);
    }

    private void mergeForwardRules(List<ForwardRule> prevs, List<ForwardRule> rules, String parentDN) throws NamingException {
        for (ForwardRule prev : prevs)
            if (!rules.contains(prev))
                destroySubcontext(dnOfForwardRule(prev, parentDN));
        for (ForwardRule rule : rules) {
            String dn = dnOfForwardRule(rule, parentDN);
            Integer indexOfPrevRule = prevs.indexOf(rule);
            if (indexOfPrevRule == -1)
                createSubcontext(dn, storeToForwardRule(rule, new BasicAttributes(true)));
            else
                modifyAttributes(dn, storeForwardRuleDiffs(prevs.get(indexOfPrevRule), rule, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeForwardRuleDiffs(ForwardRule ruleA, ForwardRule ruleB,
            ArrayList<ModificationItem> mods) {
        storeDiff(mods, "dcmDIMSE", ruleA.getDimse(), ruleB.getDimse());
        storeDiff(mods, "dicomSOPClass", ruleA.getSopClass(), ruleB.getSopClass());
        storeDiff(mods, "dcmCallingAETitle", ruleA.getCallingAET(), ruleB.getCallingAET());
        storeDiff(mods, "labeledURI", ruleA.getDestinationURI(), ruleB.getDestinationURI());
        storeDiff(mods, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(), ruleB.isExclusiveUseDefinedTC());
        storeDiff(mods, "commonName", ruleA.getCommonName(), ruleB.getCommonName());
        storeDiff(mods, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        storeDiff(mods, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB.getReceiveSchedule().getDays());
        storeDiff(mods, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB.getReceiveSchedule().getHours());
        return mods;
    }

    private void mergeRetries(List<Retry> prevs, List<Retry> retries, String parentDN) throws NamingException {
        for (Retry prev : prevs)
            if (!retries.contains(prev))
                destroySubcontext(dnOfRetry(prev, parentDN));
        for (Retry retry : retries) {
            String dn = dnOfRetry(retry, parentDN);
            Integer indexOfPrevRetry = prevs.indexOf(retry);
            if (indexOfPrevRetry == -1)
                createSubcontext(dn, storeToRetry(retry, new BasicAttributes(true)));
            else
                modifyAttributes(dn,
                        storeRetryDiffs(prevs.get(indexOfPrevRetry), retry, new ArrayList<ModificationItem>()));
        }
    }

    private void mergeForwardSchedules(HashMap<String, Schedule> prevs, HashMap<String, Schedule> schedules, String parentDN) throws NamingException {
        for (String destinationAET : prevs.keySet())
            if (!schedules.containsKey(destinationAET))
                destroySubcontext(dnOfForwardSchedule(destinationAET, parentDN));
        for (Entry<String, Schedule> forwardSchedule : schedules.entrySet()) {
            String dn = dnOfForwardSchedule(forwardSchedule.getKey(), parentDN);
            if (!prevs.containsKey(forwardSchedule.getKey()))
                createSubcontext(dn, storeToForwardSchedule(forwardSchedule, new BasicAttributes(true)));
            else
                modifyAttributes(dn, storeScheduleDiffs(prevs.get(forwardSchedule.getKey()), forwardSchedule.getValue(), 
                                new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeScheduleDiffs(Schedule scheduleA, Schedule scheduleB,
            ArrayList<ModificationItem> mods) {
        storeDiff(mods, "dcmScheduleDays", scheduleA.getDays(), scheduleB.getDays());
        storeDiff(mods, "dcmScheduleHours", scheduleA.getHours(), scheduleB.getHours());
        return mods;
    }

    private List<ModificationItem> storeRetryDiffs(Retry prev, Retry ac, ArrayList<ModificationItem> mods) {
        storeDiff(mods, "dcmRetryDelay", prev.getDelay(), ac.getDelay());
        storeDiff(mods, "dcmRetryNum", prev.getNumberOfRetries(), ac.getNumberOfRetries());
        return mods;
    }
}
