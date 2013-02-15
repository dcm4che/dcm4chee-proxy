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
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardSchedule;
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
        ProxyAEExtension pae = ae.getAEExtension(ProxyAEExtension.class);
        HashMap<String, ForwardSchedule> forwardSchedules = pae.getForwardSchedules();
        processCStore(pae, forwardSchedules);
        processNAction(pae, forwardSchedules);
        processNCreate(pae, forwardSchedules);
        processNSet(pae, forwardSchedules);
    }

    private void processNSet(ProxyAEExtension pae, HashMap<String, ForwardSchedule> forwardSchedules) {
        for (String calledAET : pae.getNSetDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(pae,
                        new File(pae.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(pae, calledAET)),
                        calledAET, "nset");
            else
                for (Entry<String, ForwardSchedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(pae,
                                new File(pae.getNSetDirectoryPath(), calledAET).listFiles(fileFilter(pae, calledAET)),
                                calledAET, "nset");
        }
    }

    private void processNCreate(ProxyAEExtension pae, HashMap<String, ForwardSchedule> forwardSchedules) {
        for (String calledAET : pae.getNCreateDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledMPPS(pae,
                        new File(pae.getNCreateDirectoryPath(), calledAET).listFiles(fileFilter(pae, calledAET)),
                        calledAET, "ncreate");
            else
                for (Entry<String, ForwardSchedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledMPPS(pae,
                                new File(pae.getNCreateDirectoryPath(), calledAET)
                                        .listFiles(fileFilter(pae, calledAET)), calledAET, "ncreate");
        }
    }

    private void processNAction(ProxyAEExtension pae, HashMap<String, ForwardSchedule> forwardSchedules) {
        for (String calledAET : pae.getNactionDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledNAction(pae, pae.getNactionDirectoryPath().listFiles(fileFilter(pae, calledAET)),
                        calledAET);
            else
                for (Entry<String, ForwardSchedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledNAction(pae,
                                pae.getNactionDirectoryPath().listFiles(fileFilter(pae, calledAET)), calledAET);
        }
    }

    private void processCStore(ProxyAEExtension pae, HashMap<String, ForwardSchedule> forwardSchedules) {
        for (String calledAET : pae.getCStoreDirectoryPath().list()) {
            // process destinations without forward schedule
            if (!forwardSchedules.keySet().contains(calledAET))
                startForwardScheduledCStoreFiles(pae, calledAET);
            else
                for (Entry<String, ForwardSchedule> entry : forwardSchedules.entrySet())
                    if (calledAET.equals(entry.getKey())
                            && entry.getValue().getSchedule().isNow(new GregorianCalendar()))
                        startForwardScheduledCStoreFiles(pae, calledAET);
        }
    }

    private FileFilter fileFilter(final ProxyAEExtension pae, final String calledAET) {
        final long now = System.currentTimeMillis();
        return new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                String path = pathname.getPath();
                if (path.endsWith(".dcm"))
                    return true;

                if (path.endsWith(".part"))
                    return false;

                try {
                    String suffix = path.substring(path.lastIndexOf('.'));
                    Retry matchingRetry = getMatchingRetry(pae, suffix);
                    if (matchingRetry == null)
                        if (pae.isDeleteFailedDataWithoutRetryConfiguration())
                            deleteFile(pathname, ": delete files without retry configuration is ENABLED");
                        else
                            moveToNoRetryPath(pae, pathname, ": delete files without retry configuration is DISABLED");
                    else if (checkNumberOfRetries(pae, matchingRetry, suffix, pathname, calledAET)
                            && (now > (pathname.lastModified() + (matchingRetry.delay * 1000))))
                        return true;
                } catch (IndexOutOfBoundsException e) {
                    LOG.error("Error parsing suffix of " + path);
                    moveToNoRetryPath(pae, pathname, ": error parsing suffix");
                }
                return false;
            }
        };
    }

    private boolean checkNumberOfRetries(ProxyAEExtension pae, Retry retry, String suffix, File pathname,
            String calledAET) {
        String substring = suffix.substring(retry.getRetryObject().getSuffix().length());
        int currentRetries = 0;
        if (!substring.isEmpty())
            try {
                currentRetries = Integer.parseInt(substring);
            } catch (NumberFormatException e) {
                LOG.error("Error parsing number of retries in suffix of file " + pathname.getName());
                moveToNoRetryPath(pae, pathname, ": error parsing suffix");
                return false;
            }
        boolean send = currentRetries < retry.numberOfRetries;
        if (!send) {
            String reason = ": max number of retries = " + retry.getNumberOfRetries();
            if (sendToFallbackAET(pae, calledAET))
                moveToFallbackAetDir(pae, pathname, calledAET, reason);
            else if (retry.deleteAfterFinalRetry)
                deleteFile(pathname, reason + " and delete after final retry is ENABLED");
            else
                moveToNoRetryPath(pae, pathname, reason);
        }
        return send;
    }

    private void moveToFallbackAetDir(ProxyAEExtension pae, File pathname, String calledAET, String reason) {
        String path = pathname.getAbsolutePath();
        File dstDir = new File(path.substring(0, path.indexOf(calledAET)) + pae.getFallbackDestinationAET());
        dstDir.mkdir();
        String fileName = pathname.getName();
        File dst = new File(dstDir, fileName.substring(0, fileName.indexOf(".dcm") + 4));
        if (pathname.renameTo(dst))
            LOG.info("rename {} to {} {} and fallback AET is {}",
                    new Object[] { pathname, dst, reason, pae.getFallbackDestinationAET() });
        else
            LOG.error("Failed to rename {} to {}", new Object[] { pathname, dst });
    }

    private boolean sendToFallbackAET(ProxyAEExtension pae, String destinationAET) {
        if (pae.getFallbackDestinationAET() != null)
            if (!destinationAET.equals(pae.getFallbackDestinationAET()))
                return true;
        return false;
    }

    protected Retry getMatchingRetry(ProxyAEExtension pae, String suffix) {
        for (Retry retry : pae.getRetries())
            if (suffix.startsWith(retry.getRetryObject().getSuffix()))
                return retry;
        return null;
    }

    protected void moveToNoRetryPath(ProxyAEExtension pae, File pathname, String reason) {
        String path = pathname.getPath();
        String spoolDirPath = pae.getSpoolDirectoryPath().getPath();
        String subPath = path.substring(path.indexOf(spoolDirPath) + spoolDirPath.length(),
                path.indexOf(pathname.getName()));
        File dstDir = new File(pae.getNoRetryPath().getPath() + subPath);
        dstDir.mkdirs();
        File dstFile = new File(dstDir, pathname.getName());
        if (pathname.renameTo(dstFile))
            LOG.info("rename {} to {} {}", new Object[] { pathname, dstFile, reason });
        else
            LOG.error("Failed to rename {} to {}", new Object[] { pathname, dstFile });
    }

    private void deleteFile(File file, String reason) {
        if (file.delete())
            LOG.info("delete {} {}", file, reason);
        else
            LOG.error("Failed to delete {}", file);
    }

    private void startForwardScheduledMPPS(final ProxyAEExtension pae, final File[] files,
            final String destinationAETitle, final String protocol) {
        ((ProxyDeviceExtension) pae.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            forwardScheduledMPPS(pae, files, destinationAETitle, protocol);
                        } catch (DicomServiceException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    protected void forwardScheduledMPPS(ProxyAEExtension pae, File[] files, String destinationAETitle, String protocol) throws DicomServiceException {
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
                Association as = pae.getApplicationEntity().connect(aeCache.findApplicationEntity(destinationAETitle),
                        rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledMPPS(as, file, fmi, protocol);
                    } else {
                        File dst = setFileSuffix(file, RetryObject.ConnectionException.getSuffix());
                        rename(as, file, dst);
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
                renameFile(RetryObject.ConnectionException.getSuffix(), file);
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
                renameFile(RetryObject.IncompatibleConnectionException.getSuffix(), file);
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
                renameFile(RetryObject.ConfigurationException.getSuffix(), file);
            } catch (IOException e) {
                LOG.error("Unable to read from file: " + e.getMessage());
                renameFile(RetryObject.ConnectionException.getSuffix(), file);
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
                renameFile(RetryObject.GeneralSecurityException.getSuffix(), file);
            }
        }
    }

    private boolean pendingNCreateForwarding(ProxyAEExtension pae, String destinationAETitle, Attributes fmi) {
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
                    try {
                        File dst = setFileSuffix(file, '.' + Integer.toHexString(status) + 'H');
                        rename(as, file, dst);
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

    private void startForwardScheduledNAction(final ProxyAEExtension pae, final File[] files,
            final String destinationAETitle) {
        ((ProxyDeviceExtension) pae.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            forwardScheduledNAction(pae, files, destinationAETitle);
                        } catch (DicomServiceException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void forwardScheduledNAction(ProxyAEExtension pae, File[] files, String destinationAETitle) 
            throws DicomServiceException {
        for (File file : files) {
            try {
                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModelSOPClass,
                        UID.ExplicitVRLittleEndian));
                Attributes fmi = readFileMetaInformation(file);
                rq.setCallingAET(fmi.getString(Tag.SourceApplicationEntityTitle));
                rq.setCalledAET(destinationAETitle);
                Association as = pae.getApplicationEntity().connect(aeCache.findApplicationEntity(destinationAETitle),
                        rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledNAction(pae, as, file, fmi);
                    } else {
                        File dst = setFileSuffix(file, RetryObject.ConnectionException.getSuffix());
                        rename(as, file, dst);
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
                renameFile(RetryObject.ConnectionException.getSuffix(), file);
            } catch (IncompatibleConnectionException e) {
                LOG.error("Incompatible connection: " + e.getMessage());
                renameFile(RetryObject.IncompatibleConnectionException.getSuffix(), file);
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration: " + e.getMessage());
                renameFile(RetryObject.ConfigurationException.getSuffix(), file);
            } catch (IOException e) {
                LOG.error("Unable to read from file: " + e.getMessage());
                renameFile(RetryObject.ConnectionException.getSuffix(), file);
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context: " + e.getMessage());
                renameFile(RetryObject.GeneralSecurityException.getSuffix(), file);
            }
        }
    }

    private void forwardScheduledNAction(final ProxyAEExtension pae, final Association as, final File file,
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
                        LOG.debug("{}: rename {} to {}", new Object[] { as, file, dest });
                    } else
                        LOG.debug("{}: failed to rename {} to {}", new Object[] { as, file, dest });
                    break;
                }
                default: {
                    LOG.debug("{}: failed to forward N-ACTION file {} with error status {}", new Object[] { as, file,
                            Integer.toHexString(status) + 'H' });
                    try {
                        File dst = setFileSuffix(file, '.' + Integer.toHexString(status) + 'H');
                        rename(as, file, dst);
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

    private void startForwardScheduledCStoreFiles(final ProxyAEExtension pae, final String calledAET) {
        ((ProxyDeviceExtension) pae.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class))
                .getFileForwardingExecutor().execute(new Runnable() {

                    @Override
                    public void run() {
                        forwardScheduledCStoreFiles(pae, calledAET);
                    }
                });
    }

    private void forwardScheduledCStoreFiles(ProxyAEExtension pae, String calledAET) {
        File dir = new File(pae.getCStoreDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter(pae, calledAET));
        if (files != null && files.length > 0)
            for (ForwardTask ft : scanFiles(calledAET, files))
                try {
                    processForwardTask(pae, ft);
                } catch (DicomServiceException e) {
                    e.printStackTrace();
                }
    }

    private void processForwardTask(ProxyAEExtension pae, ForwardTask ft)
            throws DicomServiceException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        Association asInvoked = null;
        try {
            asInvoked = pae.getApplicationEntity().connect(aeCache.findApplicationEntity(rq.getCalledAET()), rq);
            for (File file : ft.getFiles()) {
                try {
                    if (asInvoked.isReadyForDataTransfer()) {
                        forwardScheduledCStoreFiles(pae, asInvoked, file);
                    } else {
                        File dst = setFileSuffix(file, RetryObject.ConnectionException.getSuffix());
                        rename(asInvoked, file, dst);
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
            handleProcessForwardTaskException(ft, rj, RetryObject.AAssociateRJ.getSuffix());
        } catch (AAbort aa) {
            handleProcessForwardTaskException(ft, aa, RetryObject.AAbort.getSuffix());
        } catch (IOException e) {
            handleProcessForwardTaskException(ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (InterruptedException e) {
            handleProcessForwardTaskException(ft, e, RetryObject.ConnectionException.getSuffix());
        } catch (IncompatibleConnectionException e) {
            handleProcessForwardTaskException(ft, e, RetryObject.IncompatibleConnectionException.getSuffix());
        } catch (GeneralSecurityException e) {
            handleProcessForwardTaskException(ft, e, RetryObject.GeneralSecurityException.getSuffix());
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

    private void forwardScheduledCStoreFiles(final ProxyAEExtension pae, final Association asInvoked, final File file)
            throws IOException, InterruptedException {
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
                        if (pae.isEnableAuditLog())
                            pae.writeLogFile(callingAET, asInvoked.getRemoteAET(), ds[0], fileSize);
                        delete(asInvoked, file);
                        break;
                    default: {
                        LOG.debug("{}: failed to forward file {} with error status {}", new Object[] { asInvoked, file,
                                Integer.toHexString(status) + 'H' });
                        try {
                            File dst = setFileSuffix(file, '.' + Integer.toHexString(status) + 'H');
                            rename(asInvoked, file, dst);
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
        Association asInvoked = (Association) asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
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
        as.setProperty(ProxyAEExtension.FILE_SUFFIX, suffix);
        File dst = setFileSuffix(file, suffix);
        rename(as, file, dst);
    }

    private void handleProcessForwardTaskException(ForwardTask ft, Exception e, String suffix)
            throws DicomServiceException {
        LOG.error("Unable to connect to {}: {}", new Object[] { ft.getAAssociateRQ().getCalledAET(), e.getMessage() });
        for (File file : ft.getFiles())
            renameFile(suffix, file);
    }

    private void renameFile(String suffix, File file) throws DicomServiceException {
        File dst = setFileSuffix(file, suffix);
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: rename to {}", new Object[] { file, dst });
        } else {
            LOG.debug("{}: failed to rename to {}", new Object[] { file, dst });
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private File rename(Association as, File file, File dst) throws DicomServiceException {
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: rename {} to {}", new Object[] { as, file, dst });
            return dst;
        } else {
            LOG.debug("{}: failed to rename {} to {}", new Object[] { as, file, dst });
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
            int numRetries = Integer.parseInt(substring);
            return new File(path.substring(0, indexOfNumRetries) + Integer.toString(numRetries + 1));
        }
        int previousNumRetries = Integer.parseInt(path.substring(indexOfNumRetries, indexOfNextSuffix));
        String substringStart = path.substring(0, indexOfNewSuffix);
        String substringEnd = path.substring(indexOfNextSuffix);
        String pathname = substringStart + substringEnd + newSuffix + Integer.toString(previousNumRetries + 1);
        return new File(pathname);
    }

    private DataWriter createDataWriter(ProxyAEExtension pae, DicomInputStream in, Association as, Attributes[] ds,
            String cuid) throws IOException {
        AttributeCoercion ac = pae.getAttributeCoercion(as.getRemoteAET(), cuid, Role.SCP, Dimse.C_STORE_RQ);
        if (ac != null || pae.isEnableAuditLog()) {
            Attributes attrs = in.readDataset(-1, -1);
            pae.coerceAttributes(attrs, ac, as.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class));
            ds[0] = attrs;
            if (pae.isEnableAuditLog())
                pae.createStartLogFile(as.getCallingAET(), as.getCalledAET(), ds[0].getString(Tag.StudyInstanceUID));
            return new DataWriterAdapter(attrs);
        }
        return new InputStreamDataWriter(in);
    }

    private static void delete(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: delete {}", as, file);
        else
            LOG.debug("{}: failed to delete {}", as, file);
    }

}
