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

package org.dcm4chee.proxy.forward;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map.Entry;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.net.ApplicationEntity;
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
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.conf.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class ForwardFiles {

    protected static final Logger LOG = LoggerFactory.getLogger(ForwardFiles.class);
    
    private ApplicationEntityCache aeCache;

    public ForwardFiles(ApplicationEntityCache aeCache) {
        this.aeCache = aeCache;
    }

    public void execute(ApplicationEntity ae) {
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        HashMap<String, ForwardOption> forwardSchedules = proxyAEE.getForwardOptions();
        processCStore(proxyAEE, forwardSchedules);
        processNAction(proxyAEE, forwardSchedules);
        processNCreate(proxyAEE, forwardSchedules);
        processNSet(proxyAEE, forwardSchedules);
    }

    private void processNSet(ProxyAEExtension proxyAEE, HashMap<String, ForwardOption> forwardSchedules) {
        for (String calledAET : proxyAEE.getNSetDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(proxyAEE,
                        new File(proxyAEE.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(proxyAEE, calledAET)),
                        calledAET, "nset");
            else
                for (Entry<String, ForwardOption> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(proxyAEE,
                                new File(proxyAEE.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(proxyAEE, calledAET)),
                                calledAET, "nset");
        }
    }

    private void processNCreate(ProxyAEExtension proxyAEE, HashMap<String, ForwardOption> forwardSchedules) {
        for (String calledAET : proxyAEE.getNCreateDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(proxyAEE,
                        new File(proxyAEE.getNCreateDirectoryPath(), calledAET).listFiles(fileFilter(proxyAEE, calledAET)),
                        calledAET, "ncreate");
            else
                for (Entry<String, ForwardOption> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(proxyAEE,
                                new File(proxyAEE.getNCreateDirectoryPath(), calledAET)
                                        .listFiles(fileFilter(proxyAEE, calledAET)), calledAET, "ncreate");
        }
    }

    private void processNAction(ProxyAEExtension proxyAEE, HashMap<String, ForwardOption> forwardSchedules) {
        for (String calledAET : proxyAEE.getNactionDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledNAction(proxyAEE, calledAET);
            else
                for (Entry<String, ForwardOption> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledNAction(proxyAEE, calledAET);
        }
    }

    private void processCStore(ProxyAEExtension proxyAEE, HashMap<String, ForwardOption> forwardSchedules) {
        for (String calledAET : proxyAEE.getCStoreDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledCStoreFiles(proxyAEE, calledAET);
            else
                for (Entry<String, ForwardOption> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledCStoreFiles(proxyAEE, calledAET);
        }
    }

    private FileFilter fileFilter(final ProxyAEExtension proxyAEE, final String calledAET) {
        final long now = System.currentTimeMillis();
        return new FileFilter() {

            @Override
            public boolean accept(File file) {
                String path = file.getPath();
                if (path.endsWith(".dcm"))
                    return true;

                if (path.endsWith(".part"))
                    return false;

                try {
                    String suffix = path.substring(path.lastIndexOf('.'));
                    Retry matchingRetry = getMatchingRetry(proxyAEE, suffix);
                    if (matchingRetry == null)
                        if (proxyAEE.isDeleteFailedDataWithoutRetryConfiguration())
                            deleteFailedFile(proxyAEE, calledAET, file, ": delete files without retry configuration is ENABLED", 0);
                        else
                            moveToNoRetryPath(proxyAEE, file, ": delete files without retry configuration is DISABLED");
                    else if (checkNumberOfRetries(proxyAEE, matchingRetry, suffix, file, calledAET)
                            && (now > (file.lastModified() + (matchingRetry.delay * 1000))))
                        return true;
                } catch (IndexOutOfBoundsException e) {
                    LOG.error("Error parsing suffix of " + path);
                    moveToNoRetryPath(proxyAEE, file, "(error parsing suffix)");
                }
                return false;
            }
        };
    }

    private boolean checkNumberOfRetries(ProxyAEExtension proxyAEE, Retry retry, String suffix, File file,
            String calledAET) {
        int currentRetries = 0;
        String substring = suffix.substring(retry.getRetryObject().getSuffix().length());
        if (!substring.isEmpty())
            try {
                currentRetries = Integer.parseInt(substring);
            } catch (NumberFormatException e) {
                LOG.error("Error parsing number of retries in suffix of file " + file.getName());
                moveToNoRetryPath(proxyAEE, file, ": error parsing suffix");
                return false;
            }
        boolean send = currentRetries < retry.numberOfRetries;
        if (!send) {
            String reason = ": max number of retries = " + retry.getNumberOfRetries();
            if (sendToFallbackAET(proxyAEE, calledAET))
                moveToFallbackAetDir(proxyAEE, file, calledAET, reason);
            else if (retry.deleteAfterFinalRetry)
                deleteFailedFile(proxyAEE, calledAET, file, reason + " and delete after final retry is ENABLED", currentRetries);
            else
                moveToNoRetryPath(proxyAEE, file, reason);
        }
        return send;
    }

    private void moveToFallbackAetDir(ProxyAEExtension proxyAEE, File pathname, String calledAET, String reason) {
        String path = pathname.getAbsolutePath();
        File dstDir = new File(path.substring(0, path.indexOf(calledAET)) + proxyAEE.getFallbackDestinationAET());
        dstDir.mkdir();
        String fileName = pathname.getName();
        File dst = new File(dstDir, fileName.substring(0, fileName.indexOf(".")) + ".dcm");
        if (pathname.renameTo(dst))
            LOG.info("Rename {} to {} {} and fallback AET is {}",
                    new Object[] { pathname, dst, reason, proxyAEE.getFallbackDestinationAET() });
        else
            LOG.error("Failed to rename {} to {}", new Object[] { pathname, dst });
    }

    private boolean sendToFallbackAET(ProxyAEExtension proxyAEE, String destinationAET) {
        if (proxyAEE.getFallbackDestinationAET() != null)
            if (!destinationAET.equals(proxyAEE.getFallbackDestinationAET()))
                return true;
        return false;
    }

    protected Retry getMatchingRetry(ProxyAEExtension proxyAEE, String suffix) {
        for (Retry retry : proxyAEE.getRetries())
            if (suffix.startsWith(retry.getRetryObject().getSuffix()))
                return retry;
        return null;
    }

    protected void moveToNoRetryPath(ProxyAEExtension proxyAEE, File pathname, String reason) {
        String path = pathname.getPath();
        String spoolDirPath = proxyAEE.getSpoolDirectoryPath().getPath();
        String subPath = path.substring(path.indexOf(spoolDirPath) + spoolDirPath.length(),
                path.indexOf(pathname.getName()));
        File dstDir = new File(proxyAEE.getNoRetryPath().getPath() + subPath);
        dstDir.mkdirs();
        File dstFile = new File(dstDir, pathname.getName());
        if (pathname.renameTo(dstFile))
            LOG.info("rename {} to {} {}", new Object[] { pathname, dstFile, reason });
        else
            LOG.error("Failed to rename {} to {}", new Object[] { pathname, dstFile });
    }

    private void deleteFailedFile(ProxyAEExtension proxyAEE, String calledAET, File file, String reason, Integer retry) {
        try {
            if (proxyAEE.isEnableAuditLog()) {
                Attributes fmi = readFileMetaInformation(file);
                String callingAET = fmi.getString(Tag.SourceApplicationEntityTitle);
                Attributes attrs = parse(file);
                proxyAEE.createStartLogFile(AuditDirectory.DELETED, callingAET, calledAET, 
                        proxyAEE.getApplicationEntity().getConnections().get(0).getHostname(), attrs, retry);
                proxyAEE.writeLogFile(AuditDirectory.DELETED, callingAET, calledAET, attrs, file.length(), retry);
            }
        } catch (Exception e) {
            LOG.error("Failed to create log file: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
        }
        if (file.delete()) {
            LOG.info("Delete {} {}", file, reason);
        } else {
            LOG.error("Failed to delete {}", file);
        }
    }

    protected Attributes parse(File file)
            throws DicomServiceException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            in.setIncludeBulkData(IncludeBulkData.NO);
            return in.readDataset(-1, Tag.PixelData);
        } catch (IOException e) {
            LOG.warn("Failed to decode dataset: " + e.getMessage());
            LOG.debug(e.getMessage(), e);
            throw new DicomServiceException(Status.CannotUnderstand, e.getCause());
        } finally {
            SafeClose.close(in);
        }
    }

    private void startForwardScheduledMPPS(final ProxyAEExtension proxyAEE, final File[] files,
            final String destinationAETitle, final String protocol) {
        ((ProxyDeviceExtension) proxyAEE.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            forwardScheduledMPPS(proxyAEE, files, destinationAETitle, protocol);
                        } catch (IOException e) {
                            LOG.error("Error forwarding scheduled MPPS " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                });
    }

    protected void forwardScheduledMPPS(ProxyAEExtension proxyAEE, File[] files, String destinationAETitle,
            String protocol) throws IOException {
        for (File file : files) {
            Attributes fmi = null;
            try {
                fmi = readFileMetaInformation(file);
            } catch (IOException e) { 
                LOG.debug("File {} already renamed or deleted by another thread", file.getPath());
                return; 
            }
            String callingAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            try {
                if (protocol == "nset" && pendingNCreateForwarding(proxyAEE, destinationAETitle, fmi))
                    return;

                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.ModalityPerformedProcedureStepSOPClass,
                        UID.ExplicitVRLittleEndian));
                rq.setCallingAET(callingAET);
                rq.setCalledAET(destinationAETitle);
                Association as = proxyAEE.getApplicationEntity().connect(
                        aeCache.findApplicationEntity(destinationAETitle), rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledMPPS(proxyAEE, as, file, fmi, protocol);
                    } else {
                        renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET,
                                destinationAETitle);
                    }
                } finally {
                    if (as != null && as.isReadyForDataTransfer()) {
                        try {
                            as.waitForOutstandingRSP();
                            as.release();
                        } catch (InterruptedException e) {
                            LOG.error(as + ": unexpected exception: " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        } catch (IOException e) {
                            LOG.error(as + ": failed to release association: " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.error("Error connecting to {}: {} ", destinationAETitle,  e.getMessage());
                renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET, destinationAETitle);
            } catch (InterruptedException e) {
                LOG.error("Connection exception: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET, destinationAETitle);
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.IncompatibleConnectionException.getSuffix(), file, callingAET,
                        destinationAETitle);
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.ConfigurationException.getSuffix(), file, callingAET,
                        destinationAETitle);
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.GeneralSecurityException.getSuffix(), file, callingAET,
                        destinationAETitle);
            }
        }
    }

    private void writeFailedAuditLogMessage(ProxyAEExtension proxyAEE, File file, Attributes fmi, String calledAET)
            throws IOException {
        if (proxyAEE.isEnableAuditLog()) {
            if (fmi == null)
                fmi = readFileMetaInformation(file);
            Attributes attrs = parse(file);
            String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            int retry = getCurrentRetries(proxyAEE, file);
            proxyAEE.createStartLogFile(AuditDirectory.FAILED, sourceAET, calledAET, 
                    proxyAEE.getApplicationEntity().getConnections().get(0).getHostname(), attrs, retry);
            proxyAEE.writeLogFile(AuditDirectory.FAILED, sourceAET, calledAET, attrs, file.length(), retry);
        }
    }

    private boolean pendingNCreateForwarding(ProxyAEExtension proxyAEE, String destinationAETitle, Attributes fmi) {
        File dir = new File(proxyAEE.getNCreateDirectoryPath(), destinationAETitle);
        if (!dir.exists())
            return false;

        String[] files = dir.list();
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        for (String file : files)
            if (file.startsWith(iuid))
                return true;

        return false;
    }

    private void forwardScheduledMPPS(final ProxyAEExtension proxyAEE, final Association as, final File file,
            final Attributes fmi, String protocol) throws IOException, InterruptedException {
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
                    deleteSendFile(as, file);
                    break;
                default: {
                    LOG.debug("{}: failed to forward file {} with error status {}",
                            new Object[] { as, file, Integer.toHexString(status) + 'H' });
                    try {
                        renameFile(proxyAEE, '.' + Integer.toHexString(status) + 'H', file, as.getCallingAET(),
                                as.getCalledAET());
                    } catch (IOException e) {
                        LOG.error(as + ": error renaming file: " + e.getMessage());
                        LOG.debug(e.getMessage(), e);
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

    private void startForwardScheduledNAction(final ProxyAEExtension proxyAEE, final String destinationAETitle) {
        ((ProxyDeviceExtension) proxyAEE.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            forwardScheduledNAction(proxyAEE, destinationAETitle);
                        } catch (IOException e) {
                            LOG.error("Error forwarding scheduled NAction: " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                });
    }

    private void forwardScheduledNAction(ProxyAEExtension proxyAEE, String calledAET)
            throws IOException {
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter(proxyAEE, calledAET));
        if (files == null || files.length > 0)
            return;

        for (File file : files) {
            Attributes fmi = readFileMetaInformation(file);
            String callingAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            try {
                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModelSOPClass,
                        UID.ExplicitVRLittleEndian));
                rq.setCallingAET(callingAET);
                rq.setCalledAET(calledAET);
                Association as = proxyAEE.getApplicationEntity().connect(
                        aeCache.findApplicationEntity(calledAET), rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledNAction(proxyAEE, as, file, fmi);
                    } else {
                        renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET,
                                calledAET);
                    }
                } finally {
                    if (as != null && as.isReadyForDataTransfer()) {
                        try {
                            as.waitForOutstandingRSP();
                            as.release();
                        } catch (InterruptedException e) {
                            LOG.error(as + ": unexpected exception: " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        } catch (IOException e) {
                            LOG.error(as + ": failed to release association: " + e.getMessage());
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOG.error("Connection exception: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET, calledAET);
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.IncompatibleConnectionException.getSuffix(), file, callingAET,
                        calledAET);
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.ConfigurationException.getSuffix(), file, callingAET,
                        calledAET);
            } catch (IOException e) {
                LOG.error("Unable to read from file: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, callingAET, calledAET);
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
                renameFile(proxyAEE, RetryObject.GeneralSecurityException.getSuffix(), file, callingAET,
                        calledAET);
            }
        }
    }

    private void forwardScheduledNAction(final ProxyAEExtension proxyAEE, final Association as, final File file,
            final Attributes fmi) throws IOException, InterruptedException {
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
                    File dest = new File(proxyAEE.getNeventDirectoryPath(), transactionUID);
                    if (file.renameTo(dest)) {
                        dest.setLastModified(System.currentTimeMillis());
                        LOG.info("{}: rename {} to {}", new Object[] { as, file, dest });
                    } else
                        LOG.error("{}: failed to rename {} to {}", new Object[] { as, file, dest });
                    break;
                }
                default: {
                    LOG.error("{}: failed to forward N-ACTION file {} with error status {}", new Object[] { as, file,
                            Integer.toHexString(status) + 'H' });
                    try {
                        renameFile(proxyAEE, '.' + Integer.toHexString(status) + 'H', file, as.getCallingAET(),
                                as.getCalledAET());
                    } catch (IOException e) {
                        LOG.error("Error renaming file {}: {}", new Object[]{file.getPath(), e.getMessage()});
                        LOG.debug(e.getMessage(), e);
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

    private void startForwardScheduledCStoreFiles(final ProxyAEExtension proxyAEE, final String calledAET) {
        ((ProxyDeviceExtension) proxyAEE.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        forwardScheduledCStoreFiles(proxyAEE, calledAET);
                    }
                });
    }

    private void forwardScheduledCStoreFiles(ProxyAEExtension proxyAEE, String calledAET) {
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter(proxyAEE, calledAET));
        if (files != null && files.length > 0)
            for (ForwardTask ft : scanFiles(calledAET, files))
                try {
                    processForwardTask(proxyAEE, ft);
                } catch (IOException e) {
                    LOG.error("Error processing forwarding files: " + e.getMessage());
                    LOG.debug(e.getMessage(), e);
                }
    }

    private void processForwardTask(ProxyAEExtension proxyAEE, ForwardTask ft)
            throws IOException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        Association asInvoked = null;
        try {
            asInvoked = proxyAEE.getApplicationEntity().connect(aeCache.findApplicationEntity(rq.getCalledAET()), rq);
            for (File file : ft.getFiles()) {
                try {
                    if (asInvoked.isReadyForDataTransfer()) {
                        forwardScheduledCStoreFiles(proxyAEE, asInvoked, file);
                    } else {
                        renameFile(proxyAEE, RetryObject.ConnectionException.getSuffix(), file, rq.getCallingAET(),
                                rq.getCalledAET());
                    }
                } catch (NoPresentationContextException npc) {
                    handleForwardException(proxyAEE, asInvoked, file, npc,
                            RetryObject.NoPresentationContextException.getSuffix());
                } catch (AssociationStateException ass) {
                    handleForwardException(proxyAEE, asInvoked, file, ass,
                            RetryObject.AssociationStateException.getSuffix());
                } catch (IOException ioe) {
                    handleForwardException(proxyAEE, asInvoked, file, ioe, RetryObject.ConnectionException.getSuffix());
                    releaseAS(asInvoked);
                }
            }
        } catch (ConfigurationException ce) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, ce, RetryObject.ConfigurationException.getSuffix());
        } catch (AAssociateRJ rj) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, rj, RetryObject.AAssociateRJ.getSuffix());
        } catch (AAbort aa) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, aa, RetryObject.AAbort.getSuffix());
        } catch (IOException e) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (InterruptedException e) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (IncompatibleConnectionException e) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, e, RetryObject.IncompatibleConnectionException.getSuffix());
        } catch (GeneralSecurityException e) {
            handleProcessForwardTaskException(proxyAEE, rq, ft, e, RetryObject.GeneralSecurityException.getSuffix());
        } finally {
            if (asInvoked != null && asInvoked.isReadyForDataTransfer()) {
                try {
                    asInvoked.waitForOutstandingRSP();
                    asInvoked.release();
                } catch (InterruptedException e) {
                    LOG.error(asInvoked + ": unexpected exception: " + e.getMessage());
                    LOG.debug(e.getMessage(), e);
                } catch (IOException e) {
                    LOG.error(asInvoked + ": failed to release association: " + e.getMessage());
                    LOG.debug(e.getMessage(), e);
                }
            }
        }
    }

    private void forwardScheduledCStoreFiles(final ProxyAEExtension proxyAEE, final Association asInvoked,
            final File file) throws IOException, InterruptedException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            Attributes fmi = in.readFileMetaInformation();
            final String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            final String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
            final String tsuid = fmi.getString(Tag.TransferSyntaxUID);
            final String callingAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            asInvoked.getAAssociateRQ().setCallingAET(callingAET);
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
                        if (proxyAEE.isEnableAuditLog()) {
                            proxyAEE.writeLogFile(AuditDirectory.TRANSFERRED, callingAET, asInvoked.getRemoteAET(), ds[0], fileSize, -1);
                        }
                        deleteSendFile(asInvoked, file);
                        break;
                    default: {
                        LOG.debug("{}: failed to forward file {} with error status {}", new Object[] { asInvoked, file,
                                Integer.toHexString(status) + 'H' });
                        try {
                            renameFile(proxyAEE, '.' + Integer.toHexString(status) + 'H', file,
                                    asInvoked.getCallingAET(), asInvoked.getCalledAET());
                        } catch (Exception e) {
                            LOG.error("{}: error renaming file {}: {}", new Object[]{asInvoked, file.getPath(), e.getMessage()});
                            LOG.debug(e.getMessage(), e);
                        }
                    }
                    }
                }
            };
            asInvoked.cstore(cuid, iuid, 0, createDataWriter(proxyAEE, in, asInvoked, ds, cuid), tsuid, rspHandler);
        } finally {
            SafeClose.close(in);
        }
    }

    private Integer getCurrentRetries(ProxyAEExtension proxyAEE, File file){
        String suffix = file.getName().substring(file.getName().lastIndexOf('.'));
        Retry matchingRetry = getMatchingRetry(proxyAEE, suffix);
        if (matchingRetry != null) {
            String substring = suffix.substring(matchingRetry.getRetryObject().getSuffix().length());
            if (!substring.isEmpty())
                try {
                    return Integer.parseInt(substring);
                } catch (NumberFormatException e) {
                    LOG.error("Error parsing number of retries in suffix of file " + file.getName());
                    LOG.debug(e.getMessage(), e);
                }
        }
        return 1;
    }
    protected void releaseAS(Association asAccepted) {
        Association asInvoked = (Association) asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (asInvoked != null)
            try {
                asInvoked.release();
            } catch (IOException e) {
                LOG.debug("Failed to release {}: {}", new Object[] { asInvoked, e.getMessage() });
                LOG.debug(e.getMessage(), e);
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
            LOG.debug("Failed to read {}: {}", new Object[] { file, e.getMessage() });
            LOG.debug(e.getMessage(), e);
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

    private void handleForwardException(ProxyAEExtension proxyAEE, Association as, File file, Exception e, String suffix)
            throws IOException {
        LOG.error(as + ": error processing forward task: " + e.getMessage());
        as.setProperty(ProxyAEExtension.FILE_SUFFIX, suffix);
        renameFile(proxyAEE, suffix, file, as.getCalledAET(), as.getCalledAET());
    }

    private void handleProcessForwardTaskException(ProxyAEExtension proxyAEE, AAssociateRQ rq, ForwardTask ft,
            Exception e, String suffix) throws IOException {
        LOG.error("Unable to connect to {}: {}", new Object[] { ft.getAAssociateRQ().getCalledAET(), e.getMessage() });
        for (File file : ft.getFiles()) {
            renameFile(proxyAEE, suffix, file, rq.getCallingAET(), rq.getCalledAET());
        }
    }

    private void renameFile(ProxyAEExtension proxyAEE, String suffix, File file, String callingAET, String calledAET)
            throws IOException {
        File dst = setFileSuffix(file, suffix);
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.info("Rename {} to {}", new Object[] { file, dst });
            writeFailedAuditLogMessage(proxyAEE, dst, null, calledAET);
        } else {
            LOG.error("Failed to rename {} to {}", new Object[] { file, dst });
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private File setFileSuffix(File file, String newSuffix) {
        String path = file.getPath();
        int indexOfNewSuffix = path.lastIndexOf(newSuffix);
        if (indexOfNewSuffix == -1)
            return new File(path + newSuffix + "1");

        int indexOfNumRetries = indexOfNewSuffix + newSuffix.length();
        int indexOfNextSuffix = path.indexOf('.', indexOfNewSuffix + 1);
        if (indexOfNextSuffix == -1) {
            String substring = path.substring(indexOfNumRetries);
            int previousNumRetries = Integer.parseInt(substring);
            return new File(path.substring(0, indexOfNumRetries) + Integer.toString(previousNumRetries + 1));
        }
        int previousNumRetries = Integer.parseInt(path.substring(indexOfNumRetries, indexOfNextSuffix));
        String substringStart = path.substring(0, indexOfNewSuffix);
        String substringEnd = path.substring(indexOfNextSuffix);
        String pathname = substringStart + substringEnd + newSuffix + Integer.toString(previousNumRetries + 1);
        return new File(pathname);
    }

    private DataWriter createDataWriter(ProxyAEExtension proxyAEE, DicomInputStream in, Association as, Attributes[] ds,
            String cuid) throws IOException {
        AttributeCoercion ac = proxyAEE.getAttributeCoercion(as.getRemoteAET(), cuid, Role.SCP, Dimse.C_STORE_RQ);
        if (ac != null || proxyAEE.isEnableAuditLog()) {
            Attributes attrs = in.readDataset(-1, -1);
            proxyAEE.coerceAttributes(attrs, ac, as.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class));
            ds[0] = attrs;
            if (proxyAEE.isEnableAuditLog())
                proxyAEE.createStartLogFile(AuditDirectory.TRANSFERRED, as.getCallingAET(), as.getCalledAET(), 
                        as.getConnection().getHostname(), ds[0], 0);
            return new DataWriterAdapter(attrs);
        }
        return new InputStreamDataWriter(in);
    }

    private static void deleteSendFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: delete {}", as, file);
        else
            LOG.debug("{}: failed to delete {}", as, file);
    }

}
