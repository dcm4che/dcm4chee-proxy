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
 * decision by deleting the provisions above and adjustPatientID them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.dimse;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.Tag;
import org.dcm4che.data.VR;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.CancelRQHandler;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.common.CMoveInfoObject;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.pix.IDWithIssuer;
import org.dcm4chee.proxy.pix.PIXConsumer;
import org.dcm4chee.proxy.utils.AttributeCoercionUtils;
import org.dcm4chee.proxy.utils.ForwardConnectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class ForwardDimseRQ {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardDimseRQ.class);

    private int status = -1;
    private CountDownLatch waitForOutstandingRSP;
    private PresentationContext pc;
    private Attributes rq;
    private Attributes data;
    private Association asAccepted;
    private Dimse dimse;
    private Association[] fwdAssocs;
    private int NumberOfCompletedSuboperations = 0;
    private int NumberOfFailedSuboperations = 0;
    private int NumberOfWarningSuboperations = 0;
    private PIXConsumer pixConsumer;
    private ApplicationEntityCache aeCache;

    public ForwardDimseRQ(Association asAccepted, PresentationContext pc, Attributes rq, Attributes data, Dimse dimse,
            PIXConsumer pixConsumer, ApplicationEntityCache aeCache, Association... fwdAssocs) {
        this.asAccepted = asAccepted;
        this.pc = pc;
        this.rq = rq;
        this.data = data;
        this.dimse = dimse;
        this.fwdAssocs = fwdAssocs;
        this.pixConsumer = pixConsumer;
        this.aeCache = aeCache;
        waitForOutstandingRSP = new CountDownLatch(fwdAssocs.length);
    }

    private void forwardDimseRQ(final Association asInvoked, Attributes coercedData, final boolean adjustPatientID)
            throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        int priority = rq.getInt(Tag.Priority, 0);
        ApplicationEntity ae = asAccepted.getApplicationEntity();
        final ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        final int msgId = rq.getInt(Tag.MessageID, 0);
        int rspMsgId = msgId;
        if (dimse == Dimse.C_MOVE_RQ) {
            CMoveInfoObject infoObject = new CMoveInfoObject(
                    asAccepted.getRemoteAET(),
                    rq.getString(Tag.MoveDestination), 
                    asInvoked.getCalledAET(), 
                    asInvoked.getCallingAET(),
                    rq.getInt(Tag.MessageID, 0),
                    (ForwardRule) asInvoked.getProperty(ForwardRule.class.getName()));
            int newMsgId = proxyAEE.getNewCMoveMessageID(infoObject);
            if (newMsgId == -1) {
                LOG.error("Cannot forward C-MOVE-RQ to " + asInvoked.getRemoteAET()
                        + ": no free message id due to too many active c-move-requests");
                throw new DicomServiceException(Status.UnableToProcess);
            }
            rspMsgId = newMsgId;
        } else if (adjustPatientID) {
            rspMsgId = asInvoked.nextMessageID();
        }
        final DimseRSPHandler rspHandler = new DimseRSPHandler(rspMsgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes rspData) {
                super.onDimseRSP(asInvoked, cmd, rspData);
                if (adjustPatientID || dimse == Dimse.C_MOVE_RQ)
                    cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                int rspStatus = cmd.getInt(Tag.Status, -1);
                if (Status.isPending(rspStatus))
                    writeDimseRSP(pc, cmd, rspData);
                else {
                    if (status != Status.Success)
                        status = rspStatus;
                    NumberOfCompletedSuboperations = NumberOfCompletedSuboperations
                            + cmd.getInt(Tag.NumberOfCompletedSuboperations, 0);
                    NumberOfFailedSuboperations = NumberOfFailedSuboperations
                            + cmd.getInt(Tag.NumberOfFailedSuboperations, 0);
                    NumberOfWarningSuboperations = NumberOfWarningSuboperations
                            + cmd.getInt(Tag.NumberOfWarningSuboperations, 0);
                    waitForOutstandingRSP.countDown();
                    if (waitForOutstandingRSP.getCount() == 0)
                        sendFinalDimseRSP();
                }
            }

            private void writeDimseRSP(PresentationContext pc, Attributes cmd, Attributes rspData) {
                try {
                    if (adjustPatientID) {
                        rspData.setString(Tag.PatientID, VR.LO, data.getString(Tag.PatientID));
                        rspData.setString(Tag.IssuerOfPatientID, VR.LO, data.getString(Tag.IssuerOfPatientID));
                    }
                    if (rspData != null && dimse == Dimse.C_FIND_RQ) {
                            rspData = AttributeCoercionUtils.coerceDataset(
                                    proxyAEE,
                                    asAccepted,
                                    Role.SCU,
                                    Dimse.C_FIND_RSP,
                                    rspData,
                                    rq);
                    }
                    asAccepted.writeDimseRSP(pc, cmd, rspData);
                } catch (IOException e) {
                    LOG.error(asAccepted + ": failed to forward DIMSE-RSP: " + e.getMessage());
                    if(LOG.isDebugEnabled())
                        e.printStackTrace();
                    asInvoked.abort();
                }
            }
        };
        asAccepted.addCancelRQHandler(msgId, new CancelRQHandler() {

            @Override
            public void onCancelRQ(Association association) {
                try {
                    rspHandler.cancel(asInvoked);
                } catch (IOException e) {
                    LOG.error(asAccepted + ": unexpected exception: " + e.getMessage());
                    if(LOG.isDebugEnabled())
                        e.printStackTrace();
                }
            }
        });
        try {
            String cuid = rq.getString(dimse.tagOfSOPClassUID());
            switch (dimse) {
            case C_FIND_RQ:
                asInvoked.cfind(cuid, priority, coercedData, ForwardConnectionUtils.getMatchingTsuid(asInvoked, tsuid, cuid),
                        rspHandler);
                break;
            case C_GET_RQ:
                asInvoked.cget(cuid, priority, coercedData, ForwardConnectionUtils.getMatchingTsuid(asInvoked, tsuid, cuid),
                        rspHandler);
                break;
            case C_MOVE_RQ:
                asInvoked.cmove(cuid, priority, coercedData, ForwardConnectionUtils.getMatchingTsuid(asInvoked, tsuid, cuid),
                        getMoveDestination(proxyAEE), rspHandler);
                break;
            default:
                throw new DicomServiceException(Status.UnrecognizedOperation);
            }
        } catch (Exception e) {
            LOG.error("{}: unable to forward DIMSE request: {}", new Object[] { asInvoked, e.getMessage() });
            if(LOG.isDebugEnabled())
                e.printStackTrace();
            waitForOutstandingRSP.countDown();
            if (waitForOutstandingRSP.getCount() == 0)
                sendFinalDimseRSP();
        }
    }

    private String getMoveDestination(ProxyAEExtension proxyAEE) {
        return proxyAEE.getApplicationEntity().getAETitle().equals("*")
                ? rq.getString(Tag.MoveDestination)
                : proxyAEE.getApplicationEntity().getAETitle();
    }

    private void sendFinalDimseRSP() {
        if (dimse == Dimse.C_FIND_RQ)
            try {
                asAccepted.writeDimseRSP(pc, Commands.mkCFindRSP(rq, status));
                return;
            } catch (IOException e) {
                LOG.error(asAccepted + ": failed to forward C-FIND-RSP: " + e.getMessage());
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
            }
        else if (dimse == Dimse.C_GET_RQ)
            try {
                Attributes rsp = Commands.mkCGetRSP(rq, status);
                addNumberOfSuboperations(rsp);
                asAccepted.writeDimseRSP(pc, rsp);
                return;
            } catch (IOException e) {
                LOG.error(asAccepted + ": failed to forward C-GET-RSP: " + e.getMessage());
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
            }
        else if (dimse == Dimse.C_MOVE_RQ)
            try {
                Attributes rsp = Commands.mkCMoveRSP(rq, status);
                addNumberOfSuboperations(rsp);
                asAccepted.writeDimseRSP(pc, rsp);
                return;
            } catch (Exception e) {
                LOG.error(asAccepted + ": failed to forward C-MOVE-RSP: " + e.getMessage());
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
            }
    }

    private void addNumberOfSuboperations(Attributes rsp) {
        rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, NumberOfCompletedSuboperations);
        rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, NumberOfFailedSuboperations);
        rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, NumberOfWarningSuboperations);
    }

    public void execute() throws IOException, InterruptedException {
        ProxyAEExtension proxyAEE = asAccepted.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        List<ForwardRule> fwdRules = new ArrayList<ForwardRule>();
        String propertyName = ForwardRule.class.getName();
        for (Association as : fwdAssocs) {
            ForwardRule rule = (ForwardRule) as.getProperty(propertyName);
            if (!fwdRules.contains(rule))
                fwdRules.add(rule);
        }
        for (Association fwdAssoc : fwdAssocs) {
            IDWithIssuer[] pids = processPatientIDs(proxyAEE, fwdRules, fwdAssoc);
            if (pids.length == 0)
                coerceAndForward(proxyAEE, fwdAssoc, data, false);
            else
                forwardDimseRQPerPatientID(proxyAEE, fwdAssoc, pids);
        }
    }

    private void forwardDimseRQPerPatientID(ProxyAEExtension proxyAEE, Association fwdAssoc, IDWithIssuer[] pids)
            throws IOException, InterruptedException {
        waitForOutstandingRSP = new CountDownLatch((int) waitForOutstandingRSP.getCount() + (pids.length - 1));
        LOG.debug("{}: execute {} for PatientIDs {}", new Object[]{ fwdAssoc, dimse, pidsToString(pids)});
        for (IDWithIssuer pid : pids) {
            Attributes newPidAttrs = new Attributes(data);
            newPidAttrs.setString(Tag.PatientID, VR.LO, pid.id);
            newPidAttrs.setString(Tag.IssuerOfPatientID, VR.LO, pid.issuer.getLocalNamespaceEntityID());
            coerceAndForward(proxyAEE, fwdAssoc, newPidAttrs, true);
        }
    }

    private String pidsToString(IDWithIssuer[] pids) {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        int i = 1;
        for (IDWithIssuer pid : pids) {
            sb.append(pid.toString());
            if (i < pids.length)
                sb.append(", ");
            ++i;
        }
        sb.append("]");
        return sb.toString();
    }

    private void coerceAndForward(ProxyAEExtension proxyAEE, Association fwdAssoc, Attributes attrs, boolean adjustPatientID)
            throws IOException, InterruptedException {
        Attributes coercedData = new Attributes(AttributeCoercionUtils.coerceDataset(proxyAEE, fwdAssoc, Role.SCP, dimse, attrs, rq));
        forwardDimseRQ(fwdAssoc, coercedData, adjustPatientID);
    }

    private IDWithIssuer[] processPatientIDs(ProxyAEExtension proxyAEE, List<ForwardRule> fwdRules, Association fwdAssoc) {
        IDWithIssuer[] pids = IDWithIssuer.EMPTY;
        String patientID = data.getString(Tag.PatientID);
        if (patientID == null || patientID.isEmpty()) {
            LOG.debug("{}: no PatientID in dataset", fwdAssoc);
            return pids;
        }
        for (ForwardRule fwr : fwdRules)
            if (fwr.isRunPIXQuery() && fwr.getDestinationAETitles().contains(fwdAssoc.getCalledAET())) {
                Object pidsFromAssoc = asAccepted.getProperty(ProxyAEExtension.PIDS);
                if (pidsFromAssoc != null) {
                    pids = (IDWithIssuer[]) pidsFromAssoc;
                } else {
                    try {
                        LOG.debug("{}: run PIX Query based on forward rule \"{}\" using PatientID \"{}\"",
                                new Object[] { fwdAssoc, fwr.getCommonName(), patientID });
                        pids = getOtherPatientIDs(fwdAssoc, proxyAEE);
                        asAccepted.setProperty(ProxyAEExtension.PIDS, pids);
                    } catch (Exception e) {
                        LOG.error("Unable to execute PIX Query: " + e.getMessage());
                    }
                }
                break;
            }
        return pids;
    }

    private IDWithIssuer[] getOtherPatientIDs(Association as, ProxyAEExtension proxyAEE)
            throws ConfigurationException, IncompatibleConnectionException, IOException, GeneralSecurityException {
        IDWithIssuer[] pids = IDWithIssuer.EMPTY;
        Issuer issuerOfPatientID = null;
        String requestedIssuer = data.getString(Tag.IssuerOfPatientID);
        if (requestedIssuer == null) {
            String deviceName = asAccepted.getApplicationEntity().getDevice().getDeviceName();
            LOG.debug("{}: IssuerOfPatientID not in dataset, will now try to retrieve from device configuration \"{}\"", as, deviceName);
            String callingAET = asAccepted.getAAssociateAC().getCallingAET();
            ApplicationEntity issuerAET = aeCache.findApplicationEntity(callingAET);
            issuerOfPatientID = issuerAET.getDevice().getIssuerOfPatientID();
            if (issuerOfPatientID == null) {
                LOG.error("{}: no IssuerOfPatientID configured for {}", as, callingAET);
                return pids;
            }
            if (LOG.isDebugEnabled())
                LOG.debug("{}: retrieved IssuerOfPatientID = \"{}\" from configuration", as, issuerOfPatientID.toString());
            data.setString(Tag.IssuerOfPatientID, VR.LO, issuerOfPatientID.toString());
        } else {
            LOG.debug("{}: using IssuerOfPatientID = \"{}\" from dataset", as, requestedIssuer);
            issuerOfPatientID = new Issuer(requestedIssuer);
        }
        IDWithIssuer pid = IDWithIssuer.pidWithIssuer(data, issuerOfPatientID);
        if (pid != null)
            pids = pixConsumer.pixQuery(proxyAEE, pid);
        else
            LOG.error("{}: unexpected error: IDWithIssuer == null", as);
        return pids;
    }
}
