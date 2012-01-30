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

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicNActionSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class NActionSCPImpl extends BasicNActionSCP {

    public static final Logger LOG = LoggerFactory.getLogger(NActionSCPImpl.class);

    public NActionSCPImpl(){
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    @Override
    public void onNActionRQ(Association asAccepted, PresentationContext pc, Attributes rq,
            Attributes actionInfo) throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            super.onNActionRQ(asAccepted, pc, rq, actionInfo);
        } else {
            try {
                forward(asAccepted, asInvoked, pc, rq, actionInfo);
            } catch (Exception e) {
                LOG.warn("Failure in forwarding N-ACTION-RQ from " + asAccepted.getCallingAET() + " to "
                        + asAccepted.getCalledAET());
                asAccepted.writeDimseRSP(pc, Commands.mkNActionRSP(rq, Status.ProcessingFailure), null);
            }
        }
    }

    @Override
    protected Attributes action(Association as, int actionTypeID, Attributes dataset,
            Attributes rsp, Object[] handback) throws DicomServiceException {
            createTransactionUidFile(as, dataset, ".dcm");
            return null;
    }

    private void forward(final Association asAccepted, Association asInvoked,
            final PresentationContext pc, Attributes rq, Attributes data) throws IOException,
            InterruptedException {
        int actionTypeId = rq.getInt(Tag.ActionTypeID, 0);
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.RequestedSOPClassUID);
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        final String transactionUID = data.getString(Tag.TransactionUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        final File file = createTransactionUidFile(asAccepted, data, ".dcm.fwd");
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {
            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case Status.Success: {
                    File dest = new File(((ProxyApplicationEntity)asAccepted.getApplicationEntity()).getNeventDirectoryPath(), transactionUID);
                    if (file.renameTo(dest)) {
                        dest.setLastModified(System.currentTimeMillis());
                        LOG.debug("{}: RENAME {} to {}", new Object[] {asAccepted, file, dest});
                    }
                    else
                        LOG.warn("{}: Failed to RENAME {} to {}", new Object[] {asAccepted, file, dest});
                    try {
                        asAccepted.writeDimseRSP(pc, cmd, data);
                    } catch (IOException e) {
                        LOG.warn(asAccepted + ": Failed to forward N-ACTION-RSP:" + e);
                        asAccepted.setProperty(ProxyApplicationEntity.FILE_SUFFIX, ".dcm");
                        rename(asAccepted, file);
                    }
                    break;
                }
                default: {
                    LOG.warn("{}: Failed to forward N-ACTION file {} with error status {}", new Object[] {
                            asAccepted, file, Integer.toHexString(status) + 'H' });
                    asAccepted.setProperty(ProxyApplicationEntity.FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    rename(asAccepted, file);
                }
                }
            }
        };
        asInvoked.naction(cuid, iuid, actionTypeId, data, tsuid, rspHandler);
    }

    protected File createTransactionUidFile(Association as, Attributes data, String suffix)
    throws DicomServiceException {
        File file = new File(((ProxyApplicationEntity) as.getApplicationEntity())
                        .getNactionDirectoryPath(), data.getString(Tag.TransactionUID) + suffix);
        DicomOutputStream stream = null;
        try {
            stream = new DicomOutputStream(file);
            String iuid = UID.StorageCommitmentPushModelSOPInstance;
            String cuid = UID.StorageCommitmentPushModelSOPClass;
            String tsuid = UID.ImplicitVRLittleEndian;
            Attributes fmi = Attributes.createFileMetaInformation(iuid, cuid, tsuid);
            fmi.setString(Tag.SourceApplicationEntityTitle, VR.AE, as.getCallingAET());
            stream.writeDataset(fmi, data);
        } catch (Exception e) {
            LOG.warn(as + ": Failed to create transaction UID file:", e);
            file.delete();
            throw new DicomServiceException(Status.OutOfResources, e);
        } finally {
            SafeClose.close(stream);
        }
        return file;
    }
    
    private void rename(Association as, File file) {
        String path = file.getPath();
        File dst = new File(path.substring(0, path.length() - 8).concat(
                        (String) as.getProperty(ProxyApplicationEntity.FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: RENAME {} to {}", new Object[] {as, file, dst});
        }
        else {
            LOG.warn("{}: Failed to RENAME {} to {}", new Object[] {as, file, dst});
        }
    }
}
