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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.proxy.mc.net.service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicNEventReportSCU;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class NEventReportSCUImpl extends BasicNEventReportSCU {

    public static final Logger LOG = LoggerFactory.getLogger(NEventReportSCUImpl.class);

    public NEventReportSCUImpl() {
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    @Override
    public void onNEventReportRQ(Association asAccepted, PresentationContext pc, Attributes rq,
            Attributes eventInfo) throws IOException {
        Association asInvoked =
                (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            if (asAccepted.getCallingAET().equals(
                    ((ProxyApplicationEntity) asAccepted.getApplicationEntity())
                            .getDestinationAETitle()))
                forwardFromDestinationAET(asAccepted, pc, rq, eventInfo);
            else
                super.onNEventReportRQ(asAccepted, pc, rq, eventInfo);
        } else {
            try {
                forward(asAccepted, asInvoked, pc, rq, eventInfo);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.warn("Failure in forwarding N-EVENT-REPORT-RQ from "
                        + asAccepted.getCallingAET() + " to " + asAccepted.getCalledAET());
            }
        }
    }

    private void forwardFromDestinationAET(Association asAccepted, PresentationContext pc,
            Attributes data, final Attributes eventInfo) throws IOException {
        File file = getTransactionUIDFile(asAccepted, eventInfo.getString(Tag.TransactionUID));
        if (file == null) {
            abortForward(pc, asAccepted, Commands.mkRSP(data, Status.ProcessingFailure),
                    "Failed load transaction uid mapping for N-EVENT-REPORT-RQ from "
                            + asAccepted.getCallingAET());
            return;
        }
        String separator = System.getProperty("file.separator");
        String path = file.getPath();
        path = path.substring(0, path.lastIndexOf(separator));
        String calledAEString = path.substring(path.lastIndexOf(separator) + 1);
        ProxyApplicationEntity ae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
        ProxyDevice device = (ProxyDevice) ae.getDevice();
        Association asInvoked;
        try {
            ApplicationEntity calledAE = device.findApplicationEntity(calledAEString);
            AAssociateRQ rq = asAccepted.getAAssociateRQ();
            rq.setCalledAET(calledAEString);
            if (ae.getUseCallingAETitle() != null)
                rq.setCalledAET(ae.getUseCallingAETitle());
            else if (!ae.getAETitle().equals("*"))
                rq.setCallingAET(ae.getAETitle());
            asInvoked = ae.connect(calledAE, rq);
            forward(asAccepted, asInvoked, pc, data, eventInfo);
        } catch (Exception e) {
            abortForward(pc, asAccepted, Commands.mkRSP(data, Status.ProcessingFailure), e
                    .getLocalizedMessage());
        }
    }

    private File getTransactionUIDFile(Association asAccepted, final String transactionUID) 
    throws IOException {
        File spoolDir =
                ((ProxyApplicationEntity) asAccepted.getApplicationEntity())
                        .getSpoolDirectoryPath();
        File[] truidFiles = null;
        for (File callingAet : spoolDir.listFiles()) {
            truidFiles = callingAet.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(transactionUID + ".tid");
                }
            });
            if (truidFiles.length > 0)
                break;
        }
        if (truidFiles == null || truidFiles.length == 0) {
            return null;
        }
        File file = truidFiles[0];
        return file;
    }

    private void abortForward(PresentationContext pc, Association asAccepted, Attributes response,
            String logMessage) throws IOException {
        LOG.warn(logMessage);
        asAccepted.writeDimseRSP(pc, response, null);
        asAccepted.release();
    }

    private void forward(final Association asAccepted, Association asInvoked,
            final PresentationContext pc, Attributes rq, Attributes data) throws IOException,
            InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        int eventTypeId = rq.getInt(Tag.EventTypeID, 0);
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {
            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                    asInvoked.release();
                } catch (IOException e) {
                    LOG.warn("Failed to forward N-EVENT RSP to " + asAccepted, e);
                }
            }
        };
        asInvoked.neventReport(cuid, iuid, eventTypeId, data, tsuid, rspHandler);
        deleteTransactionUidFile(asAccepted, data.getString(Tag.TransactionUID));
    }

    private void deleteTransactionUidFile(Association asAccepted, String transactionUID) 
    throws IOException {
        File file = getTransactionUIDFile(asAccepted, transactionUID);
        if (file != null)
            file.delete();
    }

}
