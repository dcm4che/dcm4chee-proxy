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

package org.dcm4chee.proxy.conf;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map.Entry;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationStateException;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.InputStreamDataWriter;
import org.dcm4che.net.NoPresentationContextException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class ForwardFiles {

    protected static final Logger LOG = LoggerFactory.getLogger(ForwardFiles.class);

    public void execute(ProxyApplicationEntity pae) {
        HashMap<String, Schedule> forwardSchedules = pae.getForwardSchedules();

        processCStore(pae, forwardSchedules);
        processNAction(pae, forwardSchedules);
        processNCreate(pae, forwardSchedules);
        processNSet(pae, forwardSchedules);
    }

    private void processNSet(ProxyApplicationEntity pae, HashMap<String, Schedule> forwardSchedules) {
        for (String calledAET : pae.getNSetDirectoryPath().list()) {
            //process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(pae,
                        new File(pae.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(pae)), calledAET, "nset");
            else
                for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey()) && entry.getValue().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(pae,
                                new File(pae.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(pae)), calledAET,
                                "nset");
        }
    }

    private void processNCreate(ProxyApplicationEntity pae, HashMap<String, Schedule> forwardSchedules) {
        for (String calledAET : pae.getNCreateDirectoryPath().list()) {
            //process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(pae,
                        new File(pae.getNCreateDirectoryPath(), calledAET).listFiles(fileFilter(pae)), calledAET,
                        "ncreate");
            else
                for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey()) && entry.getValue().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(pae,
                                new File(pae.getNCreateDirectoryPath(), calledAET).listFiles(fileFilter(pae)),
                                calledAET, "ncreate");
        }
    }

    private void processNAction(ProxyApplicationEntity pae, HashMap<String, Schedule> forwardSchedules) {
        for (String calledAET : pae.getNactionDirectoryPath().list()) {
            //process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledNAction(pae, pae.getNactionDirectoryPath().listFiles(fileFilter(pae)), calledAET);
            else
                for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey()) && entry.getValue().isNow(new GregorianCalendar()))
                        startForwardScheduledNAction(pae, pae.getNactionDirectoryPath().listFiles(fileFilter(pae)),
                                calledAET);
        }
    }

    private void processCStore(ProxyApplicationEntity pae, HashMap<String, Schedule> forwardSchedules) {
        for (String calledAET : pae.getCStoreDirectoryPath().list()) {
            //process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledCStoreFiles(pae, calledAET);
            else
                for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey()) && entry.getValue().isNow(new GregorianCalendar()))
                        startForwardScheduledCStoreFiles(pae, calledAET);
        }
    }

    private FileFilter fileFilter(final ProxyApplicationEntity pae) {
        final long now = System.currentTimeMillis();
        return new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                String path = pathname.getPath();
                if (path.endsWith(".dcm"))
                    return true;
                String file = path.substring(path.lastIndexOf(ProxyApplicationEntity.getSeparator()) + 1);
                for (Retry retry : pae.getRetries())
                    if (path.endsWith(retry.getRetryObject().getSuffix()) 
                            && numRetry(retry, file)
                            && (now > (pathname.lastModified() + (retry.delay * 1000))))
                        return true;
                return false;
            }

            private boolean numRetry(Retry retry, String file) {
                int currentRetries = file.split(retry.getRetryObject().getSuffix(), -1).length - 1;
                return currentRetries < retry.numberOfRetries;
            }
        };
    }

    private void startForwardScheduledMPPS(final ProxyApplicationEntity pae, final File[] files,
            final String destinationAETitle, final String protocol) {
        ((ProxyDevice) pae.getDevice()).getFileForwardingExecutor().execute(new Runnable() {

            @Override
            public void run() {
                forwardScheduledMPPS(pae, files, destinationAETitle, protocol);
            }
        });
    }

    protected void forwardScheduledMPPS(ProxyApplicationEntity pae, File[] files, String destinationAETitle,
            String protocol) {
        for (File file : files) {
            try {
                Attributes fmi = readFileMetaInformation(file);
                if (protocol == "nset" && pendingNCreateForwarding(pae, destinationAETitle, fmi))
                    return;

                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.ModalityPerformedProcedureStepSOPClass,
                        UID.ExplicitVRLittleEndian));
                rq.setCallingAET(fmi.getString(Tag.SourceApplicationEntityTitle));
                rq.setCalledAET(destinationAETitle);
                Association as = pae.connect(pae.getDestinationAE(destinationAETitle), rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledMPPS(as, file, fmi, protocol);
                    } else {
                        as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix());
                        rename(as, file);
                    }
                } finally {
                    if (as != null && as.isReadyForDataTransfer()) {
                        try {
                            as.waitForOutstandingRSP();
                            as.release();
                        } catch (InterruptedException e) {
                            LOG.debug(as + ": unexpected exception", e);
                        } catch (IOException e) {
                            LOG.debug(as + ": failed to release association", e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOG.error("Connection exception: " + e.getMessage());
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
            } catch (IOException e) {
                LOG.error("Unable to read from file: " + e.getMessage());
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
            }
        }
    }

    private boolean pendingNCreateForwarding(ProxyApplicationEntity pae, String destinationAETitle, Attributes fmi) {
        File dir = new File(pae.getNCreateDirectoryPath(), destinationAETitle);
        if (!dir.exists())
            return false;

        String[] files = dir.list();
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        for (String file : files)
            if (file.startsWith(iuid))
                return true;

        return false;
    }

    private void forwardScheduledMPPS(final Association as, final File file, Attributes fmi, String protocol)
            throws IOException, InterruptedException {
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String tsuid = UID.ExplicitVRLittleEndian;
        DicomInputStream in = new DicomInputStream(file);
        Attributes attrs = in.readDataset(-1, -1);
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                super.onDimseRSP(as, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case Status.Success:
                    LOG.debug("{}: forwarded file {} with status {}",
                            new Object[] { as, file, Integer.toHexString(status) + 'H' });
                    delete(as, file);
                    break;
                default: {
                    LOG.debug("{}: failed to forward file {} with error status {}",
                            new Object[] { as, file, Integer.toHexString(status) + 'H' });
                    as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    try {
                        rename(as, file);
                    } catch (DicomServiceException e) {
                        e.printStackTrace();
                    }
                }
                }
            }
        };
        try {
            if (protocol == "ncreate")
                as.ncreate(cuid, iuid, attrs, tsuid, rspHandler);
            else
                as.nset(cuid, iuid, attrs, tsuid, rspHandler);
        } finally {
            SafeClose.close(in);
        }
    }

    private void startForwardScheduledNAction(final ProxyApplicationEntity pae, final File[] files,
            final String destinationAETitle) {
        ((ProxyDevice) pae.getDevice()).getFileForwardingExecutor().execute(new Runnable() {

            @Override
            public void run() {
                forwardScheduledNAction(pae, files, destinationAETitle);
            }
        });
    }

    private void forwardScheduledNAction(ProxyApplicationEntity pae, File[] files, String destinationAETitle) {
        for (File file : files) {
            try {
                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModelSOPClass,
                        UID.ExplicitVRLittleEndian));
                Attributes fmi = readFileMetaInformation(file);
                rq.setCallingAET(fmi.getString(Tag.SourceApplicationEntityTitle));
                rq.setCalledAET(destinationAETitle);
                Association as = pae.connect(pae.getDestinationAE(destinationAETitle), rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledNAction(pae, as, file, fmi);
                    } else {
                        as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix());
                        rename(as, file);
                    }
                } finally {
                    if (as != null && as.isReadyForDataTransfer()) {
                        try {
                            as.waitForOutstandingRSP();
                            as.release();
                        } catch (InterruptedException e) {
                            LOG.debug(as + ": unexpected exception", e);
                        } catch (IOException e) {
                            LOG.debug(as + ": failed to release association", e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOG.error("Connection exception: " + e.getMessage());
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
            } catch (IOException e) {
                LOG.error("Unable to read from file: " + e.getMessage());
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
            }
        }
    }

    private void forwardScheduledNAction(final ProxyApplicationEntity pae, final Association as, final File file,
            Attributes fmi) throws IOException, InterruptedException {
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String tsuid = UID.ExplicitVRLittleEndian;
        DicomInputStream in = new DicomInputStream(file);
        Attributes attrs = in.readDataset(-1, -1);
        final String transactionUID = attrs.getString(Tag.TransactionUID);
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {
            @Override
            public void onDimseRSP(Association asDestinationAET, Attributes cmd, Attributes data) {
                super.onDimseRSP(asDestinationAET, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case Status.Success: {
                    File dest = new File(pae.getNeventDirectoryPath(), transactionUID);
                    if (file.renameTo(dest)) {
                        dest.setLastModified(System.currentTimeMillis());
                        LOG.debug("{}: RENAME {} to {}", new Object[] { as, file, dest });
                    } else
                        LOG.debug("{}: failed to RENAME {} to {}", new Object[] { as, file, dest });
                    break;
                }
                default: {
                    LOG.debug("{}: failed to forward N-ACTION file {} with error status {}", new Object[] { as, file,
                            Integer.toHexString(status) + 'H' });
                    as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                    try {
                        rename(as, file);
                    } catch (DicomServiceException e) {
                        e.printStackTrace();
                    }
                }
                }
            }
        };
        try {
            as.naction(cuid, iuid, 1, attrs, tsuid, rspHandler);
        } finally {
            SafeClose.close(in);
        }
    }

    private void startForwardScheduledCStoreFiles(final ProxyApplicationEntity pae, final String calledAET) {
        ((ProxyDevice) pae.getDevice()).getFileForwardingExecutor().execute(new Runnable() {

            @Override
            public void run() {
                forwardScheduledCStoreFiles(pae, calledAET);
            }
        });
    }

    private void forwardScheduledCStoreFiles(ProxyApplicationEntity pae, String calledAET) {
        File dir = new File(pae.getCStoreDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter(pae));
        if (files != null && files.length > 0)
            for (ForwardTask ft : scanFiles(calledAET, files))
                try {
                    processForwardTask(pae, ft);
                } catch (DicomServiceException e) {
                    e.printStackTrace();
                }
    }

    private void processForwardTask(ProxyApplicationEntity pae, ForwardTask ft) throws DicomServiceException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        Association asInvoked = null;
        try {
            asInvoked = pae.connect(pae.getDestinationAE(rq.getCalledAET()), rq);
            for (File file : ft.getFiles()) {
                try {
                    if (asInvoked.isReadyForDataTransfer()) {
                        forwardScheduledCStoreFiles(pae, asInvoked, file);
                    } else {
                        asInvoked.setProperty(ProxyApplicationEntity.FILE_SUFFIX,
                                RetryObject.ConnectionException.getSuffix());
                        rename(asInvoked, file);
                    }
                } catch (NoPresentationContextException npc) {
                    handleForwardException(asInvoked, file, npc, RetryObject.NoPresentationContextException.getSuffix());
                } catch (AssociationStateException ass) {
                    handleForwardException(asInvoked, file, ass, RetryObject.AssociationStateException.getSuffix());
                } catch (IOException ioe) {
                    handleForwardException(asInvoked, file, ioe, RetryObject.ConnectionException.getSuffix());
                    releaseAS(asInvoked);
                }
            }
        } catch (ConfigurationException ce) {
            LOG.error("Unable to load configuration: " + ce.getMessage());
        } catch (AAssociateRJ rj) {
            handleProcessException(ft, rj, RetryObject.AAssociateRJ.getSuffix() + rj.getResult() + "-" + rj.getSource()
                    + "-" + rj.getReason());
        } catch (AAbort aa) {
            handleProcessException(ft, aa, RetryObject.AAbort.getSuffix() + aa.getSource() + "-" + aa.getReason());
        } catch (IOException e) {
            handleProcessException(ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (InterruptedException e) {
            handleProcessException(ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (IncompatibleConnectionException e) {
            handleProcessException(ft, e, RetryObject.IncompatibleConnectionException.getSuffix());
        } catch (GeneralSecurityException e) {
            handleProcessException(ft, e, RetryObject.GeneralSecurityException.getSuffix());
        } finally {
            if (asInvoked != null && asInvoked.isReadyForDataTransfer()) {
                try {
                    asInvoked.waitForOutstandingRSP();
                    asInvoked.release();
                } catch (InterruptedException e) {
                    LOG.debug(asInvoked + ": unexpected exception: " + e.getMessage());
                } catch (IOException e) {
                    LOG.debug(asInvoked + ": failed to release association: " + e.getMessage());
                }
            }
        }
    }

    private void forwardScheduledCStoreFiles(final ProxyApplicationEntity pae, final Association asInvoked,
            final File file) throws IOException, InterruptedException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            Attributes fmi = in.readFileMetaInformation();
            final String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            final String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
            final String tsuid = fmi.getString(Tag.TransferSyntaxUID);
            asInvoked.getAAssociateRQ().setCallingAET(fmi.getString(Tag.SourceApplicationEntityTitle));
            final Attributes[] ds = new Attributes[1];
            final long fileSize = file.length();
            DimseRSPHandler rspHandler = new DimseRSPHandler(asInvoked.nextMessageID()) {

                @Override
                public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                    super.onDimseRSP(asInvoked, cmd, data);
                    int status = cmd.getInt(Tag.Status, -1);
                    switch (status) {
                    case Status.Success:
                    case Status.CoercionOfDataElements:
                        if (pae.isEnableAuditLog())
                            pae.writeLogFile(asInvoked, ds[0], fileSize);
                        delete(asInvoked, file);
                        break;
                    default: {
                        LOG.debug("{}: failed to forward file {} with error status {}", new Object[] { asInvoked, file,
                                Integer.toHexString(status) + 'H' });
                        asInvoked.setProperty(ProxyApplicationEntity.FILE_SUFFIX,
                                '.' + Integer.toHexString(status) + 'H');
                        try {
                            rename(asInvoked, file);
                        } catch (DicomServiceException e) {
                            e.printStackTrace();
                        }
                    }
                    }
                }
            };
            asInvoked.cstore(cuid, iuid, 0, createDataWriter(pae, in, asInvoked, ds, cuid), tsuid, rspHandler);
        } finally {
            SafeClose.close(in);
        }
    }

    protected void releaseAS(Association asAccepted) {
        Association asInvoked = (Association) asAccepted.clearProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked != null)
            try {
                asInvoked.release();
            } catch (IOException e) {
                LOG.debug("Failed to release {} ({})", new Object[] { asInvoked, e });
            }
    }

    private Collection<ForwardTask> scanFiles(String calledAET, File[] files) {
        HashMap<String, ForwardTask> map = new HashMap<String, ForwardTask>(4);
        for (File file : files)
            addFileTo(calledAET, file, map);
        return map.values();
    }

    private void addFileTo(String calledAET, File file, HashMap<String, ForwardTask> map) {
        try {
            Attributes fmi = readFileMetaInformation(file);
            String callingAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            String tsuid = fmi.getString(Tag.TransferSyntaxUID);
            ForwardTask forwardTask = map.get(callingAET);
            if (forwardTask == null)
                map.put(callingAET, forwardTask = new ForwardTask(callingAET, calledAET));
            forwardTask.addFile(file, cuid, tsuid);
        } catch (IOException e) {
            LOG.debug("Failed to read {} ({})", new Object[] { file, e.getMessage() });
        }
    }

    private static Attributes readFileMetaInformation(File file) throws IOException {
        DicomInputStream in = new DicomInputStream(file);
        try {
            return in.readFileMetaInformation();
        } finally {
            SafeClose.close(in);
        }
    }

    private void handleForwardException(Association as, File file, Exception e, String suffix)
            throws DicomServiceException {
        LOG.debug(as + ": error processing forward task: " + e.getMessage());
        as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, suffix);
        rename(as, file);
    }

    private void handleProcessException(ForwardTask ft, Exception e, String suffix) throws DicomServiceException {
        LOG.error("Unable to connect to {}: {}", new Object[] { ft.getAAssociateRQ().getCalledAET(), e.getMessage() });
        for (File file : ft.getFiles()) {
            String path = file.getPath();
            File dst = new File(path.concat(suffix));
            if (file.renameTo(dst)) {
                dst.setLastModified(System.currentTimeMillis());
                LOG.debug("{}: RENAME to {}", new Object[] { file, dst });
            } else {
                LOG.debug("{}: failed to RENAME to {}", new Object[] { file, dst });
                throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
            }
        }
    }

    private File rename(Association as, File file) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(ProxyApplicationEntity.FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: RENAME {} to {}", new Object[] { as, file, dst });
            return dst;
        } else {
            LOG.debug("{}: failed to RENAME {} to {}", new Object[] { as, file, dst });
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private DataWriter createDataWriter(ProxyApplicationEntity pae, DicomInputStream in, Association as,
            Attributes[] ds, String cuid) throws IOException {
        AttributeCoercion ac = pae.getAttributeCoercion(as.getRemoteAET(), cuid, Role.SCP, Dimse.C_STORE_RQ);
        if (ac != null || pae.isEnableAuditLog()) {
            Attributes attrs = in.readDataset(-1, -1);
            pae.coerceAttributes(attrs, ac);
            ds[0] = attrs;
            if (pae.isEnableAuditLog())
                pae.createStartLogFile(as, ds[0]);
            return new DataWriterAdapter(attrs);
        }
        return new InputStreamDataWriter(in);
    }

    private static void delete(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: M-DELETE {}", as, file);
        else
            LOG.debug("{}: failed to M-DELETE {}", as, file);
    }

}
