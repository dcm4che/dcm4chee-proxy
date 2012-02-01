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

package org.dcm4chee.proxy.mc.conf.prefs;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.dcm4chee.proxy.mc.net.Retry;
import org.dcm4chee.proxy.mc.net.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@gmail.com>
 */
public class PreferencesProxyConfiguration extends PreferencesDicomConfiguration {

    public PreferencesProxyConfiguration(Preferences rootPrefs) {
        super(rootPrefs);
        setConfigurationRoot("org/dcm4chee/proxy");
    }

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
        storeNotNull(prefs, "dcmDestinationAETitle", proxyAE.getDestinationAETitle());
        storeNotNull(prefs, "dcmUseCallingAETitle", proxyAE.getUseCallingAETitle());
        storeNotNull(prefs, "dcmExclusiveUseDefinedTC", proxyAE.isExclusiveUseDefinedTC());
        storeNotNull(prefs, "dcmEnableAuditLog", proxyAE.isEnableAuditLog());
        storeNotNull(prefs, "dcmAuditDirectory", proxyAE.getAuditDirectory());
        storeNotNull(prefs, "dcmNactionDirectory", proxyAE.getNactionDirectory());
        storeNotNull(prefs, "dcmNeventDirectory", proxyAE.getNeventDirectory());
        storeNotNull(prefs, "dcmIgnoreScheduleSOPClasses", proxyAE.getIgnoreScheduleSOPClassesAsString());
        Schedule schedule = proxyAE.getForwardSchedule();
        storeNotNull(prefs, "dcmForwardScheduleDays", schedule.getDays());
        storeNotNull(prefs, "dcmForwardScheduleHours", schedule.getHours());
    }
    
    @Override
    protected void loadFrom(Device device, Preferences prefs) throws CertificateException {
            super.loadFrom(device, prefs);
            if (!(device instanceof ProxyDevice))
                return;
            
            ProxyDevice proxyDev = (ProxyDevice) device;
            proxyDev.setSchedulerInterval(prefs.getInt("dcmSchedulerInterval", 60));
    }
    
    @Override
    protected void loadFrom(ApplicationEntity ae, Preferences prefs) {
        super.loadFrom(ae, prefs);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        proxyAE.setSpoolDirectory(prefs.get("dcmSpoolDirectory", null));
        proxyAE.setAcceptDataOnFailedNegotiation(prefs.getBoolean("dcmAcceptDataOnFailedNegotiation", false));
        proxyAE.setDestinationAETitle(prefs.get("dcmDestinationAETitle", null));
        proxyAE.setUseCallingAETitle(prefs.get("dcmUseCallingAETitle", null));
        proxyAE.setAcceptDataOnFailedNegotiation(prefs.getBoolean("dcmExclusiveUseDefinedTC", false));
        proxyAE.setEnableAuditLog(prefs.getBoolean("dcmEnableAuditLog", false));
        proxyAE.setAuditDirectory(prefs.get("dcmAuditDirectory", null));
        proxyAE.setNactionDirectory(prefs.get("dcmNactionDirectory", null));
        proxyAE.setNeventDirectory(prefs.get("dcmNeventDirectory", null));
        proxyAE.setIgnoreScheduleSOPClasses(prefs.get("dcmIgnoreScheduleSOPClasses", null));
        Schedule schedule = new Schedule();
        schedule.setDays(prefs.get("dcmForwardScheduleDays", null));
        schedule.setHours(prefs.get("dcmForwardScheduleHours", null));
        proxyAE.setForwardSchedule(schedule);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, Preferences aeNode)
            throws BackingStoreException {
        super.loadChilds(ae, aeNode);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        loadRetries(proxyAE, aeNode);
        load(proxyAE.getAttributeCoercions(), aeNode);
    }
    
    private void loadRetries(ProxyApplicationEntity proxyAE, Preferences paeNode) 
    throws BackingStoreException {
        Preferences retriesNode = paeNode.node("dcmRetry");
        List<Retry> retries = new ArrayList<Retry>();
        for (String retryIndex : retriesNode.childrenNames()) {
            Preferences retryNode = retriesNode.node(retryIndex);
            Retry retry = new Retry(retryNode.get("dcmRetrySuffix", null), 
                    retryNode.getInt("dcmRetryDelay", 60), 
                    retryNode.getInt("dcmRetryNum", 10));
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
        store(proxyAE.getAttributeCoercions(), aeNode);
    }

    private void storeRetries(List<Retry> retries, Preferences parentNode) {
        Preferences retriesNode = parentNode.node("dcmRetry");
        int retryIndex = 1;
        for(Retry retry : retries)
            storeTo(retry, retriesNode.node("" + retryIndex++));
    }

    private void storeTo(Retry retry, Preferences prefs) {
        storeNotNull(prefs, "dcmRetrySuffix", retry.getSuffix());
        storeNotNull(prefs, "dcmRetryDelay", retry.getDelay());
        storeNotNull(prefs, "dcmRetryNum", retry.getNumRetry());
    }

    @Override
    protected void storeDiffs(Preferences prefs, ApplicationEntity a, ApplicationEntity b) {
        super.storeDiffs(prefs, a, b);
        if (!(a instanceof ProxyApplicationEntity) || !(b instanceof ProxyApplicationEntity))
            return;
        
        ProxyApplicationEntity pa = (ProxyApplicationEntity) a;
        ProxyApplicationEntity pb = (ProxyApplicationEntity) b;
        storeDiff(prefs, "dcmSpoolDirectory",
                pa.getSpoolDirectory(),
                pb.getSpoolDirectory());
        storeDiff(prefs, "dcmAcceptDataOnFailedNegotiation",
                pa.isAcceptDataOnFailedNegotiation(),
                pb.isAcceptDataOnFailedNegotiation());
        storeDiff(prefs, "dcmDestinationAETitle",
                pa.getDestinationAETitle(),
                pb.getDestinationAETitle());
        storeDiff(prefs, "dcmUseCallingAETitle",
                pa.getUseCallingAETitle(),
                pb.getUseCallingAETitle());
        storeDiff(prefs, "dcmExclusiveUseDefinedTC",
                pa.isExclusiveUseDefinedTC(),
                pb.isExclusiveUseDefinedTC());
        storeDiff(prefs, "dcmEnableAuditLog",
                pa.isEnableAuditLog(),
                pb.isEnableAuditLog());
        storeDiff(prefs, "dcmAuditDirectory",
                pa.getAuditDirectory(),
                pb.getAuditDirectory());
        storeDiff(prefs, "dcmNactionDirectory",
                pa.getNactionDirectory(),
                pb.getNactionDirectory());
        storeDiff(prefs, "dcmNeventDirectory",
                pa.getNeventDirectory(),
                pb.getNeventDirectory());
        storeDiff(prefs, "dcmIgnoreScheduleSOPClasses",
                pa.getIgnoreScheduleSOPClassesAsString(),
                pb.getIgnoreScheduleSOPClassesAsString());
        Schedule scheduleA = pa.getForwardSchedule();
        Schedule scheduleB = pb.getForwardSchedule();
        storeDiff(prefs, "dcmForwardScheduleDays",
                scheduleA.getDays(),
                scheduleB.getDays());
        storeDiff(prefs, "dcmUseCallingAETitle",
                scheduleA.getHours(),
                scheduleB.getHours());
    }

    @Override
    protected void storeDiffs(Preferences prefs, Device a, Device b) {
        super.storeDiffs(prefs, a, b);
        if (!(a instanceof ProxyDevice) || !(b instanceof ProxyDevice))
            return;
        
        ProxyDevice pa = (ProxyDevice) a;
        ProxyDevice pb = (ProxyDevice) b;
        storeDiff(prefs, "dcmSchedulerInterval",
                pa.getSchedulerInterval(),
                pb.getSchedulerInterval());
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
    }

    private void mergeRetries(List<Retry> prevs, List<Retry> retries, Preferences parentNode) 
    throws BackingStoreException {
        Preferences retriesNode = parentNode.node("dcmRetry");
        int retryIndex = 1;
        Iterator<Retry> prevIter = prevs.listIterator();
        for (Retry retry : retries) {
            Preferences retryNode = retriesNode.node(""+ retryIndex++);
            if (prevIter.hasNext())
                storeDiffs(retryNode, prevIter.next(), retry);
            else
                storeTo(retry, retryNode);
        }
        while (prevIter.hasNext()) {
            prevIter.next();
            retriesNode.node("" + retryIndex++).removeNode();
        }
    }

    private void storeDiffs(Preferences prefs, Retry a, Retry b) {
        storeDiff(prefs, "dcmRetryDelay", a.getDelay(), b.getDelay());
        storeDiff(prefs, "dcmRetryNum", a.getNumRetry(), b.getNumRetry());
    }
}