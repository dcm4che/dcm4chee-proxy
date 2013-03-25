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

package org.dcm4chee.proxy.dimse;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.List;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class StgCmt extends DicomService {
    
    private ApplicationEntityCache aeCache;

    public StgCmt(ApplicationEntityCache aeCache) {
        super(UID.StorageCommitmentPushModelSOPClass);
        this.aeCache = aeCache;
    }

    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes data) throws IOException {
        switch (dimse) {
        case N_EVENT_REPORT_RQ:
            processNEventReportRQ(asAccepted, pc, dimse, rq, data);
            break;
        case N_ACTION_RQ:
            processNActionRQ(asAccepted, pc, dimse, rq, data);
            break;
        default:
            super.onDimseRQ(asAccepted, pc, dimse, rq, data);
        }
    }

    private void processNActionRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes data) throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (asInvoked == null)
            try {
                processNActionRQForwardRules(asAccepted, pc, dimse, rq, data);
            } catch (ConfigurationException e) {
                LOG.error(asAccepted + ": error processing N-ACTION-RQ: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e.getCause());
            }
        else
            try {
                onNActionRQ(asAccepted, asInvoked, pc, rq, data);
            } catch (Exception e) {
                LOG.debug(asAccepted + ": error forwarding N-ACTION-RQ: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e.getCause());
            }
    }

    private void processNActionRQForwardRules(Association as, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes data) throws IOException, ConfigurationException {
        ApplicationEntity ae = as.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        List<ForwardRule> forwardRules = proxyAEE.filterForwardRulesOnDimseRQ(as, rq, dimse);
        if (forwardRules.size() > 1)
            throw new ConfigurationException("more than one matching forward rule");
        
        ForwardRule rule = forwardRules.get(0);
        List<String> destinationAETs = proxyAEE.getDestinationAETsFromForwardRule(as, rule, data);
        switch (destinationAETs.size()) {
        case 0:
            throw new ConfigurationException("no destination");
        case 1: {
            String callingAET = (rule.getUseCallingAET() == null) ? as.getCallingAET() : rule.getUseCallingAET();
            processNActionForwardRule(as, pc, dimse, rq, data, proxyAEE, callingAET, destinationAETs.get(0));
            break;
        }
        default:
            throw new ConfigurationException("more than one destination");
        }
    }

    private void processNActionForwardRule(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes data, ProxyAEExtension pae, String callingAET, String calledAET) throws IOException,
            ConfigurationException {
        try {
            AAssociateRQ aarq = asAccepted.getAAssociateRQ();
            aarq.setCallingAET(callingAET);
            aarq.setCalledAET(calledAET);
            Association asCalled = pae.getApplicationEntity().connect(
                    aeCache.findApplicationEntity(aarq.getCalledAET()), aarq);
            asAccepted.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asCalled);
            processNActionRQ(asAccepted, pc, dimse, rq, data);
        } catch (InterruptedException e) {
            LOG.error("Unexpected exception: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException e) {
            LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e.getMessage() });
            LOG.debug(e.getMessage(), e);
        } catch (GeneralSecurityException e) {
            LOG.error("Failed to create SSL context: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

    private void processNEventReportRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes data) throws IOException {
        ProxyAEExtension proxyAEE = (ProxyAEExtension) asAccepted.getApplicationEntity().getAEExtension(
                ProxyAEExtension.class);
        File transactionUIDFile = new File(proxyAEE.getNeventDirectoryPath(), data.getString(Tag.TransactionUID));
        if (!transactionUIDFile.exists()) {
            LOG.debug(asAccepted + ": failed to load Transaction UID mapping for N-EVENT-REPORT-RQ from "
                    + asAccepted.getCallingAET());
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(rq, Status.InvalidArgumentValue));
            return;
        }

        if (pendingFileForwarding(asAccepted, data)) {
            LOG.debug("Waiting for pending file forwarding before sending NEventReportRQ for TransactionUID: "
                    + data.getString(Tag.TransactionUID));
            return;
        }

        Association asInvoked = (Association) asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            if (proxyAEE.isAssociationFromDestinationAET(asAccepted))
                forwardNEventReportRQFromDestinationAET(asAccepted, pc, rq, data, transactionUIDFile);
            else
                super.onDimseRQ(asAccepted, pc, dimse, rq, data);
        } else {
            try {
                onNEventReportRQ(asAccepted, asInvoked, pc, rq, data, transactionUIDFile);
            } catch (InterruptedException e) {
                LOG.error(asAccepted + ": error forwarding N-EVENT-REPORT-RQ: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e.getCause());
            }
        }
    }

    private boolean pendingFileForwarding(Association as, Attributes eventInfo) {
        File dir = new File(
                ((ProxyAEExtension) as.getApplicationEntity().getAEExtension(ProxyAEExtension.class))
                        .getSpoolDirectoryPath(),
                as.getCalledAET());
        if (!dir.exists())
            return false;

        String[] files = dir.list();
        Sequence referencedSOPSequence = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        Iterator<Attributes> it = referencedSOPSequence.iterator();
        while (it.hasNext()) {
            Attributes item = it.next();
            String referencedSOPInstanceUID = item.getString(Tag.ReferencedSOPInstanceUID);
            for (String file : files)
                if (file.startsWith(referencedSOPInstanceUID))
                    return true;
        }
        return false;
    }

    private void forwardNEventReportRQFromDestinationAET(Association asAccepted, PresentationContext pc,
            Attributes data, final Attributes eventInfo, File file) {
        DicomInputStream dis = null;
        String calledAEString = null;
        try {
            dis = new DicomInputStream(file);
            Attributes fmi = dis.readFileMetaInformation();
            calledAEString = fmi.getString(Tag.SourceApplicationEntityTitle);
            ApplicationEntity ae = asAccepted.getApplicationEntity();
            ApplicationEntity calledAE = aeCache.findApplicationEntity(calledAEString);
            AAssociateRQ rq = asAccepted.getAAssociateRQ();
            rq.setCalledAET(calledAEString);
            if (!ae.getAETitle().equals("*"))
                rq.setCallingAET(ae.getAETitle());
            Association asInvoked = ae.connect(calledAE, rq);
            onNEventReportRQ(asAccepted, asInvoked, pc, data, eventInfo, file);
        } catch (AAssociateRJ e) {
            LOG.error(asAccepted + ": rejected association to forward AET: " + e.getReason());
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.AAssociateRJ.getSuffix() + "0");
            rename(asAccepted, file);
        } catch (IOException e) {
            LOG.error(asAccepted + ": unexpected exception: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix() + "0");
            rename(asAccepted, file);
        } catch (ConfigurationException e) {
            LOG.error(asAccepted + ": error loading AET {} from configuration: {}", new Object[] { calledAEString, e.getMessage() });
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix() + "0");
            rename(asAccepted, file);
        } catch (InterruptedException e) {
            LOG.error(asAccepted + ": unexpected exception: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix() + "0");
            rename(asAccepted, file);
        } catch (IncompatibleConnectionException e) {
            LOG.error(asAccepted + ": incompatible connection to forward AET {}: {}", new Object[] { calledAEString, e.getMessage() });
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.IncompatibleConnectionException.getSuffix() + "0");
            rename(asAccepted, file);
        } catch (GeneralSecurityException e) {
            LOG.error(asAccepted + ": error creating SSL context: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            abortForward(pc, asAccepted, Commands.mkNEventReportRSP(data, Status.Success));
            asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.GeneralSecurityException.getSuffix() + "0");
            rename(asAccepted, file);
        } finally {
            SafeClose.close(dis);
        }
    }

    private void abortForward(PresentationContext pc, Association asAccepted, Attributes response) {
        try {
            asAccepted.writeDimseRSP(pc, response, null);
            asAccepted.release();
        } catch (IOException e) {
            LOG.error(asAccepted + ": error forwarding storage commitment: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
    }

    private void onNEventReportRQ(final Association asAccepted, Association asInvoked, final PresentationContext pc,
            Attributes rq, Attributes data, final File file) throws IOException, InterruptedException {
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
                    LOG.error("{}: failed to forward file {} with error status {}", new Object[] { asAccepted, file,
                            Integer.toHexString(status) + 'H' });
                    LOG.debug(e.getMessage(), e);
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    rename(asAccepted, file);
                }
            }
        };
        asInvoked.neventReport(cuid, iuid, eventTypeId, data, tsuid, rspHandler);
    }

    private void rename(Association as, File file) {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(ProxyAEExtension.FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.info("{}: RENAME {} to {}", new Object[] { as, file, dst });
        } else
            LOG.error("{}: failed to RENAME {} to {}", new Object[] { as, file, dst });
    }

    private void onNActionRQ(final Association asAccepted, Association asInvoked, final PresentationContext pc,
            final Attributes rq, Attributes data) throws IOException, InterruptedException {
        int actionTypeId = rq.getInt(Tag.ActionTypeID, 0);
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.RequestedSOPClassUID);
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        final String transactionUID = data.getString(Tag.TransactionUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        final ProxyAEExtension proxyAEE = (ProxyAEExtension) asAccepted.getApplicationEntity().getAEExtension(
                ProxyAEExtension.class);
        final File file = createTransactionUidFile(proxyAEE, asAccepted, data, ".dcm.fwd");
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {
            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case Status.Success: {
                    File dest = new File(proxyAEE.getNeventDirectoryPath(), transactionUID);
                    if (file.renameTo(dest)) {
                        dest.setLastModified(System.currentTimeMillis());
                        LOG.info("{}: RENAME {} to {}", new Object[] { asAccepted, file, dest });
                    } else
                        LOG.error("{}: failed to RENAME {} to {}", new Object[] { asAccepted, file, dest });
                    try {
                        asAccepted.writeDimseRSP(pc, cmd, data);
                    } catch (IOException e) {
                        LOG.error(asAccepted + ": failed to forward N-ACTION-RSP: " + e.getMessage());
                        LOG.debug(e.getMessage(), e);
                        asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, ".dcm");
                        rename(asAccepted, file);
                    }
                    break;
                }
                default: {
                    LOG.warn("{}: failed to forward N-ACTION file {} with error status {}", new Object[] { asAccepted,
                            file, Integer.toHexString(status) + 'H' });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    rename(asAccepted, file);
                    try {
                        asAccepted.writeDimseRSP(pc, cmd);
                    } catch (IOException e) {
                        LOG.error(asAccepted + ": Failed to forward N-ACTION-RSP: " + e.getMessage());
                        LOG.debug(e.getMessage(), e);
                    }
                }
                }
            }
        };
        asInvoked.naction(cuid, iuid, actionTypeId, data, tsuid, rspHandler);
    }

    protected File createTransactionUidFile(ProxyAEExtension proxyAE, Association as, Attributes data, String suffix)
            throws DicomServiceException {
        File dir = new File(proxyAE.getNactionDirectoryPath(), as.getCalledAET());
        dir.mkdir();
        File file = new File(dir, data.getString(Tag.TransactionUID) + suffix);
        DicomOutputStream stream = null;
        try {
            stream = new DicomOutputStream(file);
            String iuid = UID.StorageCommitmentPushModelSOPInstance;
            String cuid = UID.StorageCommitmentPushModelSOPClass;
            String tsuid = UID.ExplicitVRLittleEndian;
            Attributes fmi = Attributes.createFileMetaInformation(iuid, cuid, tsuid);
            fmi.setString(Tag.SourceApplicationEntityTitle, VR.AE, as.getCallingAET());
            stream.writeDataset(fmi, data);
        } catch (Exception e) {
            LOG.error(as + ": Failed to create transaction UID file: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            file.delete();
            throw new DicomServiceException(Status.OutOfResources, e.getCause());
        } finally {
            SafeClose.close(stream);
        }
        return file;
    }

    private void deleteTransactionUidFile(Association as, File file) {
        if (file.delete())
            LOG.info("{}: DELETE {}", new Object[] { as, file });
        else {
            LOG.error("{}: failed to DELETE {}", new Object[] { as, file });
        }
    }
}
