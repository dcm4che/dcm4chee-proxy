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

package org.dcm4chee.proxy;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.UID;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.pdu.UserIdentityAC;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyAssociationHandler extends AssociationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyAssociationHandler.class);

    private ApplicationEntityCache aeCache;

    public ProxyAssociationHandler(ApplicationEntityCache aeCache) {
        this.aeCache = aeCache;
    }

    @Override
    protected AAssociateAC makeAAssociateAC(Association as, AAssociateRQ rq, UserIdentityAC userIdentity)
            throws IOException {
        ProxyAEExtension proxyAEE = as.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        filterForwardRulesOnNegotiationRQ(as, rq, proxyAEE);
        if (!proxyAEE.isAssociationFromDestinationAET(as) && sendNow(as, proxyAEE)) {
            ForwardRule forwardRule = proxyAEE.getCurrentForwardRules(as).get(0);
            LOG.info("{}: directly forwarding to {} based on forward rule \"{}\"", new Object[] { as,
                    forwardRule.getDestinationAETitles(), forwardRule.getCommonName() });
            return forwardAAssociateRQ(as, rq, proxyAEE);
        }
        as.setProperty(ProxyAEExtension.FILE_SUFFIX, ".dcm");
        rq.addRoleSelection(new RoleSelection(UID.StorageCommitmentPushModelSOPClass, true, true));
        return super.makeAAssociateAC(as, rq, userIdentity);
    }

    private void filterForwardRulesOnNegotiationRQ(Association as, AAssociateRQ rq, ProxyAEExtension proxyAEE) {
        List<ForwardRule> filterList = new ArrayList<ForwardRule>();
        for (ForwardRule rule : proxyAEE.getForwardRules()) {
            List<String> callingAET = rule.getCallingAETs();
            if ((callingAET.isEmpty() || callingAET.contains(rq.getCallingAET()))
                    && rule.getReceiveSchedule().isNow(new GregorianCalendar())) {
                LOG.debug(
                        "Adding forward rule \"{}\" based on i) Calling AET = {} and ii) receive schedule days = {}, hours = {}",
                        new Object[] { rule.getCommonName(), rule.getCallingAETs(), rule.getReceiveSchedule().getDays(),
                                rule.getReceiveSchedule().getHours() });
                filterList.add(rule);
            }
        }
        List<ForwardRule> returnList = new ArrayList<ForwardRule>(filterList);
        for (Iterator<ForwardRule> iterator = filterList.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            for (ForwardRule fwr : filterList) {
                if (rule.getCommonName().equals(fwr.getCommonName()))
                    continue;
                if (rule.getCallingAETs() == null && fwr.getCallingAETs() != null
                        && fwr.getCallingAETs().equals(rq.getCallingAET())) {
                    LOG.debug(
                            "Removing forward rule \"{}\" with Calling AET = NULL due to rule \"{}\" with matching Calling AET = {}",
                            new Object[] { rule.getCommonName(), fwr.getCommonName(), fwr.getCallingAETs() });
                    returnList.remove(rule);
                }
            }
        }
        as.setProperty(ProxyAEExtension.FORWARD_RULES, returnList);
    }

    @Override
    protected void onClose(Association as) {
        super.onClose(as);
        Object forwardAssociationProperty = as.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (forwardAssociationProperty == null)
            return;

        Association[] asInvoked;
        if (forwardAssociationProperty instanceof Association) {
            ArrayList<Association> list = new ArrayList<Association>(1);
            list.add((Association) forwardAssociationProperty);
            asInvoked = list.toArray(new Association[1]);
        } else {
            @SuppressWarnings("unchecked")
            HashMap<String, Association> fwdAssocs = (HashMap<String, Association>) forwardAssociationProperty;
            asInvoked = fwdAssocs.values().toArray(new Association[fwdAssocs.size()]);
        }
        for (Association assoc : asInvoked) {
            if (assoc != null && assoc.isRequestor())
                try {
                    assoc.waitForOutstandingRSP();
                    assoc.release();
                } catch (Exception e) {
                    LOG.debug("Failed to release {} ({})", new Object[] { assoc, e.getMessage() });
                }
        }
    }

    private boolean sendNow(Association as, ProxyAEExtension proxyAEE) {
        List<ForwardRule> matchingForwardRules = proxyAEE.getCurrentForwardRules(as);
        return (matchingForwardRules.size() == 1
                && !forwardBasedOnTemplates(matchingForwardRules)
                && matchingForwardRules.get(0).getDimse().isEmpty()
                && matchingForwardRules.get(0).getSopClasses().isEmpty()
                && (matchingForwardRules.get(0).getCallingAETs().isEmpty() 
                    || matchingForwardRules.get(0).getCallingAETs().contains(as.getCallingAET()))
                && matchingForwardRules.get(0).getDestinationAETitles().size() == 1 && isAvailableDestinationAET(
                    matchingForwardRules.get(0).getDestinationAETitles().get(0), proxyAEE))
                && matchingForwardRules.get(0).getMpps2DoseSrTemplateURI() == null
                && !matchingForwardRules.get(0).isRunPIXQuery();
    }

    private boolean forwardBasedOnTemplates(List<ForwardRule> forwardRules) {
        for (ForwardRule rule : forwardRules)
            if (rule.getReceiveSchedule().isNow(new GregorianCalendar()))
                if (rule.containsTemplateURI())
                    return true;
        return false;
    }

    private boolean isAvailableDestinationAET(String destinationAET, ProxyAEExtension proxyAEE) {
        HashMap<String, ForwardOption> forwardOptions = proxyAEE.getForwardOptions();
        if (!forwardOptions.keySet().contains(destinationAET))
            return true;

        Schedule forwardAETSchedule = forwardOptions.get(destinationAET).getSchedule();
        return forwardAETSchedule.isNow(new GregorianCalendar());
    }

    private AAssociateAC forwardAAssociateRQ(Association asAccepted, AAssociateRQ rq, ProxyAEExtension proxyAEE)
            throws IOException {
        ForwardRule forwardRule = proxyAEE.getCurrentForwardRules(asAccepted).get(0);
        List<ForwardRule> fwrList = new ArrayList<ForwardRule>();
        fwrList.add(forwardRule);
        asAccepted.setProperty(ProxyAEExtension.FORWARD_RULES, fwrList);
        String calledAET = forwardRule.getDestinationAETitles().get(0);
        AAssociateAC ac = new AAssociateAC();
        ac.setCalledAET(rq.getCalledAET());
        ac.setCallingAET(rq.getCallingAET());
        String callingAET = (forwardRule.getUseCallingAET() != null) 
                ? forwardRule.getUseCallingAET() 
                : (proxyAEE.getApplicationEntity().getAETitle().equals("*")) 
                    ? rq.getCallingAET() 
                    : proxyAEE.getApplicationEntity().getAETitle();
        try {
            AAssociateRQ forwardRq = copyOf(rq);
            Association asCalled = proxyAEE.openForwardAssociation(asAccepted, forwardRule, callingAET, calledAET, forwardRq, aeCache);
            asAccepted.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asCalled);
            asCalled.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asAccepted);
            AAssociateAC acCalled = asCalled.getAAssociateAC();
            if (forwardRule.isExclusiveUseDefinedTC()) {
                AAssociateAC acProxy = super.makeAAssociateAC(asAccepted, forwardRq, null);
                LOG.debug("{}: generating subset of transfer capabilities", asAccepted);
                for (PresentationContext pcCalled : acCalled.getPresentationContexts()) {
                    final PresentationContext pcLocal = acProxy.getPresentationContext(pcCalled.getPCID());
                    LOG.debug("{}: use {} : {}", new Object[]{asAccepted, pcCalled.getTransferSyntaxes(), pcLocal.isAccepted() });
                    ac.addPresentationContext(pcLocal.isAccepted() ? pcCalled : pcLocal);
                }
            } else
                addPresentationContext(asAccepted, proxyAEE, calledAET, ac, callingAET, asCalled, acCalled);
            for (RoleSelection rs : acCalled.getRoleSelections())
                ac.addRoleSelection(rs);
            for (ExtendedNegotiation extNeg : acCalled.getExtendedNegotiations())
                ac.addExtendedNegotiation(extNeg);
            for (CommonExtendedNegotiation extNeg : acCalled.getCommonExtendedNegotiations())
                ac.addCommonExtendedNegotiation(extNeg);
            ac.setMaxPDULength(asCalled.getConnection().getReceivePDULength());
            ac.setMaxOpsInvoked(minZeroAsMax(rq.getMaxOpsInvoked(), asCalled.getConnection().getMaxOpsPerformed()));
            ac.setMaxOpsPerformed(minZeroAsMax(rq.getMaxOpsPerformed(), asCalled.getConnection().getMaxOpsInvoked()));
            return ac;
        } catch (ConfigurationException e) {
            LOG.error("Unable to load configuration for destination AET: ", e.getMessage());
            if(LOG.isDebugEnabled())
                e.printStackTrace();
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (AAssociateRJ rj) {
            return handleNegotiateConnectException(asAccepted, rq, ac, calledAET, rj,
                    RetryObject.AAssociateRJ.getSuffix() + "0", rj.getReason(), proxyAEE);
        } catch (AAbort aa) {
            return handleNegotiateConnectException(asAccepted, rq, ac, calledAET, aa, RetryObject.AAbort.getSuffix()
                    + "0", aa.getReason(), proxyAEE);
        } catch (IOException e) {
            return handleNegotiateConnectException(asAccepted, rq, ac, calledAET, e,
                    RetryObject.ConnectionException.getSuffix() + "0", 0, proxyAEE);
        } catch (InterruptedException e) {
            LOG.error("Unexpected exception: ", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException e) {
            return handleNegotiateConnectException(asAccepted, rq, ac, calledAET, e,
                    RetryObject.IncompatibleConnectionException.getSuffix() + "0", 0, proxyAEE);
        } catch (GeneralSecurityException e) {
            return handleNegotiateConnectException(asAccepted, rq, ac, calledAET, e,
                    RetryObject.GeneralSecurityException.getSuffix() + "0", 0, proxyAEE);
        }
    }

    private void addPresentationContext(Association asAccepted, ProxyAEExtension proxyAEE, String calledAET,
            AAssociateAC ac, String callingAET, Association asCalled, AAssociateAC acCalled) {
        if (isConnectionWithChangedTC(proxyAEE, calledAET, callingAET)) {
            for (PresentationContext pc : acCalled.getPresentationContexts()) {
                String abstractSyntaxCalled = asCalled.getAAssociateRQ().getPresentationContext(pc.getPCID()).getAbstractSyntax();
                if (asAccepted.getAAssociateRQ().containsPresentationContextFor(abstractSyntaxCalled))
                    ac.addPresentationContext(pc);
            }
        } else {
            for (PresentationContext pc : acCalled.getPresentationContexts())
                ac.addPresentationContext(pc);
        }
    }

    private boolean isConnectionWithChangedTC(ProxyAEExtension proxyAEE, String calledAET, String callingAET) {
        HashMap<String, ForwardOption> forwardOptions = proxyAEE.getForwardOptions();
        return forwardOptions.containsKey(calledAET) && forwardOptions.get(calledAET).isConvertEmf2Sf()
                || forwardOptions.containsKey(callingAET) && forwardOptions.get(callingAET).isConvertEmf2Sf();
    }

    private AAssociateRQ copyOf(AAssociateRQ rq) {
        AAssociateRQ copy = new AAssociateRQ();
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

    static int minZeroAsMax(int i1, int i2) {
        return i1 == 0 ? i2 : i2 == 0 ? i1 : Math.min(i1, i2);
    }

    private AAssociateAC handleNegotiateConnectException(Association as, AAssociateRQ rq, AAssociateAC ac,
            String destinationAETitle, Exception e, String suffix, int reason, ProxyAEExtension proxyAEE)
            throws IOException, AAbort {
        as.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        LOG.error(as + ": unable to connect to {}: {}", new Object[] { destinationAETitle, e.getMessage() });
        if (proxyAEE.isAcceptDataOnFailedAssociation()) {
            as.setProperty(ProxyAEExtension.FILE_SUFFIX, suffix);
            return super.makeAAssociateAC(as, rq, null);
        }
        throw new AAbort(AAbort.UL_SERIVE_PROVIDER, reason);
    }
}
