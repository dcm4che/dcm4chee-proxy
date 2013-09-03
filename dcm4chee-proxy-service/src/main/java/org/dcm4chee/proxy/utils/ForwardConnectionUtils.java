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

import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.UID;
import org.dcm4che.net.Association;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.Proxy;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
public class ForwardConnectionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardConnectionUtils.class);

    public static Association openForwardAssociation(ProxyAEExtension proxyAEE, ForwardRule rule, String callingAET,
            String calledAET, AAssociateRQ rq) throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException, ConfigurationException {
        return proxyAEE.getApplicationEntity().connect(Proxy.getInstance().findApplicationEntity(calledAET), rq);
    }

    public static Association openForwardAssociation(ProxyAEExtension proxyAEE, Association asAccepted,
            ForwardRule rule, String callingAET, String calledAET, AAssociateRQ rq, ApplicationEntityCache aeCache)
            throws IOException, InterruptedException, IncompatibleConnectionException, GeneralSecurityException,
            ConfigurationException {
        rq.setCallingAET(callingAET);
        rq.setCalledAET(calledAET);
        HashMap<String, ForwardOption> forwardOptions = proxyAEE.getForwardOptions();
        if (forwardOptions.containsKey(asAccepted.getRemoteAET())
                && forwardOptions.get(asAccepted.getRemoteAET()).isConvertEmf2Sf())
            addEnhancedTS(rq);
        else if (forwardOptions.containsKey(calledAET) && forwardOptions.get(calledAET).isConvertEmf2Sf())
            addReducedTS(rq);
        Association asInvoked = proxyAEE.getApplicationEntity().connect(aeCache.findApplicationEntity(calledAET), rq);
        asInvoked.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asAccepted);
        asInvoked.setProperty(ForwardRule.class.getName(), rule);
        return asInvoked;
    }

    public static HashMap<String, Association> openForwardAssociations(ProxyAEExtension proxyAEE,
            Association asAccepted, List<ForwardRule> forwardRules, Attributes data, ApplicationEntityCache aeCache)
            throws DicomServiceException {
        HashMap<String, Association> fwdAssocs = new HashMap<String, Association>(forwardRules.size());
        for (ForwardRule rule : forwardRules) {
            String callingAET = (rule.getUseCallingAET() == null) ? asAccepted.getCallingAET() : rule
                    .getUseCallingAET();
            List<String> destinationAETs = new ArrayList<String>();
            try {
                destinationAETs = ForwardRuleUtils.getDestinationAETsFromForwardRule(asAccepted, rule, data);
            } catch (ConfigurationException e) {
                LOG.error("Failed to get destination AET from forward rule {}: {}", rule.getCommonName(), e);
                asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConfigurationException.getSuffix()
                        + "0");
                return fwdAssocs;
            }
            for (String calledAET : destinationAETs) {
                try {
                    Association asInvoked = openForwardAssociation(proxyAEE, asAccepted, rule, callingAET, calledAET,
                            copyOf(asAccepted, rule), aeCache);
                    if (asInvoked != null)
                        fwdAssocs.put(calledAET, asInvoked);
                } catch (IncompatibleConnectionException e) {
                    LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX,
                            RetryObject.IncompatibleConnectionException.getSuffix() + "0");
                } catch (GeneralSecurityException e) {
                    LOG.error("Failed to create SSL context: ", e.getMessage());
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX,
                            RetryObject.GeneralSecurityException.getSuffix() + "0");
                } catch (ConfigurationException e) {
                    LOG.error("Unable to load configuration for destination AET {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConfigurationException.getSuffix()
                            + "0");
                } catch (ConnectException e) {
                    LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix()
                            + "0");
                } catch (Exception e) {
                    LOG.error("Unexpected exception: ", e);
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, ".err");
                }
            }
        }
        return fwdAssocs;
    }

    public static AAssociateRQ copyOf(Association as, ForwardRule rule) {
        AAssociateRQ rq = as.getAAssociateRQ();
        AAssociateRQ copy = new AAssociateRQ();
        if (rule != null && rule.isExclusiveUseDefinedTC())
            for (PresentationContext pc : filterMatchingPC(as))
                copy.addPresentationContext(pc);
        else
            for (PresentationContext pc : rq.getPresentationContexts())
                copy.addPresentationContext(pc);
        copy.setReservedBytes(rq.getReservedBytes());
        copy.setProtocolVersion(rq.getProtocolVersion());
        copy.setMaxPDULength(rq.getMaxPDULength());
        copy.setMaxOpsInvoked(rq.getMaxOpsInvoked());
        copy.setMaxOpsPerformed(rq.getMaxOpsPerformed());
        copy.setCalledAET(rq.getCalledAET());
        copy.setCallingAET(rq.getCallingAET());
        copy.setApplicationContext(rq.getApplicationContext());
        copy.setImplClassUID(rq.getImplClassUID());
        copy.setImplVersionName(rq.getImplVersionName());
        copy.setUserIdentityRQ(rq.getUserIdentityRQ());
        for (RoleSelection rs : rq.getRoleSelections())
            copy.addRoleSelection(rs);
        for (ExtendedNegotiation en : rq.getExtendedNegotiations())
            copy.addExtendedNegotiation(en);
        for (CommonExtendedNegotiation cen : rq.getCommonExtendedNegotiations())
            copy.addCommonExtendedNegotiation(cen);
        return copy;
    }

    private static void addEnhancedTS(AAssociateRQ rq) {
        List<PresentationContext> newPcList = new ArrayList<PresentationContext>(3);
        int pcSize = rq.getNumberOfPresentationContexts();
        HashMap<String, String[]> as_ts = new HashMap<String, String[]>(pcSize);
        for (PresentationContext pc : rq.getPresentationContexts())
            as_ts.put(pc.getAbstractSyntax(), pc.getTransferSyntaxes());
        addTsSopClass(UID.CTImageStorage, UID.EnhancedCTImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.MRImageStorage, UID.EnhancedMRImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.PositronEmissionTomographyImageStorage, UID.EnhancedPETImageStorage, newPcList, pcSize, as_ts);
        for (PresentationContext pc : newPcList)
            rq.addPresentationContext(pc);
    }

    public static void addReducedTS(AAssociateRQ rq) {
        List<PresentationContext> newPcList = new ArrayList<PresentationContext>(3);
        int pcSize = rq.getNumberOfPresentationContexts();
        HashMap<String, String[]> as_ts = new HashMap<String, String[]>(pcSize);
        for (PresentationContext pc : rq.getPresentationContexts())
            as_ts.put(pc.getAbstractSyntax(), pc.getTransferSyntaxes());
        addTsSopClass(UID.EnhancedCTImageStorage, UID.CTImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.EnhancedMRImageStorage, UID.MRImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.EnhancedPETImageStorage, UID.PositronEmissionTomographyImageStorage, newPcList, pcSize, as_ts);
        for (PresentationContext pc : newPcList)
            rq.addPresentationContext(pc);
    }

    private static void addTsSopClass(String tsA, String tsB, List<PresentationContext> newPcList,
            int pcSize, HashMap<String, String[]> as_ts) {
        String[] imageStorageTS = as_ts.get(tsA);
        if (imageStorageTS != null)
            if (!as_ts.containsKey(tsB))
                newPcList.add(new PresentationContext((pcSize + newPcList.size()) * 2 + 1, tsB, imageStorageTS));
    }

    private static List<PresentationContext> filterMatchingPC(Association as) {
        AAssociateRQ rq = as.getAAssociateRQ();
        AAssociateAC ac = as.getAAssociateAC();
        List<PresentationContext> returnList = new ArrayList<PresentationContext>(rq.getNumberOfPresentationContexts());
        for (int i = 0; i < rq.getNumberOfPresentationContexts(); i++) {
            int pcid = i * 2 + 1;
            PresentationContext pcAC = ac.getPresentationContext(pcid);
            PresentationContext pcRQ = rq.getPresentationContext(pcid);
            List<String> tss = new ArrayList<String>(pcAC.getTransferSyntaxes().length);
            tss.add(UID.ImplicitVRLittleEndian);
            for (String otherTss : pcAC.getTransferSyntaxes())
                if (!tss.contains(otherTss))
                    tss.add(otherTss);
            returnList.add(new PresentationContext(pcid, pcRQ.getAbstractSyntax(), (String[]) tss
                    .toArray(new String[tss.size()])));
        }
        return returnList;
    }

    public static String getMatchingTsuid(Association asInvoked, String tsuid, String cuid) {
        Set<String> tsuids = asInvoked.getTransferSyntaxesFor(cuid);
        return tsuids.contains(tsuid) 
                ? tsuid 
                : tsuids.isEmpty() 
                    ? null 
                    : tsuids.iterator().next();
    }

    public static boolean requiresMultiFrameConversion(ProxyAEExtension proxyAEE, String destinationAET, String sopClass) {
        HashMap<String, ForwardOption> fwdOptions = proxyAEE.getForwardOptions();
        ForwardOption fwdOption = fwdOptions.get(destinationAET);
        if (fwdOption != null)
            return (fwdOption.isConvertEmf2Sf() 
                        && (sopClass.equals(UID.EnhancedCTImageStorage)
                            || sopClass.equals(UID.EnhancedMRImageStorage) 
                            || sopClass.equals(UID.EnhancedPETImageStorage)));
        return false;
    }

}
