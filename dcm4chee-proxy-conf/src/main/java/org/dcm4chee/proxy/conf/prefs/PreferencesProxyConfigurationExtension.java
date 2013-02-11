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

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.dcm4che.conf.prefs.PreferencesDicomConfigurationExtension;
import org.dcm4che.conf.prefs.PreferencesUtils;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7ConfigurationExtension;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ForwardSchedule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.conf.ProxyHL7ApplicationExtension;
import org.dcm4chee.proxy.conf.Retry;
import org.dcm4chee.proxy.conf.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@gmail.com>
 */
public class PreferencesProxyConfigurationExtension extends PreferencesDicomConfigurationExtension implements
        PreferencesHL7ConfigurationExtension {

    @Override
    protected void storeTo(Device device, Preferences prefs) {
        ProxyDeviceExtension proxyDev = device.getDeviceExtension(ProxyDeviceExtension.class);
        if (proxyDev == null)
            return;

        prefs.putBoolean("dcmProxyDevice", true);
        PreferencesUtils.storeNotNull(prefs, "dcmSchedulerInterval", proxyDev.getSchedulerInterval());
        PreferencesUtils.storeNotNull(prefs, "dcmForwardThreads", proxyDev.getForwardThreads());
        PreferencesUtils.storeNotDef(prefs, "dcmProxyConfigurationStaleTimeout",
                proxyDev.getConfigurationStaleTimeout(), 0);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Preferences prefs) {
        ProxyAEExtension proxyAE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAE == null)
            return;

        prefs.putBoolean("dcmProxyNetworkAE", true);
        PreferencesUtils.storeNotNull(prefs, "dcmSpoolDirectory", proxyAE.getSpoolDirectory());
        PreferencesUtils.storeNotNull(prefs, "dcmAcceptDataOnFailedNegotiation",
                proxyAE.isAcceptDataOnFailedNegotiation());
        PreferencesUtils.storeNotNull(prefs, "dcmEnableAuditLog", proxyAE.isEnableAuditLog());
        PreferencesUtils
                .storeNotNull(prefs, "hl7ProxyPIXConsumerApplication", proxyAE.getProxyPIXConsumerApplication());
        PreferencesUtils
                .storeNotNull(prefs, "hl7RemotePIXManagerApplication", proxyAE.getRemotePIXManagerApplication());
        PreferencesUtils.storeNotNull(prefs, "dcmDeleteFailedDataWithoutRetryConfiguration",
                proxyAE.isDeleteFailedDataWithoutRetryConfiguration());
        PreferencesUtils.storeNotNull(prefs, "dcmDestinationAETitle", proxyAE.getFallbackDestinationAET());
    }

    @Override
    public void storeTo(HL7Application hl7App, Preferences prefs) {
        ProxyHL7ApplicationExtension prxHL7App = hl7App.getHL7ApplicationExtension(ProxyHL7ApplicationExtension.class);
        if (prxHL7App == null)
            return;

        prefs.putBoolean("dcmProxyHL7Application", true);
        PreferencesUtils.storeNotEmpty(prefs, "labeledURI", prxHL7App.getTemplatesURIs());
    }

    @Override
    protected void loadFrom(Device device, Preferences prefs) throws CertificateException, BackingStoreException {
        if (!prefs.getBoolean("dcmProxyDevice", false))
            return;

        ProxyDeviceExtension proxyDev = new ProxyDeviceExtension();
        device.addDeviceExtension(proxyDev);
        proxyDev.setSchedulerInterval(prefs.getInt("dcmSchedulerInterval",
                ProxyDeviceExtension.DEFAULT_SCHEDULER_INTERVAL));
        proxyDev.setForwardThreads(prefs.getInt("dcmForwardThreads", ProxyDeviceExtension.DEFAULT_FORWARD_THREADS));
        proxyDev.setConfigurationStaleTimeout(prefs.getInt("dcmProxyConfigurationStaleTimeout", 0));
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Preferences prefs) {
        if (!prefs.getBoolean("dcmProxyNetworkAE", false))
            return;

        ProxyAEExtension proxyAEE = new ProxyAEExtension();
        ae.addAEExtension(proxyAEE);
        proxyAEE.setSpoolDirectory(prefs.get("dcmSpoolDirectory", null));
        proxyAEE.setAcceptDataOnFailedNegotiation(prefs.getBoolean("dcmAcceptDataOnFailedNegotiation", false));
        proxyAEE.setEnableAuditLog(prefs.getBoolean("dcmEnableAuditLog", false));
        proxyAEE.setProxyPIXConsumerApplication(prefs.get("hl7ProxyPIXConsumerApplication", null));
        proxyAEE.setRemotePIXManagerApplication(prefs.get("hl7RemotePIXManagerApplication", null));
        proxyAEE.setDeleteFailedDataWithoutRetryConfiguration(prefs.getBoolean(
                "dcmDeleteFailedDataWithoutRetryConfiguration", false));
        proxyAEE.setFallbackDestinationAET(prefs.get("dcmDestinationAETitle", null));
    }

    @Override
    public void loadFrom(HL7Application hl7App, Preferences prefs) {
        if (!prefs.getBoolean("dcmProxyHL7Application", false))
            return;

        ProxyHL7ApplicationExtension proxyHL7App = new ProxyHL7ApplicationExtension();
        hl7App.addHL7ApplicationExtension(proxyHL7App);
        proxyHL7App.setTemplatesURIs(PreferencesUtils.stringArray(prefs, "labeledURI"));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, Preferences aeNode) throws BackingStoreException {
        ProxyAEExtension proxyAE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAE == null)
            return;

        loadRetries(proxyAE, aeNode);
        loadForwardSchedules(proxyAE, aeNode);
        loadForwardRules(proxyAE, aeNode);
        config.load(proxyAE.getAttributeCoercions(), aeNode);
    }

    private void loadForwardRules(ProxyAEExtension proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences rulesNode = paeNode.node("dcmForwardRule");
        List<ForwardRule> rules = new ArrayList<ForwardRule>();
        for (String ruleName : rulesNode.childrenNames()) {
            Preferences ruleNode = rulesNode.node(ruleName);
            ForwardRule rule = new ForwardRule();
            rule.setDimse(Arrays.asList(dimseArray(ruleNode, "dcmForwardRuleDimse")));
            rule.setSopClass(Arrays.asList(PreferencesUtils.stringArray(ruleNode, "dcmSOPClass")));
            rule.setCallingAET(ruleNode.get("dcmCallingAETitle", null));
            rule.setDestinationURIs(Arrays.asList(PreferencesUtils.stringArray(ruleNode, "labeledURI")));
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
            rule.setRunPIXQuery(ruleNode.getBoolean("dcmPIXQuery", Boolean.FALSE));
            rule.setDescription(ruleNode.get("dicomDescription", null));
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

    private void loadForwardSchedules(ProxyAEExtension proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences schedulesNode = paeNode.node("dcmForwardSchedule");
        HashMap<String, ForwardSchedule> fwdSchedules = new HashMap<String, ForwardSchedule>();
        for (String scheduleIndex : schedulesNode.childrenNames()) {
            Preferences scheduleNode = schedulesNode.node(scheduleIndex);
            ForwardSchedule fwdSchedule = new ForwardSchedule();
            fwdSchedule.setDestinationAET(scheduleNode.get("dcmDestinationAETitle", null));
            Schedule schedule = new Schedule();
            schedule.setDays(scheduleNode.get("dcmScheduleDays", null));
            schedule.setHours(scheduleNode.get("dcmScheduleHours", null));
            fwdSchedule.setSchedule(schedule);
            fwdSchedule.setDescription(scheduleNode.get("dicomDescription", null));
            fwdSchedules.put(fwdSchedule.getDestinationAET(), fwdSchedule);
        }
        proxyAE.setForwardSchedules(fwdSchedules);
    }

    private void loadRetries(ProxyAEExtension proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences retriesNode = paeNode.node("dcmRetry");
        List<Retry> retries = new ArrayList<Retry>();
        for (String retryIndex : retriesNode.childrenNames()) {
            Preferences retryNode = retriesNode.node(retryIndex);
            Retry retry = new Retry(
                    RetryObject.valueOf(
                            retryNode.get("dcmRetryObject", null)),
                            retryNode.getInt("dcmRetryDelay", Retry.DEFAULT_DELAY),
                            retryNode.getInt("dcmRetryNum", Retry.DEFAULT_RETRIES),
                            retryNode.getBoolean("dcmDeleteAfterFinalRetry", false));
            retries.add(retry);
        }
        proxyAE.setRetries(retries);
    }

    @Override
    protected void storeChilds(ApplicationEntity ae, Preferences aeNode) {
        ProxyAEExtension proxyAE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAE == null)
            return;

        storeRetries(proxyAE.getRetries(), aeNode);
        storeForwardSchedules(proxyAE.getForwardSchedules().values(), aeNode);
        storeForwardRules(proxyAE.getForwardRules(), aeNode);
        config.store(proxyAE.getAttributeCoercions(), aeNode);
    }

    private void storeForwardRules(List<ForwardRule> forwardRules, Preferences paeNode) {
        Preferences rulesNode = paeNode.node("dcmForwardRule");
        for (ForwardRule rule : forwardRules)
            storeToForwardRule(rule, rulesNode.node(rule.getCommonName()));
    }

    private void storeToForwardRule(ForwardRule rule, Preferences prefs) {
        storeForwardRuleDimse(rule, prefs);
        PreferencesUtils.storeNotEmpty(prefs, "dcmSOPClass",
                rule.getSopClass().toArray(new String[rule.getSopClass().size()]));
        PreferencesUtils.storeNotNull(prefs, "dcmCallingAETitle", rule.getCallingAET());
        PreferencesUtils.storeNotEmpty(prefs, "labeledURI",
                rule.getDestinationURI().toArray(new String[rule.getDestinationURI().size()]));
        PreferencesUtils.storeNotNull(prefs, "dcmUseCallingAETitle", rule.getUseCallingAET());
        PreferencesUtils.storeNotDef(prefs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC(), Boolean.FALSE);
        PreferencesUtils.storeNotNull(prefs, "cn", rule.getCommonName());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        PreferencesUtils.storeNotNull(prefs, "dcmConversion", rule.getConversion());
        PreferencesUtils.storeNotNull(prefs, "dcmConversionUri", rule.getConversionUri());
        PreferencesUtils.storeNotDef(prefs, "dcmPIXQuery", rule.isRunPIXQuery(), Boolean.FALSE);
        PreferencesUtils.storeNotNull(prefs, "dicomDescription", rule.getDescription());
    }

    private void storeForwardRuleDimse(ForwardRule rule, Preferences prefs) {
        List<String> dimseList = new ArrayList<String>();
        for (Dimse dimse : rule.getDimse())
            dimseList.add(dimse.toString());
        PreferencesUtils.storeNotEmpty(prefs, "dcmForwardRuleDimse", dimseList.toArray(new String[dimseList.size()]));
    }

    private void storeForwardSchedules(Collection<ForwardSchedule> fwdSchedules, Preferences parentNode) {
        Preferences schedulesNode = parentNode.node("dcmForwardSchedule");
        for (ForwardSchedule forwardSchedule : fwdSchedules)
            storeToForwardSchedule(forwardSchedule, schedulesNode.node(forwardSchedule.getDestinationAET()));
    }

    private void storeToForwardSchedule(ForwardSchedule forwardSchedule, Preferences prefs) {
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleDays", forwardSchedule.getSchedule().getDays());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleHours", forwardSchedule.getSchedule().getHours());
        PreferencesUtils.storeNotNull(prefs, "dcmDestinationAETitle", forwardSchedule.getDestinationAET());
        PreferencesUtils.storeNotNull(prefs, "dicomDescription", forwardSchedule.getDescription());
    }

    private void storeRetries(List<Retry> retries, Preferences parentNode) {
        Preferences retriesNode = parentNode.node("dcmRetry");
        for (Retry retry : retries)
            storeToRetry(retry, retriesNode.node(retry.getRetryObject().toString()));
    }

    private void storeToRetry(Retry retry, Preferences prefs) {
        PreferencesUtils.storeNotNull(prefs, "dcmRetryObject", retry.getRetryObject().toString());
        PreferencesUtils.storeNotNull(prefs, "dcmRetryDelay", retry.getDelay());
        PreferencesUtils.storeNotNull(prefs, "dcmRetryNum", retry.getNumberOfRetries());
        PreferencesUtils.storeNotNull(prefs, "dcmDeleteAfterFinalRetry", retry.isDeleteAfterFinalRetry());
    }

    @Override
    protected void storeDiffs(ApplicationEntity a, ApplicationEntity b, Preferences prefs) {
        ProxyAEExtension pa = a.getAEExtension(ProxyAEExtension.class);
        ProxyAEExtension pb = b.getAEExtension(ProxyAEExtension.class);
        if (pa == null || pb == null)
            return;

        PreferencesUtils.storeDiff(prefs, "dcmSpoolDirectory", pa.getSpoolDirectory(), pb.getSpoolDirectory());
        PreferencesUtils.storeDiff(prefs, "dcmAcceptDataOnFailedNegotiation", pa.isAcceptDataOnFailedNegotiation(),
                pb.isAcceptDataOnFailedNegotiation());
        PreferencesUtils.storeDiff(prefs, "dcmEnableAuditLog", pa.isEnableAuditLog(), pb.isEnableAuditLog());
        PreferencesUtils.storeDiff(prefs, "hl7ProxyPIXConsumerApplication", pa.getProxyPIXConsumerApplication(),
                pb.getProxyPIXConsumerApplication());
        PreferencesUtils.storeDiff(prefs, "hl7RemotePIXManagerApplication", pa.getRemotePIXManagerApplication(),
                pb.getRemotePIXManagerApplication());
        PreferencesUtils.storeDiff(prefs, "dcmDeleteFailedDataWithoutRetryConfiguration", 
                pa.isDeleteFailedDataWithoutRetryConfiguration(),
                pb.isDeleteFailedDataWithoutRetryConfiguration());
        PreferencesUtils.storeDiff(prefs, "dcmDestinationAETitle", pa.getFallbackDestinationAET(), pb.getFallbackDestinationAET());
    }

    @Override
    protected void storeDiffs(Device a, Device b, Preferences prefs) {
        ProxyDeviceExtension pa = a.getDeviceExtension(ProxyDeviceExtension.class);
        ProxyDeviceExtension pb = b.getDeviceExtension(ProxyDeviceExtension.class);
        if (pa == null || pb == null)
            return;

        PreferencesUtils.storeDiff(prefs, "dcmSchedulerInterval", pa.getSchedulerInterval(), pb.getSchedulerInterval());
        PreferencesUtils.storeDiff(prefs, "dcmForwardThreads", pa.getForwardThreads(), pb.getForwardThreads());
        PreferencesUtils.storeDiff(prefs, "dcmProxyConfigurationStaleTimeout", 
                pa.getConfigurationStaleTimeout(),
                pb.getConfigurationStaleTimeout(), 
                0);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, Preferences aePrefs)
            throws BackingStoreException {
        ProxyAEExtension pprev = prev.getAEExtension(ProxyAEExtension.class);
        ProxyAEExtension pae = ae.getAEExtension(ProxyAEExtension.class);
        if (pprev == null || pae == null)
            return;

        config.merge(pprev.getAttributeCoercions(), pae.getAttributeCoercions(), aePrefs);
        mergeRetries(pprev.getRetries(), pae.getRetries(), aePrefs);
        mergeForwardSchedules(pprev.getForwardSchedules().values(), pae.getForwardSchedules().values(), aePrefs);
        mergeForwardRules(pprev.getForwardRules(), pae.getForwardRules(), aePrefs);
    }

    @Override
    public void storeDiffs(HL7Application a, HL7Application b, Preferences prefs) {
        ProxyHL7ApplicationExtension pa = a.getHL7ApplicationExtension(ProxyHL7ApplicationExtension.class);
        ProxyHL7ApplicationExtension pb = b.getHL7ApplicationExtension(ProxyHL7ApplicationExtension.class);
        if (pa == null || pb == null)
            return;

        PreferencesUtils.storeDiff(prefs, "labeledURI", pa.getTemplatesURIs(), pb.getTemplatesURIs());
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
        PreferencesUtils.storeDiff(prefs, "dcmForwardRuleDimse", 
                dimseA.toArray(new String[dimseA.size()]), 
                dimseB.toArray(new String[dimseB.size()]));
        PreferencesUtils.storeDiff(prefs, "dcmSOPClass",
                ruleA.getSopClass().toArray(new String[ruleA.getSopClass().size()]), 
                ruleB.getSopClass().toArray(new String[ruleB.getSopClass().size()]));
        PreferencesUtils.storeDiff(prefs, "dcmCallingAETitle", ruleA.getCallingAET(), ruleB.getCallingAET());
        PreferencesUtils.storeDiff(prefs, "labeledURI", 
                ruleA.getDestinationURI().toArray(new String[ruleA.getDestinationURI().size()]), 
                ruleB.getDestinationURI().toArray(new String[ruleB.getDestinationURI().size()]));
        PreferencesUtils.storeDiff(prefs, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(), ruleB.isExclusiveUseDefinedTC());
        PreferencesUtils.storeDiff(prefs, "cn", ruleA.getCommonName(), ruleB.getCommonName());
        PreferencesUtils.storeDiff(prefs, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB.getReceiveSchedule().getDays());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB.getReceiveSchedule().getHours());
        PreferencesUtils.storeDiff(prefs, "dcmConversion", ruleA.getConversion(), ruleB.getConversion());
        PreferencesUtils.storeDiff(prefs, "dcmConversionUri", ruleA.getConversionUri(), ruleB.getConversionUri());
        PreferencesUtils.storeDiff(prefs, "dcmPIXQuery", ruleA.isRunPIXQuery(), ruleB.isRunPIXQuery());
        PreferencesUtils.storeDiff(prefs, "dicomDescription", ruleA.getDescription(), ruleB.getDescription());
    }

    private void mergeForwardSchedules(Collection<ForwardSchedule> prevSchedules,
            Collection<ForwardSchedule> currSchedules, Preferences parentNode) throws BackingStoreException {
        Preferences schedulesNode = parentNode.node("dcmForwardSchedule");
        Iterator<ForwardSchedule> prevIter = prevSchedules.iterator();
        List<String> currFwdScheduleNames = new ArrayList<String>();
        for (ForwardSchedule fwdSchedule : currSchedules)
            currFwdScheduleNames.add(fwdSchedule.getDestinationAET());
        while (prevIter.hasNext()) {
            String prevFwdRuleName = prevIter.next().getDestinationAET();
            if (!currFwdScheduleNames.contains(prevFwdRuleName))
                schedulesNode.node(prevFwdRuleName).removeNode();
        }
        for (ForwardSchedule fwdSchedule : currSchedules) {
            Preferences scheduleNode = schedulesNode.node(fwdSchedule.getDestinationAET());
            if (prevIter.hasNext())
                storeForwardScheduleDiffs(scheduleNode, prevIter.next(), fwdSchedule);
            else
                storeToForwardSchedule(fwdSchedule, scheduleNode);
        }
    }

    private void storeForwardScheduleDiffs(Preferences prefs, ForwardSchedule a, ForwardSchedule b) {
        PreferencesUtils.storeDiff(prefs, "dcmDestinationAETitle", a.getDestinationAET(), b.getDestinationAET());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleDays", a.getSchedule().getDays(), b.getSchedule().getDays());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleHours", a.getSchedule().getHours(), b.getSchedule().getHours());
        PreferencesUtils.storeDiff(prefs, "dicomDescription", a.getDescription(), b.getDescription());
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
        PreferencesUtils.storeDiff(prefs, "dcmRetryDelay", a.getDelay(), b.getDelay());
        PreferencesUtils.storeDiff(prefs, "dcmRetryNum", a.getNumberOfRetries(), b.getNumberOfRetries());
        PreferencesUtils.storeDiff(prefs, "dcmDeleteAfterFinalRetry", a.isDeleteAfterFinalRetry(), b.isDeleteAfterFinalRetry());
    }
}