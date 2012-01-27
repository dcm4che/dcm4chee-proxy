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
import java.io.IOException;
import java.util.Iterator;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicNEventReportSCU;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class NEventReportSCUImpl extends BasicNEventReportSCU {
    
    public static final String FILE_SUFFIX = ".dcm.fwd";

    public static final Logger LOG = LoggerFactory.getLogger(NEventReportSCUImpl.class);

    public NEventReportSCUImpl() {
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    @Override
    public void onNEventReportRQ(Association asAccepted, PresentationContext pc, Attributes rq,
            Attributes eventInfo) throws IOException {
        File transactionUIDFile = new File(((ProxyApplicationEntity) 
                asAccepted.getApplicationEntity()).getNeventDirectoryPath(), 
                eventInfo.getString(Tag.TransactionUID));
        if (!transactionUIDFile.exists()) {
            abortForward(pc, asAccepted, Commands
                    .mkNEventReportRSP(rq, Status.InvalidArgumentValue),
                    "Failed to load Transaction UID mapping for N-EVENT-REPORT-RQ from "
                            + asAccepted.getCallingAET());
            return;
        }
        if (pendingFileForwarding(asAccepted, eventInfo)) {
            LOG.debug("Pending file forwading before sending NEventReportRQ for TransactionUID:" 
                    + eventInfo.getString(Tag.TransactionUID));
            return;
        }
        Association asInvoked =
                (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            if (isAssociationFromDestinationAET(asAccepted))
                forwardFromDestinationAET(asAccepted, pc, rq, eventInfo, transactionUIDFile);
            else
                super.onNEventReportRQ(asAccepted, pc, rq, eventInfo);
        } else {
            try {
                forward(asAccepted, asInvoked, pc, rq, eventInfo, transactionUIDFile);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.warn("Failure in forwarding N-EVENT-REPORT-RQ from "
                        + asAccepted.getCallingAET() + " to " + asAccepted.getCalledAET());
            }
        }
    }

    private boolean pendingFileForwarding(Association as, Attributes eventInfo) {
        File dir = new File(((ProxyApplicationEntity) as.getApplicationEntity())
                .getSpoolDirectoryPath(), as.getCalledAET());
        if (!dir.exists())
            return false;
        String[] files = dir.list();
        Sequence referencedSOPSequence = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        Iterator<Attributes> it = referencedSOPSequence.iterator();
        while(it.hasNext()) {
            Attributes item = it.next();
            String referencedSOPInstanceUID = item.getString(Tag.ReferencedSOPInstanceUID);
            for (String file : files)
                if (file.startsWith(referencedSOPInstanceUID))
                    return true;
        }
        return false;
    }

    private boolean isAssociationFromDestinationAET(Association asAccepted) {
        return asAccepted.getCallingAET().equals(
                ((ProxyApplicationEntity) asAccepted.getApplicationEntity())
                        .getDestinationAETitle());
    }

    private void forwardFromDestinationAET(Association asAccepted, PresentationContext pc,
            Attributes data, final Attributes eventInfo, File file) {
        DicomInputStream dis = null;
        try {
            dis = new DicomInputStream(file);
            Attributes fmi = dis.readFileMetaInformation();
            String calledAEString = fmi.getString(Tag.SourceApplicationEntityTitle);
            ProxyApplicationEntity ae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
            ProxyDevice device = (ProxyDevice) ae.getDevice();
            ApplicationEntity calledAE = device.findApplicationEntity(calledAEString);
            AAssociateRQ rq = asAccepted.getAAssociateRQ();
            rq.setCalledAET(calledAEString);
            if (ae.getUseCallingAETitle() != null)
                rq.setCalledAET(ae.getUseCallingAETitle());
            else if (!ae.getAETitle().equals("*"))
                rq.setCallingAET(ae.getAETitle());
            Association asInvoked = ae.connect(calledAE, rq);
            forward(asAccepted, asInvoked, pc, data, eventInfo, file);
        } catch (Exception e) {
            abortForward(pc, asAccepted,
                    Commands.mkNEventReportRSP(data, Status.ProcessingFailure), e
                            .getLocalizedMessage());
        } finally {
            SafeClose.close(dis);
        }
    }

    private void abortForward(PresentationContext pc, Association asAccepted, Attributes response,
            String logMessage) {
        LOG.warn(logMessage);
        try {
            asAccepted.writeDimseRSP(pc, response, null);
            asAccepted.release();
        } catch (IOException e) {
            LOG.warn(asAccepted + ": Failed to make N-EVENT-REPORT-RSP:" + e);
        }
    }

    private void forward(final Association asAccepted, Association asInvoked,
            final PresentationContext pc, Attributes rq, Attributes data, final File file)
            throws IOException, InterruptedException {
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
                    deleteTransactionUidFile(asAccepted, file);
                    asInvoked.release();
                } catch (IOException e) {
                    int status = cmd.getInt(Tag.Status, -1);
                    LOG.warn("{}: Failed to forward file {} with error status {}",
                            new Object[] { asAccepted, file, Integer.toHexString(status) + 'H' });
                    asAccepted.setProperty(FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    rename(asAccepted, file);
                }
            }
        };
        asInvoked.neventReport(cuid, iuid, eventTypeId, data, tsuid, rspHandler);
    }

    private void deleteTransactionUidFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: DELETE {}", new Object[] { as, file });
        else {
            LOG.warn("{}: Failed to DELETE {}", new Object[] { as, file });
        }
    }

    private void rename(Association as, File file) {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: RENAME {} to {}", new Object[] {as, file, dst});
        }
        else
            LOG.warn("{}: Failed to RENAME {} to {}", new Object[] {as, file, dst});
    }
}
