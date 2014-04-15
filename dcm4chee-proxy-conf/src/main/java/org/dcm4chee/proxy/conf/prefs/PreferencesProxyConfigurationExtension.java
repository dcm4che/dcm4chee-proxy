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

package org.dcm4chee.proxy.conf.prefs;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.dcm4che3.conf.prefs.PreferencesDicomConfigurationExtension;
import org.dcm4che3.conf.prefs.PreferencesUtils;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.conf.Retry;
import org.dcm4chee.proxy.conf.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@gmail.com>
 */
public class PreferencesProxyConfigurationExtension extends PreferencesDicomConfigurationExtension {
    
    public static final Logger LOG = LoggerFactory.getLogger(PreferencesProxyConfigurationExtension.class);

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
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAEE == null)
            return;

        prefs.putBoolean("dcmProxyNetworkAE", true);
        PreferencesUtils.storeNotNull(prefs, "dcmSpoolDirectory", proxyAEE.getSpoolDirectory());
        PreferencesUtils.storeNotNull(prefs, "dcmAcceptDataOnFailedAssociation",
                proxyAEE.isAcceptDataOnFailedAssociation());
        PreferencesUtils.storeNotNull(prefs, "dcmEnableAuditLog", proxyAEE.isEnableAuditLog());
        PreferencesUtils
                .storeNotNull(prefs, "hl7ProxyPIXConsumerApplication", proxyAEE.getProxyPIXConsumerApplication());
        PreferencesUtils
                .storeNotNull(prefs, "hl7RemotePIXManagerApplication", proxyAEE.getRemotePIXManagerApplication());
        PreferencesUtils.storeNotNull(prefs, "dcmDeleteFailedDataWithoutRetryConfiguration",
                proxyAEE.isDeleteFailedDataWithoutRetryConfiguration());
        PreferencesUtils.storeNotNull(prefs, "dcmDestinationAETitle", proxyAEE.getFallbackDestinationAET());
        PreferencesUtils.storeNotNull(prefs, "dcmMergeStgCmtMessagesUsingANDLogic",
                proxyAEE.isMergeStgCmtMessagesUsingANDLogic());
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
        proxyAEE.setAcceptDataOnFailedAssociation(prefs.getBoolean("dcmAcceptDataOnFailedAssociation", false));
        proxyAEE.setEnableAuditLog(prefs.getBoolean("dcmEnableAuditLog", false));
        proxyAEE.setProxyPIXConsumerApplication(prefs.get("hl7ProxyPIXConsumerApplication", null));
        proxyAEE.setRemotePIXManagerApplication(prefs.get("hl7RemotePIXManagerApplication", null));
        proxyAEE.setDeleteFailedDataWithoutRetryConfiguration(prefs.getBoolean(
                "dcmDeleteFailedDataWithoutRetryConfiguration", false));
        proxyAEE.setFallbackDestinationAET(prefs.get("dcmDestinationAETitle", null));
        proxyAEE.setMergeStgCmtMessagesUsingANDLogic(prefs.getBoolean("dcmMergeStgCmtMessagesUsingANDLogic", false));
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, Preferences aeNode) throws BackingStoreException {
        ProxyAEExtension proxyAE = ae.getAEExtension(ProxyAEExtension.class);
        if (proxyAE == null)
            return;

        loadRetries(proxyAE, aeNode);
        loadForwardOptions(proxyAE, aeNode);
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
            rule.setSopClasses(Arrays.asList(PreferencesUtils.stringArray(ruleNode, "dcmSOPClass")));
            rule.setCallingAETs(Arrays.asList(PreferencesUtils.stringArray(ruleNode, "dcmAETitle")));
            rule.setDestinationURIs(Arrays.asList(PreferencesUtils.stringArray(ruleNode, "labeledURI")));
            rule.setUseCallingAET(ruleNode.get("dcmUseCallingAETitle", null));
            rule.setExclusiveUseDefinedTC(ruleNode.getBoolean("dcmExclusiveUseDefinedTC", Boolean.FALSE));
            rule.setCommonName(ruleNode.get("cn", null));
            Schedule schedule = new Schedule();
            schedule.setDays(ruleNode.get("dcmScheduleDays", null));
            schedule.setHours(ruleNode.get("dcmScheduleHours", null));
            rule.setReceiveSchedule(schedule);
            rule.setMpps2DoseSrTemplateURI(ruleNode.get("dcmMpps2DoseSrTemplateURI", null));
            rule.setDoseSrIODTemplateURI(ruleNode.get("doseSrIODTemplateURI", null));
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

    private void loadForwardOptions(ProxyAEExtension proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences fwdOptionsNode = paeNode.node("dcmForwardOption");
        HashMap<String, ForwardOption> fwdOptions = new HashMap<String, ForwardOption>();
        for (String fwdOptionIndex : fwdOptionsNode.childrenNames()) {
            Preferences fwdOptionNode = fwdOptionsNode.node(fwdOptionIndex);
            ForwardOption fwdOption = new ForwardOption();
            fwdOption.setDescription(fwdOptionNode.get("dicomDescription", null));
            fwdOption.setConvertEmf2Sf(fwdOptionNode.getBoolean("dcmConvertEmf2Sf", false));
            Schedule schedule = new Schedule();
            schedule.setDays(fwdOptionNode.get("dcmScheduleDays", null));
            schedule.setHours(fwdOptionNode.get("dcmScheduleHours", null));
            fwdOption.setSchedule(schedule);
            fwdOptions.put(fwdOptionNode.get("dcmDestinationAETitle", null), fwdOption);
        }
        proxyAE.setForwardOptions(fwdOptions);
    }

    private void loadRetries(ProxyAEExtension proxyAE, Preferences paeNode) throws BackingStoreException {
        Preferences retriesNode = paeNode.node("dcmRetry");
        List<Retry> retries = new ArrayList<Retry>();
        for (String retryName : retriesNode.childrenNames()) {
            Preferences retryNode = retriesNode.node(retryName);
            Retry retry = new Retry(
                            RetryObject.valueOf(retryName), 
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
        storeForwardOptions(proxyAE.getForwardOptions(), aeNode);
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
                rule.getSopClasses().toArray(new String[rule.getSopClasses().size()]));
        PreferencesUtils.storeNotEmpty(prefs, "dcmAETitle", 
                rule.getCallingAETs().toArray(new String[rule.getCallingAETs().size()]));
        PreferencesUtils.storeNotEmpty(prefs, "labeledURI",
                rule.getDestinationURI().toArray(new String[rule.getDestinationURI().size()]));
        PreferencesUtils.storeNotNull(prefs, "dcmUseCallingAETitle", rule.getUseCallingAET());
        PreferencesUtils.storeNotDef(prefs, "dcmExclusiveUseDefinedTC", rule.isExclusiveUseDefinedTC(), Boolean.FALSE);
        PreferencesUtils.storeNotNull(prefs, "cn", rule.getCommonName());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleDays", rule.getReceiveSchedule().getDays());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleHours", rule.getReceiveSchedule().getHours());
        PreferencesUtils.storeNotNull(prefs, "dcmMpps2DoseSrTemplateURI", rule.getMpps2DoseSrTemplateURI());
        PreferencesUtils.storeNotNull(prefs, "doseSrIODTemplateURI", rule.getDoseSrIODTemplateURI());
        PreferencesUtils.storeNotDef(prefs, "dcmPIXQuery", rule.isRunPIXQuery(), Boolean.FALSE);
        PreferencesUtils.storeNotNull(prefs, "dicomDescription", rule.getDescription());
    }

    private void storeForwardRuleDimse(ForwardRule rule, Preferences prefs) {
        List<String> dimseList = new ArrayList<String>();
        for (Dimse dimse : rule.getDimse())
            dimseList.add(dimse.toString());
        PreferencesUtils.storeNotEmpty(prefs, "dcmForwardRuleDimse", dimseList.toArray(new String[dimseList.size()]));
    }

    private void storeForwardOptions(HashMap<String, ForwardOption> fwdOptions, Preferences parentNode) {
        Preferences fwdOptionsNode = parentNode.node("dcmForwardOption");
        for (Entry<String, ForwardOption> fwdOptionEntry : fwdOptions.entrySet())
            storeToForwardOption(fwdOptionEntry, fwdOptionsNode.node(fwdOptionEntry.getKey()));
    }

    private void storeToForwardOption(Entry<String, ForwardOption> fwdOptionEntry, Preferences prefs) {
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleDays", fwdOptionEntry.getValue().getSchedule().getDays());
        PreferencesUtils.storeNotNull(prefs, "dcmScheduleHours", fwdOptionEntry.getValue().getSchedule().getHours());
        PreferencesUtils.storeNotNull(prefs, "dicomDescription", fwdOptionEntry.getValue().getDescription());
        PreferencesUtils.storeNotNull(prefs, "dcmConvertEmf2Sf", fwdOptionEntry.getValue().isConvertEmf2Sf());
        PreferencesUtils.storeNotNull(prefs, "dcmDestinationAETitle", fwdOptionEntry.getKey());
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
        PreferencesUtils.storeDiff(prefs, "dcmAcceptDataOnFailedAssociation", pa.isAcceptDataOnFailedAssociation(),
                pb.isAcceptDataOnFailedAssociation());
        PreferencesUtils.storeDiff(prefs, "dcmEnableAuditLog", pa.isEnableAuditLog(), pb.isEnableAuditLog());
        PreferencesUtils.storeDiff(prefs, "hl7ProxyPIXConsumerApplication", pa.getProxyPIXConsumerApplication(),
                pb.getProxyPIXConsumerApplication());
        PreferencesUtils.storeDiff(prefs, "hl7RemotePIXManagerApplication", pa.getRemotePIXManagerApplication(),
                pb.getRemotePIXManagerApplication());
        PreferencesUtils.storeDiff(prefs, "dcmDeleteFailedDataWithoutRetryConfiguration",
                pa.isDeleteFailedDataWithoutRetryConfiguration(), pb.isDeleteFailedDataWithoutRetryConfiguration());
        PreferencesUtils.storeDiff(prefs, "dcmDestinationAETitle", pa.getFallbackDestinationAET(),
                pb.getFallbackDestinationAET());
        PreferencesUtils.storeDiff(prefs, "dcmMergeStgCmtMessagesUsingANDLogic",
                pa.isMergeStgCmtMessagesUsingANDLogic(), pb.isMergeStgCmtMessagesUsingANDLogic());
    }

    @Override
    protected void storeDiffs(Device a, Device b, Preferences prefs) {
        ProxyDeviceExtension pa = a.getDeviceExtension(ProxyDeviceExtension.class);
        ProxyDeviceExtension pb = b.getDeviceExtension(ProxyDeviceExtension.class);
        if (pa == null || pb == null)
            return;

        PreferencesUtils.storeDiff(prefs, "dcmSchedulerInterval", pa.getSchedulerInterval(), pb.getSchedulerInterval());
        PreferencesUtils.storeDiff(prefs, "dcmForwardThreads", pa.getForwardThreads(), pb.getForwardThreads());
        PreferencesUtils.storeDiff(prefs, "dcmProxyConfigurationStaleTimeout", pa.getConfigurationStaleTimeout(),
                pb.getConfigurationStaleTimeout(), 0);
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
        mergeForwardOptions(pprev.getForwardOptions(), pae.getForwardOptions(), aePrefs);
        mergeForwardRules(pprev.getForwardRules(), pae.getForwardRules(), aePrefs);
    }

    private void mergeForwardRules(List<ForwardRule> prevForwardRules, List<ForwardRule> currForwardRules,
            Preferences aeNode) throws BackingStoreException {
        Preferences forwardRulesNode = aeNode.node("dcmForwardRule");
        HashMap<String, ForwardRule> prevFwdRuleMap = new HashMap<String, ForwardRule>();
        for (ForwardRule rule : prevForwardRules)
            prevFwdRuleMap.put(rule.getCommonName(), rule);
        for (ForwardRule rule : currForwardRules) {
            Preferences ruleNode = forwardRulesNode.node(rule.getCommonName());
            if (prevFwdRuleMap.containsKey(rule.getCommonName()))
                storeForwardRuleDiffs(ruleNode, prevFwdRuleMap.get(rule.getCommonName()), rule);
            else
                storeToForwardRule(rule, ruleNode);
        }
        HashMap<String, ForwardRule> currFwdRulesMap = new HashMap<String, ForwardRule>();
        for (ForwardRule rule : currForwardRules)
            currFwdRulesMap.put(rule.getCommonName(), rule);
        for (ForwardRule rule : prevForwardRules) {
            if (!currFwdRulesMap.containsKey(rule.getCommonName()))
                forwardRulesNode.node(rule.getCommonName()).removeNode();
        }
    }

    private void storeForwardRuleDiffs(Preferences prefs, ForwardRule ruleA, ForwardRule ruleB) {
        List<String> dimseA = new ArrayList<String>();
        for (Dimse dimse : ruleA.getDimse())
            dimseA.add(dimse.toString());
        List<String> dimseB = new ArrayList<String>();
        for (Dimse dimse : ruleB.getDimse())
            dimseB.add(dimse.toString());
        PreferencesUtils.storeDiff(prefs, "dcmForwardRuleDimse", dimseA.toArray(new String[dimseA.size()]),
                dimseB.toArray(new String[dimseB.size()]));
        PreferencesUtils.storeDiff(prefs, "dcmSOPClass",
                ruleA.getSopClasses().toArray(new String[ruleA.getSopClasses().size()]),
                ruleB.getSopClasses().toArray(new String[ruleB.getSopClasses().size()]));
        PreferencesUtils.storeDiff(prefs, "dcmAETitle", 
                ruleA.getCallingAETs().toArray(new String[ruleA.getCallingAETs().size()]), 
                ruleB.getCallingAETs().toArray(new String[ruleB.getCallingAETs().size()]));
        PreferencesUtils.storeDiff(prefs, "labeledURI",
                ruleA.getDestinationURI().toArray(new String[ruleA.getDestinationURI().size()]), ruleB
                        .getDestinationURI().toArray(new String[ruleB.getDestinationURI().size()]));
        PreferencesUtils.storeDiff(prefs, "dcmExclusiveUseDefinedTC", ruleA.isExclusiveUseDefinedTC(),
                ruleB.isExclusiveUseDefinedTC());
        PreferencesUtils.storeDiff(prefs, "cn", ruleA.getCommonName(), ruleB.getCommonName());
        PreferencesUtils.storeDiff(prefs, "dcmUseCallingAETitle", ruleA.getUseCallingAET(), ruleB.getUseCallingAET());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleDays", ruleA.getReceiveSchedule().getDays(), ruleB
                .getReceiveSchedule().getDays());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleHours", ruleA.getReceiveSchedule().getHours(), ruleB
                .getReceiveSchedule().getHours());
        PreferencesUtils.storeDiff(prefs, "dcmMpps2DoseSrTemplateURI", ruleA.getMpps2DoseSrTemplateURI(),
                ruleB.getMpps2DoseSrTemplateURI());
        PreferencesUtils.storeDiff(prefs, "doseSrIODTemplateURI", ruleA.getDoseSrIODTemplateURI(),
                ruleB.getDoseSrIODTemplateURI());
        PreferencesUtils.storeDiff(prefs, "dcmPIXQuery", ruleA.isRunPIXQuery(), ruleB.isRunPIXQuery());
        PreferencesUtils.storeDiff(prefs, "dicomDescription", ruleA.getDescription(), ruleB.getDescription());
    }

    private void mergeForwardOptions(HashMap<String, ForwardOption> prevOptions, HashMap<String, ForwardOption> currOptions,
            Preferences parentNode) throws BackingStoreException {
        Preferences fwdOptionsNode = parentNode.node("dcmForwardOption");
        for (Entry<String, ForwardOption> entry : currOptions.entrySet()) {
            String destinationAET = entry.getKey();
            Preferences fwdOptionNode = fwdOptionsNode.node(destinationAET);
            if (prevOptions.containsKey(destinationAET))
                storeForwardOptionDiffs(fwdOptionNode, prevOptions.get(destinationAET), entry.getValue());
            else
                storeToForwardOption(entry, fwdOptionNode);
        }
        for (Entry<String, ForwardOption> entry : prevOptions.entrySet()) {
            if (!currOptions.containsKey(entry.getKey()))
                fwdOptionsNode.node(entry.getKey()).removeNode();
        }
    }

    private void storeForwardOptionDiffs(Preferences prefs, ForwardOption a, ForwardOption b) {
        PreferencesUtils.storeDiff(prefs, "dcmScheduleDays", a.getSchedule().getDays(), b.getSchedule().getDays());
        PreferencesUtils.storeDiff(prefs, "dcmScheduleHours", a.getSchedule().getHours(), b.getSchedule().getHours());
        PreferencesUtils.storeDiff(prefs, "dicomDescription", a.getDescription(), b.getDescription());
        PreferencesUtils.storeDiff(prefs, "dcmConvertEmf2Sf", a.isConvertEmf2Sf(), b.isConvertEmf2Sf());
    }

    private void mergeRetries(List<Retry> prevRetries, List<Retry> currRetries, Preferences parentNode)
            throws BackingStoreException {
        Preferences retriesNode = parentNode.node("dcmRetry");
        HashMap<String, Retry> prevRetriesMap = new HashMap<String, Retry>();
        for (Retry retry : prevRetries)
            prevRetriesMap.put(retry.getRetryObject().toString(), retry);
        for (Retry retry : currRetries) {
            String retryObject = retry.getRetryObject().toString();
            Preferences retryNode = retriesNode.node(retryObject);
            if (prevRetriesMap.containsKey(retryObject))
                storeRetryDiffs(retryNode, prevRetriesMap.get(retryObject), retry);
            else
                storeToRetry(retry, retryNode);
        }
        HashMap<String, Retry> currRetriesMap = new HashMap<String, Retry>();
        for (Retry retry : currRetries)
            currRetriesMap.put(retry.getRetryObject().toString(), retry);
        for (Retry retry : prevRetries) {
            String retryObject = retry.getRetryObject().toString();
            if (!currRetriesMap.containsKey(retryObject))
                retriesNode.node(retryObject).removeNode();
        }
    }

    private void storeRetryDiffs(Preferences prefs, Retry a, Retry b) {
        PreferencesUtils.storeDiff(prefs, "dcmRetryDelay", a.getDelay(), b.getDelay());
        PreferencesUtils.storeDiff(prefs, "dcmRetryNum", a.getNumberOfRetries(), b.getNumberOfRetries());
        PreferencesUtils.storeDiff(prefs, "dcmDeleteAfterFinalRetry", a.isDeleteAfterFinalRetry(),
                b.isDeleteAfterFinalRetry());
    }
}