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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ForwardRule.conversionType;
import org.dcm4chee.proxy.conf.ProxyApplicationEntity;
import org.dcm4chee.proxy.conf.ProxyDevice;
import org.dcm4chee.proxy.conf.ProxyHL7Application;
import org.dcm4chee.proxy.conf.Retry;
import org.dcm4chee.proxy.conf.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapProxyConfiguration extends LdapHL7Configuration {

    public LdapProxyConfiguration() throws ConfigurationException {}

    public LdapProxyConfiguration(Hashtable<?, ?> env) throws ConfigurationException {
        super(env);
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
    protected Attribute objectClassesOf(HL7Application app, Attribute attr) {
        super.objectClassesOf(app, attr);
        if (app instanceof ProxyHL7Application)
            attr.add("dcmProxyHL7Application");
        return attr;
    }

    @Override
    protected Device newDevice(Attributes attrs) throws NamingException {
        if (!hasObjectClass(attrs, "dcmProxyDevice"))
            return super.newDevice(attrs);
        ProxyDevice device = new ProxyDevice(stringValue(attrs.get("dicomDeviceName")));
        device.setSchedulerInterval(intValue(attrs.get("dcmSchedulerInterval"), 60));
        device.setForwardThreads(intValue(attrs.get("dcmForwardThreads"), 1));
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
    protected HL7Application newHL7Application(Attributes attrs) throws NamingException {
        if (!hasObjectClass(attrs, "dcmProxyHL7Application"))
            return super.newHL7Application(attrs);
        return new ProxyHL7Application(stringValue(attrs.get("hl7ApplicationName")));
    }

    @Override
    protected Attributes storeTo(Device device, Attributes attrs) {
        super.storeTo(device, attrs);
        if (!(device instanceof ProxyDevice))
            return attrs;
        ProxyDevice proxyDev = (ProxyDevice) device;
        storeNotNull(attrs, "dcmSchedulerInterval", proxyDev.getSchedulerInterval());
        storeNotNull(attrs, "dcmForwardThreads", proxyDev.getForwardThreads());
        storeNotDef(attrs, "dcmProxyConfigurationStaleTimeout", proxyDev.getConfigurationStaleTimeout(), 0);
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
        storeNotNull(attrs, "hl7ProxyPIXConsumerApplication", proxyAE.getProxyPIXConsumerApplication());
        storeNotNull(attrs, "hl7RemotePIXManagerApplication", proxyAE.getRemotePIXManagerApplication());
        return attrs;
    }

    @Override
    protected Attributes storeTo(HL7Application hl7App, String deviceDN, Attributes attrs) {
        super.storeTo(hl7App, deviceDN, attrs);
        if (!(hl7App instanceof ProxyHL7Application))
            return attrs;

        ProxyHL7Application prxHL7App = (ProxyHL7Application) hl7App;
        storeNotEmpty(attrs, "labeledURI", prxHL7App.getTemplatesURIs());
        return attrs;
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        super.loadFrom(device, attrs);
        if (!(device instanceof ProxyDevice))
            return;
        ProxyDevice proxyDev = (ProxyDevice) device;
        proxyDev.setSchedulerInterval(intValue(attrs.get("dcmSchedulerInterval"), ProxyDevice.DEFAULT_SCHEDULER_INTERVAL));
        proxyDev.setForwardThreads(intValue(attrs.get("dcmForwardThreads"), ProxyDevice.DEFAULT_FORWARD_THREADS));
        proxyDev.setConfigurationStaleTimeout(intValue(attrs.get("dcmProxyConfigurationStaleTimeout"), 0));
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
        proxyAE.setProxyPIXConsumerApplication(stringValue(attrs.get("hl7ProxyPIXConsumerApplication")));
        proxyAE.setRemotePIXManagerApplication(stringValue(attrs.get("hl7RemotePIXManagerApplication")));
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
                rule.setDimse(Arrays.asList(dimseArray(attrs.get("dcmForwardRuleDimse"))));
                rule.setSopClass(Arrays.asList(stringArray(attrs.get("dcmSOPClass"))));
                rule.setCallingAET(stringValue(attrs.get("dcmCallingAETitle")));
                rule.setDestinationURIs(Arrays.asList(stringArray(attrs.get("labeledURI"))));
                rule.setUseCallingAET(stringValue(attrs.get("dcmUseCallingAETitle")));
                rule.setExclusiveUseDefinedTC(booleanValue(attrs.get("dcmExclusiveUseDefinedTC"), Boolean.FALSE));
                rule.setCommonName(stringValue(attrs.get("cn")));
                Schedule schedule = new Schedule();
                schedule.setDays(stringValue(attrs.get("dcmScheduleDays")));
                schedule.setHours(stringValue(attrs.get("dcmScheduleHours")));
                rule.setReceiveSchedule(schedule);
                rule.setConversion(conversionTypeValue(attrs.get("dcmConversion")));
                rule.setConversionUri(stringValue(attrs.get("dcmConversionUri")));
                rule.setRunPIXQuery(booleanValue(attrs.get("dcmPIXQuery"), Boolean.FALSE));
                rules.add(rule);
            }
            proxyAE.setForwardRules(rules);
        } finally {
            safeClose(ne);
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
                Retry retry = new Retry(
                        RetryObject.valueOf(stringValue(attrs.get("dcmRetryObject"))), 
                        intValue(attrs.get("dcmRetryDelay"),60), 
                        intValue(attrs.get("dcmRetryNum"), 10));
                retries.add(retry);
            }
            proxyAE.setRetries(retries);
        } finally {
            safeClose(ne);
        }
    }

    @Override
    protected void loadFrom(HL7Application hl7App, Attributes attrs)
            throws NamingException {
       super.loadFrom(hl7App, attrs);
       if (!(hl7App instanceof ProxyHL7Application))
           return;
       ProxyHL7Application arcHL7App = (ProxyHL7Application) hl7App;
       arcHL7App.setTemplatesURIs(stringArray(attrs.get("labeledURI")));
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
        sb.append("cn=").append(rule.getCommonName());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    private Attributes storeToForwardRule(ForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmForwardRule");
        storeForwardRuleDimse(attrs, rule.getDimse());
        storeNotEmpty(attrs, "dcmSOPClass", rule.getSopClass().toArray(new String[rule.getSopClass().size()]));
        storeNotNull(attrs, "dcmCallingAETitle", rule.getCallingAET());
        storeNotEmpty(attrs, "labeledURI", rule.getDestinationURI().toArray(new String[rule.getDestinationURI().size()]));
        storeNotNull(attrs, "dcmUseCallingAETitle", rule.getUseCallingAET());
        storeBoolean(attrs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC());
        storeNotNull(attrs, "cn", rule.getCommonName());
        storeNotNull(attrs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        storeNotNull(attrs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        storeNotNull(attrs, "dcmConversion", rule.getConversion());
        storeNotNull(attrs, "dcmConversionUri", rule.getConversionUri());
        storeBoolean(attrs, "dcmPIXQuery", rule.isRunPIXQuery());
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
            createSubcontext(dnOfRetry(retry, parentDN), storeToRetry(retry, new BasicAttributes(true)));
    }

    private void storeForwardSchedules(HashMap<String, Schedule> forwardSchedules, String parentDN) throws NamingException {
        for (Entry<String, Schedule> forwardSchedule : forwardSchedules.entrySet())
            createSubcontext(dnOfForwardSchedule(forwardSchedule.getKey(), parentDN), 
                    storeToForwardSchedule(forwardSchedule, new BasicAttributes(true)));
    }

    private Attributes storeToRetry(Retry retry, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRetry");
        storeNotNull(attrs, "dcmRetryObject", retry.getRetryObject().toString());
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
        sb.append("dcmRetryObject=").append(retry.getRetryObject().toString());
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
        storeDiff(mods, "hl7ProxyPIXConsumerApplication",
                pa.getProxyPIXConsumerApplication(),
                pb.getProxyPIXConsumerApplication());
        storeDiff(mods, "hl7RemotePIXManagerApplication",
                pa.getRemotePIXManagerApplication(),
                pb.getRemotePIXManagerApplication());
        return mods;
    }
    
    @Override
    protected List<ModificationItem> storeDiffs(HL7Application a,
            HL7Application b, String deviceDN, List<ModificationItem> mods) {
        super.storeDiffs(a, b, deviceDN, mods);
        if (!(a instanceof ProxyHL7Application 
           && b instanceof ProxyHL7Application))
            return mods;
        
        ProxyHL7Application aa = (ProxyHL7Application) a;
        ProxyHL7Application bb = (ProxyHL7Application) b;
        storeDiff(mods, "labeledURI",
                aa.getTemplatesURIs(),
                bb.getTemplatesURIs());
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
        storeDiff(mods, "dcmForwardThreads", pa.getForwardThreads(), pb.getForwardThreads());
        storeDiff(mods, "dcmProxyConfigurationStaleTimeout",
                pa.getConfigurationStaleTimeout(),
                pb.getConfigurationStaleTimeout(),
                0);
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
        List<String> dimseA = new ArrayList<String>();
        for (Dimse dimse : ruleA.getDimse())
            dimseA.add(dimse.toString());
        List<String> dimseB = new ArrayList<String>();
        for (Dimse dimse : ruleB.getDimse())
            dimseB.add(dimse.toString());
        storeDiff(mods, "dcmForwardRuleDimse", 
                dimseA.toArray(new String[dimseA.size()]), 
                dimseB.toArray(new String[dimseB.size()]));
        storeDiff(mods, "dcmSOPClass", 
                ruleA.getSopClass().toArray(new String[ruleA.getSopClass().size()]), 
                ruleB.getSopClass().toArray(new String[ruleB.getSopClass().size()]));
        storeDiff(mods, "dcmCallingAETitle", ruleA.getCallingAET(), ruleB.getCallingAET());
        storeDiff(mods, "labeledURI", 
                ruleA.getDestinationURI().toArray(new String[ruleA.getDestinationURI().size()]), 
                ruleB.getDestinationURI().toArray(new String[ruleB.getDestinationURI().size()]));
        storeDiff(mods, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(), ruleB.isExclusiveUseDefinedTC());
        storeDiff(mods, "cn", ruleA.getCommonName(), ruleB.getCommonName());
        storeDiff(mods, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        storeDiff(mods, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB.getReceiveSchedule().getDays());
        storeDiff(mods, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB.getReceiveSchedule().getHours());
        storeDiff(mods, "dcmConversion", ruleA.getConversion(), ruleB.getConversion());
        storeDiff(mods, "dcmConversionUri", ruleA.getConversionUri(), ruleB.getConversionUri());
        storeDiff(mods, "dcmPIXQuery", ruleA.isRunPIXQuery(), ruleB.isRunPIXQuery());
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
