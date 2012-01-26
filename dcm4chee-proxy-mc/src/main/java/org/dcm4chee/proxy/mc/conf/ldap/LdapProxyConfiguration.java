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
import java.util.Hashtable;
import java.util.List;

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
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.dcm4chee.proxy.mc.net.Retry;
import org.dcm4chee.proxy.mc.net.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class LdapProxyConfiguration extends ExtendedLdapDicomConfiguration {

    public LdapProxyConfiguration(Hashtable<String, Object> env,
            String baseDN) throws NamingException {
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
        storeNotNull(attrs, "dcmDestinationAETitle", proxyAE.getDestinationAETitle());
        storeNotNull(attrs, "dcmUseCallingAETitle", proxyAE.getUseCallingAETitle());
        storeNotNull(attrs, "dcmExclusiveUseDefinedTC", proxyAE.isExclusiveUseDefinedTC());
        storeNotNull(attrs, "dcmEnableAuditLog", proxyAE.isEnableAuditLog());
        storeNotNull(attrs, "dcmAuditDirectory", proxyAE.getAuditDirectory());
        storeNotNull(attrs, "dcmNactionDirectory", proxyAE.getNactionDirectory());
        storeNotNull(attrs, "dcmNeventDirectory", proxyAE.getNeventDirectory());
        Schedule schedule = proxyAE.getForwardSchedule();
        storeNotNull(attrs, "dcmForwardScheduleDays", schedule.getDays());
        storeNotNull(attrs, "dcmForwardScheduleHours", schedule.getHours());
        return attrs;
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, 
        CertificateException {
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
       proxyAE.setAcceptDataOnFailedNegotiation(booleanValue(attrs.get("dcmAcceptDataOnFailedNegotiation"), Boolean.FALSE));
       proxyAE.setDestinationAETitle(stringValue(attrs.get("dcmDestinationAETitle")));
       proxyAE.setUseCallingAETitle(stringValue(attrs.get("dcmUseCallingAETitle")));
       proxyAE.setExclusiveUseDefinedTC(booleanValue(attrs.get("dcmExclusiveUseDefinedTC"), Boolean.FALSE));
       proxyAE.setEnableAuditLog(booleanValue(attrs.get("dcmEnableAuditLog"), Boolean.FALSE));
       proxyAE.setAuditDirectory(stringValue(attrs.get("dcmAuditDirectory")));
       proxyAE.setNactionDirectory(stringValue(attrs.get("dcmNactionDirectory")));
       proxyAE.setNeventDirectory(stringValue(attrs.get("dcmNeventDirectory")));
       Schedule schedule = new Schedule();
       schedule.setDays(stringValue(attrs.get("dcmForwardScheduleDays")));
       schedule.setHours(stringValue(attrs.get("dcmForwardScheduleHours")));
       proxyAE.setForwardSchedule(schedule);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        super.loadChilds(ae, aeDN);
        if (!(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity proxyAE = (ProxyApplicationEntity) ae;
        loadRetries(proxyAE, aeDN);
        load(proxyAE.getAttributeCoercions(), aeDN);
    }

    private void loadRetries(ProxyApplicationEntity proxyAE, String aeDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = 
            search(aeDN, "(objectclass=dcmRetry)");
        try {
            List<Retry> retries = new ArrayList<Retry>();
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                Retry retry = new Retry(
                        stringValue(attrs.get("dcmRetrySuffix")), 
                        intValue(attrs.get("dcmRetryDelay"), 60),
                        intValue(attrs.get("dcmRetryNum"), 10));
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
        store(proxyAE.getAttributeCoercions(), aeDN);
    }

    private void storeRetries(List<Retry> retries, String parentDN) throws NamingException {
        for(Retry retry : retries)
            createSubcontext(dnOf(retry, parentDN), storeTo(retry, new BasicAttributes(true)));
    }

    private Attributes storeTo(Retry retry, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRetry");
        storeNotNull(attrs, "dcmRetrySuffix", retry.getSuffix());
        storeNotNull(attrs, "dcmRetryDelay", retry.getDelay());
        storeNotNull(attrs, "dcmRetryNum", retry.getNumRetry());
        return attrs;
    }

    private String dnOf(Retry retry, String parentDN) {
        StringBuilder sb = new StringBuilder();
        sb.append("dcmRetrySuffix=").append(retry.getSuffix());
        sb.append(',').append(parentDN);
        return sb.toString();
    }

    @Override
    protected List<ModificationItem> storeDiffs(ApplicationEntity a, ApplicationEntity b,
            String deviceDN, List<ModificationItem> mods) {
        super.storeDiffs(a, b, deviceDN, mods);
        if (!(a instanceof ProxyApplicationEntity) || !(b instanceof ProxyApplicationEntity))
            return mods;
        ProxyApplicationEntity pa = (ProxyApplicationEntity) a;
        ProxyApplicationEntity pb = (ProxyApplicationEntity) b;
        storeDiff(mods, "dcmSpoolDirectory",
                pa.getSpoolDirectory(),
                pb.getSpoolDirectory());
        storeDiff(mods, "dcmAcceptDataOnFailedNegotiation",
                pa.isAcceptDataOnFailedNegotiation(),
                pb.isAcceptDataOnFailedNegotiation());
        storeDiff(mods, "dcmDestinationAETitle",
                pa.getDestinationAETitle(),
                pb.getDestinationAETitle());
        storeDiff(mods, "dcmUseCallingAETitle",
                pa.getUseCallingAETitle(),
                pb.getUseCallingAETitle());
        storeDiff(mods, "dcmExclusiveUseDefinedTC",
                pa.isExclusiveUseDefinedTC(),
                pb.isExclusiveUseDefinedTC());
        storeDiff(mods, "dcmEnableAuditLog",
                pa.isEnableAuditLog(),
                pb.isEnableAuditLog());
        storeDiff(mods, "dcmAuditDirectory",
                pa.getAuditDirectory(),
                pb.getAuditDirectory());
        storeDiff(mods, "dcmNactionDirectory",
                pa.getNactionDirectory(),
                pb.getNactionDirectory());
        storeDiff(mods, "dcmNeventDirectory",
                pa.getNeventDirectory(),
                pb.getNeventDirectory());
        Schedule scheduleA = pa.getForwardSchedule();
        Schedule scheduleB = pb.getForwardSchedule();
        storeDiff(mods, "dcmForwardScheduleDays",
                scheduleA.getDays(),
                scheduleB.getDays());
        storeDiff(mods, "dcmUseCallingAETitle",
                scheduleA.getHours(),
                scheduleB.getHours());
        return mods;
    }

    @Override
    protected List<ModificationItem> storeDiffs(Device a, Device b, List<ModificationItem> mods) {
        super.storeDiffs(a, b, mods);
        if (!(a instanceof ProxyDevice) || !(b instanceof ProxyDevice))
            return mods;
        ProxyDevice pa = (ProxyDevice) a;
        ProxyDevice pb = (ProxyDevice) b;
        storeDiff(mods, "dcmSchedulerInterval",
                pa.getSchedulerInterval(),
                pb.getSchedulerInterval());
        return mods;
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN)
            throws NamingException {
        super.mergeChilds(prev, ae, aeDN);
        if (!(prev instanceof ProxyApplicationEntity) || !(ae instanceof ProxyApplicationEntity))
            return;
        ProxyApplicationEntity pprev = (ProxyApplicationEntity) prev;
        ProxyApplicationEntity pae = (ProxyApplicationEntity) ae;
        merge(pprev.getAttributeCoercions(), pae.getAttributeCoercions(), aeDN);
        mergeRetries(pprev.getRetries(), pae.getRetries(), aeDN);
    }

    private void mergeRetries(List<Retry> prevs, List<Retry> retries,
            String parentDN) throws NamingException {
        for (Retry prev : prevs)
            if (!retries.contains(prev))
                destroySubcontext(dnOf(prev, parentDN));
        for (Retry retry : retries) {
            String dn = dnOf(retry, parentDN);
            Integer indexOfPrevRetry = prevs.indexOf(retry);
            if (indexOfPrevRetry == -1)
                createSubcontext(dn, storeTo(retry, new BasicAttributes(true)));
            else
                modifyAttributes(dn, storeDiffs(prevs.get(indexOfPrevRetry), retry,
                        new ArrayList<ModificationItem>()));
        }
}

    private List<ModificationItem> storeDiffs(Retry prev, Retry ac,
            ArrayList<ModificationItem> mods) {
        storeDiff(mods, "dcmRetryDelay", prev.getDelay(), ac.getDelay());
        storeDiff(mods, "dcmRetryNum", prev.getNumRetry(), ac.getNumRetry());
        return mods;
    }
}
