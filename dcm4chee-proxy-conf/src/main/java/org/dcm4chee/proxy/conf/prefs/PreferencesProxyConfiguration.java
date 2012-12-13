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

package org.dcm4chee.proxy.conf.prefs;

import java.io.Serializable;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyApplicationEntity;
import org.dcm4chee.proxy.conf.ProxyDevice;
import org.dcm4chee.proxy.conf.Retry;
import org.dcm4chee.proxy.conf.RetryObject;
import org.dcm4chee.proxy.conf.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@gmail.com>
 */
public class PreferencesProxyConfiguration extends PreferencesDicomConfiguration implements Serializable {

    private static final long serialVersionUID = 7295686215722926221L;

    @Override
    protected Device newDevice(Preferences deviceNode) {
        if (!deviceNode.getBoolean("dcmProxyDevice", false))
            return super.newDevice(deviceNode);

        ProxyDevice device = new ProxyDevice(deviceNode.name());
        device.setDicomConf(this);
        return device;
    }

    @Override
    protected ApplicationEntity newApplicationEntity(Preferences aeNode) {
        if (!aeNode.getBoolean("dcmProxyNetworkAE", false))
            return super.newApplicationEntity(aeNode);

        return new ProxyApplicationEntity(aeNode.name());
    }

    @Override
    protected void storeTo(Device device, Preferences prefs) {
        super.storeTo(device, prefs);
        if (!(device instanceof ProxyDevice))
            return;

        ProxyDevice proxyDev = (ProxyDevice) device;
        prefs.putBoolean("dcmProxyDevice", true);
        storeNotNull(prefs, "dcmSchedulerInterval", proxyDev.getSchedulerInterval());
        storeNotNull(prefs, "dcmForwardThreads", proxyDev.getForwardThreads());
        storeNotDef(prefs, "dcmProxyConfigurationStaleTimeout", proxyDev.getConfigurationStaleTimeout(), 0);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Preferences prefs, List<Connection> devConns) {
        super.storeTo(ae, prefs, devConns);
        if (!(ae instanceof ProxyApplicationEntity))
            return;

        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        prefs.putBoolean("dcmProxyNetworkAE", true);
        storeNotNull(prefs, "dcmSpoolDirectory", proxyAE.getSpoolDirectory());
        storeNotNull(prefs, "dcmAcceptDataOnFailedNegotiation", proxyAE.isAcceptDataOnFailedNegotiation());
        storeNotNull(prefs, "dcmEnableAuditLog", proxyAE.isEnableAuditLog());
    }

    @Override
    protected void loadFrom(Device device, Preferences prefs) throws CertificateException, BackingStoreException {
        super.loadFrom(device, prefs);
        if (!(device instanceof ProxyDevice))
            return;

        ProxyDevice proxyDev = (ProxyDevice) device;
        proxyDev.setSchedulerInterval(prefs.getInt("dcmSchedulerInterval", ProxyDevice.DEFAULT_SCHEDULER_INTERVAL));
        proxyDev.setForwardThreads(prefs.getInt("dcmForwardThreads", ProxyDevice.DEFAULT_FORWARD_THREADS));
        proxyDev.setConfigurationStaleTimeout(prefs.getInt("dcmProxyConfigurationStaleTimeout", 0));
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Preferences prefs) {
        super.loadFrom(ae, prefs);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        proxyAE.setSpoolDirectory(prefs.get("dcmSpoolDirectory", null));
        proxyAE.setAcceptDataOnFailedNegotiation(prefs.getBoolean("dcmAcceptDataOnFailedNegotiation", false));
        proxyAE.setEnableAuditLog(prefs.getBoolean("dcmEnableAuditLog", false));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, Preferences aeNode) throws BackingStoreException {
        super.loadChilds(ae, aeNode);
        if (!(ae instanceof ProxyApplicationEntity))
            return;

        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        loadRetries(proxyAE, aeNode);
        loadForwardSchedules(proxyAE, aeNode);
        loadForwardRules(proxyAE, aeNode);
        load(proxyAE.getAttributeCoercions(), aeNode);
    }

    private void loadForwardRules(ProxyApplicationEntity proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences rulesNode = paeNode.node("dcmForwardRule");
        List<ForwardRule> rules = new ArrayList<ForwardRule>();
        for (String ruleName : rulesNode.childrenNames()) {
            Preferences ruleNode = rulesNode.node(ruleName);
            ForwardRule rule = new ForwardRule();
            rule.setDimse(Arrays.asList(dimseArray(ruleNode, "dcmForwardRuleDimse")));
            rule.setSopClass(Arrays.asList(stringArray(ruleNode, "dcmSOPClass")));
            rule.setCallingAET(ruleNode.get("dcmCallingAETitle", null));
            rule.setDestinationURIs(Arrays.asList(stringArray(ruleNode, "labeledURI")));
            rule.setUseCallingAET(ruleNode.get("dcmUseCallingAETitle", null));
            rule.setExclusiveUseDefinedTC(ruleNode.getBoolean("dcmExclusiveUseDefinedTC", Boolean.FALSE));
            rule.setCommonName(ruleNode.get("cn", null));
            Schedule schedule = new Schedule();
            schedule.setDays(ruleNode.get("dcmScheduleDays", null));
            schedule.setHours(ruleNode.get("dcmScheduleHours", null));
            rule.setReceiveSchedule(schedule);
            String conversion = ruleNode.get("dcmConversion", null);
            if (conversion != null)
                rule.setConversion(ForwardRule.conversionType.valueOf(conversion));
            rule.setConversionUri(ruleNode.get("dcmConversionUri", null));
            rule.setRemotePixManager(ruleNode.get("dcmRemotePIXManager", null));
            rules.add(rule);
        }
        proxyAE.setForwardRules(rules);
    }

    private static Dimse[] dimseArray(Preferences prefs, String key) {
        int n = prefs.getInt(key + ".#", 0);
        if (n == 0)
            return new Dimse[] {};

        Dimse[] dimse = new Dimse[n];
        for (int i = 0; i < n; i++)
            dimse[i] = Dimse.valueOf(prefs.get(key + '.' + (i + 1), null));
        return dimse;
    }

    private void loadForwardSchedules(ProxyApplicationEntity proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences schedulesNode = paeNode.node("dcmForwardSchedule");
        HashMap<String, Schedule> forwardSchedules = new HashMap<String, Schedule>();
        for (String scheduleIndex : schedulesNode.childrenNames()) {
            Preferences scheduleNode = schedulesNode.node(scheduleIndex);
            Schedule schedule = new Schedule();
            schedule.setDays(scheduleNode.get("dcmForwardScheduleDays", null));
            schedule.setHours(scheduleNode.get("dcmForwardScheduleHours", null));
            forwardSchedules.put(scheduleNode.get("dcmDestinationAETitle", null), schedule);
        }
        proxyAE.setForwardSchedules(forwardSchedules);
    }

    private void loadRetries(ProxyApplicationEntity proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences retriesNode = paeNode.node("dcmRetry");
        List<Retry> retries = new ArrayList<Retry>();
        for (String retryIndex : retriesNode.childrenNames()) {
            Preferences retryNode = retriesNode.node(retryIndex);
            Retry retry = new Retry(
                    RetryObject.valueOf(retryNode.get("dcmRetryObject", null)),
                    retryNode.getInt("dcmRetryDelay", Retry.DEFAULT_DELAY),
                    retryNode.getInt("dcmRetryNum", Retry.DEFAULT_RETRIES));
            retries.add(retry);
        }
        proxyAE.setRetries(retries);
    }

    @Override
    protected void storeChilds(ApplicationEntity ae, Preferences aeNode) {
        super.storeChilds(ae, aeNode);
        if (!(ae instanceof ProxyApplicationEntity))
            return;

        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        storeRetries(proxyAE.getRetries(), aeNode);
        storeForwardSchedules(proxyAE.getForwardSchedules(), aeNode);
        storeForwardRules(proxyAE.getForwardRules(), aeNode);
        store(proxyAE.getAttributeCoercions(), aeNode);
    }

    private void storeForwardRules(List<ForwardRule> forwardRules, Preferences paeNode) {
        Preferences rulesNode = paeNode.node("dcmForwardRule");
        for (ForwardRule rule : forwardRules)
            storeToForwardRule(rule, rulesNode.node(rule.getCommonName()));
    }

    private void storeToForwardRule(ForwardRule rule, Preferences prefs) {
        storeForwardRuleDimse(rule, prefs);
        storeNotEmpty(prefs, "dcmSOPClass", rule.getSopClass().toArray(new String[rule.getSopClass().size()]));
        storeNotNull(prefs, "dcmCallingAETitle", rule.getCallingAET());
        storeNotEmpty(prefs, "labeledURI", rule.getDestinationURI().toArray(new String[rule.getDestinationURI().size()]));
        storeNotNull(prefs, "dcmUseCallingAETitle", rule.getUseCallingAET());
        storeNotDef(prefs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC(), Boolean.FALSE);
        storeNotNull(prefs, "cn", rule.getCommonName());
        storeNotNull(prefs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        storeNotNull(prefs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        storeNotNull(prefs, "dcmConversion", rule.getConversion());
        storeNotNull(prefs, "dcmConversionUri", rule.getConversionUri());
        storeNotNull(prefs, "dcmRemotePIXManager", rule.getRemotePixManager());
    }

    private void storeForwardRuleDimse(ForwardRule rule, Preferences prefs) {
        List<String> dimseList = new ArrayList<String>();
        for (Dimse dimse : rule.getDimse())
            dimseList.add(dimse.toString());
        storeNotEmpty(prefs, "dcmForwardRuleDimse", dimseList.toArray(new String[dimseList.size()]));
    }

    private void storeForwardSchedules(HashMap<String, Schedule> forwardSchedules, Preferences parentNode) {
        Preferences schedulesNode = parentNode.node("dcmForwardSchedule");
        for (Entry<String, Schedule> forwardSchedule : forwardSchedules.entrySet())
            storeToForwardSchedule(forwardSchedule, schedulesNode.node(forwardSchedule.getKey()));
    }

    private void storeToForwardSchedule(Entry<String, Schedule> forwardSchedule, Preferences prefs) {
        storeNotNull(prefs, "dcmForwardScheduleDays", forwardSchedule.getValue().getDays());
        storeNotNull(prefs, "dcmForwardScheduleHours", forwardSchedule.getValue().getHours());
        storeNotNull(prefs, "dcmDestinationAETitle", forwardSchedule.getKey());
    }

    private void storeRetries(List<Retry> retries, Preferences parentNode) {
        Preferences retriesNode = parentNode.node("dcmRetry");
        for (Retry retry : retries)
            storeToRetry(retry, retriesNode.node(retry.getRetryObject().toString()));
    }

    private void storeToRetry(Retry retry, Preferences prefs) {
        storeNotNull(prefs, "dcmRetryObject", retry.getRetryObject().toString());
        storeNotNull(prefs, "dcmRetryDelay", retry.getDelay());
        storeNotNull(prefs, "dcmRetryNum", retry.getNumberOfRetries());
    }

    @Override
    protected void storeDiffs(Preferences prefs, ApplicationEntity a, ApplicationEntity b) {
        super.storeDiffs(prefs, a, b);
        if (!(a instanceof ProxyApplicationEntity) || !(b instanceof ProxyApplicationEntity))
            return;

        ProxyApplicationEntity pa = (ProxyApplicationEntity) a;
        ProxyApplicationEntity pb = (ProxyApplicationEntity) b;
        storeDiff(prefs, "dcmSpoolDirectory", pa.getSpoolDirectory(), pb.getSpoolDirectory());
        storeDiff(prefs, "dcmAcceptDataOnFailedNegotiation", pa.isAcceptDataOnFailedNegotiation(),
                pb.isAcceptDataOnFailedNegotiation());
        storeDiff(prefs, "dcmEnableAuditLog", pa.isEnableAuditLog(), pb.isEnableAuditLog());
    }

    @Override
    protected void storeDiffs(Preferences prefs, Device a, Device b) {
        super.storeDiffs(prefs, a, b);
        if (!(a instanceof ProxyDevice) || !(b instanceof ProxyDevice))
            return;

        ProxyDevice pa = (ProxyDevice) a;
        ProxyDevice pb = (ProxyDevice) b;
        storeDiff(prefs, "dcmSchedulerInterval", pa.getSchedulerInterval(), pb.getSchedulerInterval());
        storeDiff(prefs, "dcmForwardThreads", pa.getForwardThreads(), pb.getForwardThreads());
        storeDiff(prefs, "dcmProxyConfigurationStaleTimeout",
                pa.getConfigurationStaleTimeout(),
                pb.getConfigurationStaleTimeout(),
                0);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, Preferences aeNode)
            throws BackingStoreException {
        super.mergeChilds(prev, ae, aeNode);
        if (!(prev instanceof ProxyApplicationEntity) || !(ae instanceof ProxyApplicationEntity))
            return;

        ProxyApplicationEntity pprev = (ProxyApplicationEntity) prev;
        ProxyApplicationEntity pae = (ProxyApplicationEntity) ae;
        merge(pprev.getAttributeCoercions(), pae.getAttributeCoercions(), aeNode);
        mergeRetries(pprev.getRetries(), pae.getRetries(), aeNode);
        mergeForwardSchedules(pprev.getForwardSchedules(), pae.getForwardSchedules(), aeNode);
        mergeForwardRules(pprev.getForwardRules(), pae.getForwardRules(), aeNode);
    }

    private void mergeForwardRules(List<ForwardRule> prevForwardRules, List<ForwardRule> currForwardRules,
            Preferences aeNode) throws BackingStoreException {
        Preferences forwardRulesNode = aeNode.node("dcmForwardRule");
        Iterator<ForwardRule> prevIter = prevForwardRules.listIterator();
        List<String> currForwardRuleNames = new ArrayList<String>();
        for (ForwardRule rule : currForwardRules)
            currForwardRuleNames.add(rule.getCommonName());
        while (prevIter.hasNext()) {
            String prevForwardRuleName = prevIter.next().getCommonName();
            if (!currForwardRuleNames.contains(prevForwardRuleName))
                forwardRulesNode.node(prevForwardRuleName).removeNode();
        }
        for (ForwardRule rule : currForwardRules) {
            Preferences ruleNode = forwardRulesNode.node(rule.getCommonName());
            if (prevIter.hasNext())
                storeForwardRuleDiffs(ruleNode, prevIter.next(), rule);
            else
                storeToForwardRule(rule, ruleNode);
        }
    }

    private void storeForwardRuleDiffs(Preferences prefs, ForwardRule ruleA, ForwardRule ruleB) {
        List<String> dimseA = new ArrayList<String>();
        for (Dimse dimse : ruleA.getDimse())
            dimseA.add(dimse.toString());
        List<String> dimseB = new ArrayList<String>();
        for (Dimse dimse : ruleB.getDimse())
            dimseB.add(dimse.toString());
        storeDiff(prefs, "dcmForwardRuleDimse", 
                dimseA.toArray(new String[dimseA.size()]), 
                dimseB.toArray(new String[dimseB.size()]));
        storeDiff(prefs, "dcmSOPClass",
                ruleA.getSopClass().toArray(new String[ruleA.getSopClass().size()]), 
                ruleB.getSopClass().toArray(new String[ruleB.getSopClass().size()]));
        storeDiff(prefs, "dcmCallingAETitle", ruleA.getCallingAET(), ruleB.getCallingAET());
        storeDiff(prefs, "labeledURI", 
                ruleA.getDestinationURI().toArray(new String[ruleA.getDestinationURI().size()]), 
                ruleB.getDestinationURI().toArray(new String[ruleB.getDestinationURI().size()]));
        storeDiff(prefs, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(), ruleB.isExclusiveUseDefinedTC());
        storeDiff(prefs, "cn", ruleA.getCommonName(), ruleB.getCommonName());
        storeDiff(prefs, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        storeDiff(prefs, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB.getReceiveSchedule().getDays());
        storeDiff(prefs, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB.getReceiveSchedule().getHours());
        storeDiff(prefs, "dcmConversion", ruleA.getConversion(), ruleB.getConversion());
        storeDiff(prefs, "dcmConversionUri", ruleA.getConversionUri(), ruleB.getConversionUri());
        storeDiff(prefs, "dcmRemotePIXManager", ruleA.getRemotePixManager(), ruleB.getRemotePixManager());
    }

    private void mergeForwardSchedules(HashMap<String, Schedule> prevs, HashMap<String, Schedule> schedules,
            Preferences parentNode) throws BackingStoreException {
        Preferences schedulesNode = parentNode.node("dcmForwardSchedule");
        for (String aet : prevs.keySet())
            if (!schedules.containsKey(aet))
                schedulesNode.node(aet).removeNode();
        for (Entry<String, Schedule> entry : schedules.entrySet()) {
            Preferences scheduleNode = schedulesNode.node(entry.getKey());
            if (prevs.containsKey(entry.getKey()))
                storeScheduleDiffs(scheduleNode, prevs.get(entry.getKey()), entry.getValue());
            else
                storeToForwardSchedule(entry, scheduleNode);
        }
    }

    private void storeScheduleDiffs(Preferences prefs, Schedule a, Schedule b) {
        storeDiff(prefs, "dcmForwardScheduleDays", a.getDays(), b.getDays());
        storeDiff(prefs, "dcmForwardScheduleHours", a.getHours(), b.getHours());
    }

    private void mergeRetries(List<Retry> prevRetries, List<Retry> currRetries, Preferences parentNode)
            throws BackingStoreException {
        Preferences retriesNode = parentNode.node("dcmRetry");
        Iterator<Retry> prevIter = prevRetries.listIterator();
        List<String> currRetryObjects = new ArrayList<String>();
        for (Retry retry : currRetries)
            currRetryObjects.add(retry.getRetryObject().toString());
        while (prevIter.hasNext()) {
            String prevRetryObject = prevIter.next().getRetryObject().toString();
            if (!currRetryObjects.contains(prevRetryObject))
                retriesNode.node(prevRetryObject).removeNode();
        }
        for (Retry retry : currRetries) {
            Preferences retryNode = retriesNode.node(retry.getRetryObject().toString());
            if (prevIter.hasNext())
                storeRetryDiffs(retryNode, prevIter.next(), retry);
            else
                storeToRetry(retry, retryNode);
        }
    }

    private void storeRetryDiffs(Preferences prefs, Retry a, Retry b) {
        storeDiff(prefs, "dcmRetryDelay", a.getDelay(), b.getDelay());
        storeDiff(prefs, "dcmRetryNum", a.getNumberOfRetries(), b.getNumberOfRetries());
    }
}