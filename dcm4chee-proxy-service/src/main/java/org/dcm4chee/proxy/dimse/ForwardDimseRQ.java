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
 * decision by deleting the provisions above and adjustPatientID them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.dimse;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.dcm4che.conf.api.ApplicationEntityCache;
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
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.common.CMoveInfoObject;
import org.dcm4chee.proxy.common.IDWithIssuer;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.pix.PIXConsumer;
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
    private IDWithIssuer requestedPatientIDWithIssuer;
    private boolean adjustPatientID = false;
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

    private void forwardDimseRQ(final Association asInvoked) throws IOException, InterruptedException {
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
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                if (adjustPatientID || dimse == Dimse.C_MOVE_RQ)
                    cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                int rspStatus = cmd.getInt(Tag.Status, -1);
                if (Status.isPending(rspStatus))
                    writeDimseRSP(pc, cmd, data);
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

            private void writeDimseRSP(PresentationContext pc, Attributes cmd, Attributes data) {
                try {
                    if (adjustPatientID) {
                        data.setString(Tag.PatientID, VR.LO, requestedPatientIDWithIssuer.id);
                        data.setString(Tag.IssuerOfPatientID, VR.LO,
                                requestedPatientIDWithIssuer.issuer.getLocalNamespaceEntityID());
                    }
                    if (data != null) {
                        if (dimse == Dimse.C_FIND_RQ)
                            proxyAEE.coerceDataset(
                                    asAccepted,
                                    Role.SCU,
                                    Dimse.C_FIND_RSP,
                                    data,
                                    asAccepted.getApplicationEntity().getDevice()
                                            .getDeviceExtension(ProxyDeviceExtension.class));
                        else if (dimse == Dimse.C_GET_RQ)
                            proxyAEE.coerceDataset(
                                    asAccepted,
                                    Role.SCU,
                                    Dimse.C_GET_RSP,
                                    data,
                                    asAccepted.getApplicationEntity().getDevice()
                                            .getDeviceExtension(ProxyDeviceExtension.class));
                        else if (dimse == Dimse.C_MOVE_RQ)
                            proxyAEE.coerceDataset(
                                    asAccepted,
                                    Role.SCU,
                                    Dimse.C_MOVE_RSP,
                                    data,
                                    asAccepted.getApplicationEntity().getDevice()
                                            .getDeviceExtension(ProxyDeviceExtension.class));
                    }
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.error(asAccepted + ": failed to forward DIMSE-RSP: " + e.getMessage());
                    LOG.debug(e.getMessage(), e);
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
                    LOG.debug(e.getMessage(), e);
                }
            }
        });
        try {
            String cuid = rq.getString(dimse.tagOfSOPClassUID());
            switch (dimse) {
            case C_FIND_RQ:
                asInvoked.cfind(cuid, priority, data, ProxyAEExtension.getMatchingTsuid(asInvoked, tsuid, cuid),
                        rspHandler);
                break;
            case C_GET_RQ:
                asInvoked.cget(cuid, priority, data, ProxyAEExtension.getMatchingTsuid(asInvoked, tsuid, cuid),
                        rspHandler);
                break;
            case C_MOVE_RQ:
                asInvoked.cmove(cuid, priority, data, ProxyAEExtension.getMatchingTsuid(asInvoked, tsuid, cuid),
                        getMoveDestination(proxyAEE), rspHandler);
                break;
            default:
                throw new DicomServiceException(Status.UnrecognizedOperation);
            }
        } catch (Exception e) {
            LOG.error("{}: unable to forward DIMSE request: {}", new Object[] { asInvoked, e.getMessage() });
            LOG.debug(e.getMessage(), e);
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
                LOG.debug(e.getMessage(), e);
            }
        else if (dimse == Dimse.C_GET_RQ)
            try {
                Attributes rsp = Commands.mkCGetRSP(rq, status);
                addNumberOfSuboperations(rsp);
                asAccepted.writeDimseRSP(pc, rsp);
                return;
            } catch (IOException e) {
                LOG.error(asAccepted + ": failed to forward C-GET-RSP: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
            }
        else if (dimse == Dimse.C_MOVE_RQ)
            try {
                Attributes rsp = Commands.mkCMoveRSP(rq, status);
                addNumberOfSuboperations(rsp);
                asAccepted.writeDimseRSP(pc, rsp);
                return;
            } catch (Exception e) {
                LOG.error(asAccepted + ": failed to forward C-MOVE-RSP: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
            }
    }

    private void addNumberOfSuboperations(Attributes rsp) {
        rsp.setInt(Tag.NumberOfCompletedSuboperations, VR.US, NumberOfCompletedSuboperations);
        rsp.setInt(Tag.NumberOfFailedSuboperations, VR.US, NumberOfFailedSuboperations);
        rsp.setInt(Tag.NumberOfWarningSuboperations, VR.US, NumberOfWarningSuboperations);
    }

    public void execute() throws IOException, InterruptedException {
        ProxyAEExtension proxyAEE = asAccepted.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        List<ForwardRule> fwdRules = proxyAEE.getCurrentForwardRules(asAccepted);
        for (Association fwdAssoc : fwdAssocs) {
            IDWithIssuer[] pids = processPatientIDs(proxyAEE, fwdRules, fwdAssoc);
            if (pids.length == 0)
                coerceAndForward(proxyAEE, fwdAssoc);
            else
                forwardDimseRQPerPatientID(proxyAEE, fwdAssoc, pids);
        }
    }

    private void forwardDimseRQPerPatientID(ProxyAEExtension pae, Association fwdAssoc, IDWithIssuer[] pids)
            throws IOException, InterruptedException {
        requestedPatientIDWithIssuer = new IDWithIssuer(data.getString(Tag.PatientID),
                data.getString(Tag.IssuerOfPatientID));
        adjustPatientID = true;
        waitForOutstandingRSP = new CountDownLatch((int) waitForOutstandingRSP.getCount() + (pids.length - 1));
        for (IDWithIssuer pid : pids) {
            data.setString(Tag.PatientID, VR.LO, pid.id);
            data.setString(Tag.IssuerOfPatientID, VR.LO, pid.issuer.getLocalNamespaceEntityID());
            coerceAndForward(pae, fwdAssoc);
        }
    }

    private void coerceAndForward(ProxyAEExtension pae, Association fwdAssoc) throws IOException,
            InterruptedException {
        pae.coerceDataset(fwdAssoc, Role.SCP, dimse, data, asAccepted.getApplicationEntity()
                .getDevice().getDeviceExtension(ProxyDeviceExtension.class));
        forwardDimseRQ(fwdAssoc);
    }

    private IDWithIssuer[] processPatientIDs(ProxyAEExtension pae, List<ForwardRule> fwdRules, Association fwdAssoc) {
        IDWithIssuer[] pids = IDWithIssuer.EMPTY;
        for (ForwardRule fwr : fwdRules)
            if (fwr.isRunPIXQuery() && fwr.getDestinationAETitles().contains(fwdAssoc.getCalledAET())) {
                pids = getOtherPatientIDs(pae, data);
                break;
            }
        return pids;
    }

    private IDWithIssuer[] getOtherPatientIDs(ProxyAEExtension pae, Attributes attrs) {
        IDWithIssuer[] pids = IDWithIssuer.EMPTY;
        try {
            Issuer issuerOfPatientID = null;
            String requestedIssuer = attrs.getString(Tag.IssuerOfPatientID);
            if (requestedIssuer == null) {
                String callingAET = asAccepted.getAAssociateAC().getCallingAET();
                ApplicationEntity issuerAET = aeCache.findApplicationEntity(callingAET);
                issuerOfPatientID = issuerAET.getDevice().getIssuerOfPatientID();
                if (issuerOfPatientID == null) {
                    LOG.error("No IssuerOfPatientID for " + callingAET);
                    return pids;
                }
            } else {
                issuerOfPatientID = new Issuer(requestedIssuer);
            }
            IDWithIssuer pid = IDWithIssuer.pidWithIssuer(attrs, issuerOfPatientID);
            if (pid != null)
                pids = pixConsumer.pixQuery(pae, pid);
        } catch (Exception e) {
            LOG.error("Error getting other patient IDs: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
        return pids;
    }
}
