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

package org.dcm4chee.proxy.dimse;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.emf.MultiframeExtractor;
import org.dcm4che.io.DicomEncodingOptions;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.net.Association;
import org.dcm4che.net.Commands;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.InputStreamDataWriter;
import org.dcm4che.net.PDVInputStream;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.CMoveInfoObject;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class CStore extends BasicCStoreSCP {

    private ApplicationEntityCache aeCache;
    
    public CStore(ApplicationEntityCache aeCache, String... sopClasses) {
        super(sopClasses);
        this.aeCache = aeCache;
    }

    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            PDVInputStream data) throws IOException {
        if (dimse != Dimse.C_STORE_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        ProxyAEExtension proxyAEE = asAccepted.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        Object forwardAssociationProperty = asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (forwardAssociationProperty == null
                || proxyAEE.isAcceptDataOnFailedAssociation()
                || !proxyAEE.getAttributeCoercions().getAll().isEmpty()
                || proxyAEE.isEnableAuditLog()
                || (forwardAssociationProperty instanceof HashMap<?, ?>)
                || (forwardAssociationProperty instanceof Association 
                        &&  requiresMultiFrameConversion(proxyAEE, ((Association)forwardAssociationProperty).getRemoteAET(), rq))
                || proxyAEE.isAssociationFromDestinationAET(asAccepted))
            spool(proxyAEE, asAccepted, pc, rq, data, null);
        else {
            try {
                forward(proxyAEE, asAccepted, (Association) forwardAssociationProperty, pc, rq, 
                        new InputStreamDataWriter(data), -1, null, null);
            } catch (Exception e) {
                LOG.debug(asAccepted + ": error forwarding C-STORE-RQ: " + e.getMessage());
                asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
                asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix() + "1");
                super.onDimseRQ(asAccepted, pc, dimse, rq, data);
            }
        }
    }

    protected void spool(ProxyAEExtension proxyAEE, Association asAccepted, PresentationContext pc, Attributes cmd,
            PDVInputStream data, Attributes rsp) throws IOException {
        File file = createSpoolFile(proxyAEE, asAccepted);
        MessageDigest digest = getMessageDigest(asAccepted);
        Attributes fmi = processInputStream(asAccepted, pc, cmd, data, file, digest);
        Object forwardAssociationProperty = asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        try {
            if (forwardAssociationProperty == null && proxyAEE.isAssociationFromDestinationAET(asAccepted))
                forwardAssociationProperty = getCMoveDestinationAS(proxyAEE, asAccepted, cmd);
            if (forwardAssociationProperty != null && forwardAssociationProperty instanceof Association)
                forwardObject(asAccepted, (Association) forwardAssociationProperty, pc, cmd, file, fmi);
            else
                processForwardRules(proxyAEE, asAccepted, forwardAssociationProperty, pc, cmd, rsp, file, fmi);
        } catch (Exception e) {
            if (proxyAEE.isAcceptDataOnFailedAssociation())
                try {
                    processForwardRules(proxyAEE, asAccepted, forwardAssociationProperty, pc, cmd, rsp, file, fmi);
                } catch (ConfigurationException c) {
                    LOG.error("{}: configuration error while processing C-STORE-RQ: {}", asAccepted, c);
                    throw new DicomServiceException(Status.UnableToProcess);
                }
            else {
                LOG.error("{}: error processing C-STORE-RQ: {}", asAccepted, e);
                throw new DicomServiceException(Status.UnableToProcess);
            }
        }
    }

    private static void deleteFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: delete {}", as, file);
        else
            LOG.debug("{}: failed to delete {}", as, file);
    }

    private Association getCMoveDestinationAS(ProxyAEExtension proxyAEE, Association asAccepted, Attributes cmd)
            throws DicomServiceException {
        int moveOriMsgId = cmd.getInt(Tag.MoveOriginatorMessageID, 0);
        String moveOriAET = cmd.getString(Tag.MoveOriginatorApplicationEntityTitle);
        CMoveInfoObject cmoveInfoObject = proxyAEE.getCMoveInfoObject(moveOriMsgId);
        if (cmoveInfoObject == null 
                || !cmoveInfoObject.getMoveOriginatorAET().equals(moveOriAET)
                || !cmoveInfoObject.getCalledAET().equals(asAccepted.getRemoteAET()))
            return null;

        try {
            AAssociateRQ rq = asAccepted.getAAssociateRQ();
            ForwardRule rule = cmoveInfoObject.getRule();
            String callingAET = (rule.getUseCallingAET() == null) ? asAccepted.getCallingAET() : rule.getUseCallingAET();
            Association asInvoked = proxyAEE.openForwardAssociation(asAccepted, rule, callingAET,
                    cmoveInfoObject.getMoveDestinationAET(), rq, aeCache);
            asAccepted.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asInvoked);
            asAccepted.setProperty(ProxyAEExtension.FORWARD_CMOVE_INFO, cmoveInfoObject);
            asAccepted.setProperty(ForwardRule.class.getName(), rule);
            return asInvoked;
        } catch (Exception e) {
            LOG.error("Unable to connect to {}: {}", cmoveInfoObject.getMoveDestinationAET(), e.getMessage());
            throw new DicomServiceException(Status.UnableToProcess);
        }
    }

    private Attributes processInputStream(Association as, PresentationContext pc, Attributes rq, PDVInputStream data,
            File file, MessageDigest digest) throws FileNotFoundException, IOException {
        LOG.debug("{}: write {}", as, file);
        FileOutputStream fout = new FileOutputStream(file);
        BufferedOutputStream bout = new BufferedOutputStream(digest == null ? fout : new DigestOutputStream(fout,
                digest));
        DicomOutputStream out = new DicomOutputStream(bout, UID.ExplicitVRLittleEndian);
        Attributes fmi = createFileMetaInformation(as, rq, pc.getTransferSyntax());
        out.writeFileMetaInformation(fmi);
        out.writeAttribute(0x00030016, VR.SH, as.getConnection().getHostname().getBytes());
        try {
            data.copyTo(out);
        } finally {
            SafeClose.close(out);
        }
        return fmi;
    }

    private void processForwardRules(ProxyAEExtension proxyAEE, Association asAccepted,
            Object forwardAssociationProperty, PresentationContext pc, Attributes rq, Attributes rsp, File file,
            Attributes fmi) throws IOException, DicomServiceException, ConfigurationException {
        List<ForwardRule> forwardRules = proxyAEE.filterForwardRulesOnDimseRQ(asAccepted, rq, Dimse.C_STORE_RQ);
        if (forwardRules.size() == 0)
            throw new ConfigurationException("no matching forward rule");

        if (forwardRules.size() == 1 && forwardRules.get(0).getDestinationAETitles().size() == 1)
            processSingleForwardDestination(asAccepted, forwardAssociationProperty, pc, rq, file, fmi, proxyAEE,
                    forwardRules.get(0));
        else {
            for (ForwardRule rule : forwardRules) {
                List<String> destinationAETs = proxyAEE.getDestinationAETsFromForwardRule(asAccepted, rule);
                for (String calledAET : destinationAETs) {
                    File copy;
                    if (rule.getUseCallingAET() != null)
                        copy = createMappedDicomCopy(proxyAEE, asAccepted, file, calledAET, rule.getUseCallingAET(), rq, pc);
                    else
                        copy = createMappedFileCopy(proxyAEE, asAccepted, file, calledAET);
                    rename(asAccepted, copy, ".dcm");
                }
            }
            deleteFile(asAccepted, file);
            Attributes cmd = Commands.mkCStoreRSP(rq, Status.Success);
            asAccepted.writeDimseRSP(pc, cmd);
        }
    }

    private File createMappedDicomCopy(ProxyAEExtension proxyAEE, Association asAccepted, File file, String calledAET,
            String callingAET, Attributes rq, PresentationContext pc) throws DicomServiceException {
        String separator = ProxyAEExtension.getSeparator();
        String path = file.getPath();
        String fileName = path.substring(path.lastIndexOf(separator) + 1, path.length());
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), calledAET);
        dir.mkdir();
        File dst = new File(dir, fileName);
        DicomOutputStream out = null;
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            out = new DicomOutputStream(dst);
            Attributes fmi = createFileMetaInformation(asAccepted, rq, pc.getTransferSyntax());
            fmi.setString(Tag.SourceApplicationEntityTitle, VR.AE, callingAET);
            LOG.debug(asAccepted + ": copy " + file.getPath() + " to " + dst.getPath());
            out.writeDataset(fmi, in.readDataset(-1, -1));
        } catch (Exception e) {
            LOG.debug(asAccepted + ": failed to copy " + file.getPath() + " to " + dst.getPath());
            dst.delete();
            throw new DicomServiceException(Status.OutOfResources, e);
        } finally {
            SafeClose.close(in);
            SafeClose.close(out);
        }
        return dst;
    }

    private void processSingleForwardDestination(Association asAccepted, Object forwardAssociationProperty,
            PresentationContext pc, Attributes rq, File file, Attributes fmi, ProxyAEExtension proxyAEE,
            ForwardRule rule) throws DicomServiceException, IOException {
        String calledAET = rule.getDestinationAETitles().get(0);
        ForwardOption forwardOption = proxyAEE.getForwardOptions().get(calledAET);
        if (forwardOption == null || forwardOption.getSchedule().isNow(new GregorianCalendar())) {
            String callingAET = (rule.getUseCallingAET() == null) ? asAccepted.getCallingAET() : rule.getUseCallingAET();
            Association asInvoked = getSingleForwardDestination(asAccepted, callingAET, calledAET,
                    asAccepted.getAAssociateRQ(), forwardAssociationProperty, proxyAEE, rule);
            if (asInvoked != null) {
                asAccepted.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asInvoked);
                if (requiresMultiFrameConversion(proxyAEE, asInvoked.getRemoteAET(), rq))
                    processMultiFrame(proxyAEE, asAccepted, asInvoked, pc, rq, file, fmi);
                else
                    forwardObject(asAccepted, asInvoked, pc, rq, file, fmi);
            } else {
                storeToCalledAETSpoolDir(proxyAEE, asAccepted, pc, rq, file, calledAET, rule);
            }
        } else {
            storeToCalledAETSpoolDir(proxyAEE, asAccepted, pc, rq, file, calledAET, rule);
        }
    }

    private void storeToCalledAETSpoolDir(ProxyAEExtension proxyAEE, Association asAccepted, PresentationContext pc,
            Attributes rq, File file, String calledAET, ForwardRule rule) throws IOException, DicomServiceException {
        if (rule.getUseCallingAET() != null)
            rewriteFile(proxyAEE, asAccepted, pc, rq, file, calledAET, rule);
        else
            renameFile(proxyAEE, asAccepted, pc, rq, file, calledAET);
    }

    private void rewriteFile(ProxyAEExtension proxyAEE, Association asAccepted, PresentationContext pc, Attributes rq,
            File file, String calledAET, ForwardRule rule) throws DicomServiceException, IOException {
        File copy = createMappedDicomCopy(proxyAEE, asAccepted, file, calledAET, rule.getUseCallingAET(), rq, pc);
        rename(asAccepted, copy, ".dcm");
        if (file.delete()) {
            asAccepted.writeDimseRSP(pc, Commands.mkCStoreRSP(rq, Status.Success));
        } else {
            LOG.error("{}: failed to delete {}", new Object[] { asAccepted, file });
            throw new DicomServiceException(Status.OutOfResources);
        }
    }

    private void renameFile(ProxyAEExtension proxyAEE, Association asAccepted, PresentationContext pc, Attributes rq,
            File file, String calledAET) throws IOException, DicomServiceException {
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), calledAET);
        dir.mkdir();
        String fileName = file.getName();
        File dst = new File(dir, fileName.substring(0, fileName.lastIndexOf('.')).concat(".dcm"));
        if (file.renameTo(dst)) {
            asAccepted.writeDimseRSP(pc, Commands.mkCStoreRSP(rq, Status.Success));
        } else {
            LOG.error("{}: failed to rename {} to {}", new Object[] { asAccepted, file, dst });
            throw new DicomServiceException(Status.OutOfResources);
        }
    }

    private Association getSingleForwardDestination(Association as, String callingAET, String calledAET,
            AAssociateRQ rq, Object forwardAssociationProperty, ProxyAEExtension pae, ForwardRule rule) {
        return (forwardAssociationProperty == null)
            ? newForwardAssociation(as, callingAET, calledAET, rq, pae, new HashMap<String, Association>(1), rule)
            : (forwardAssociationProperty instanceof Association)
                ? (Association) forwardAssociationProperty
                : getAssociationFromHashMap(as, callingAET, calledAET, rq, forwardAssociationProperty, pae, rule);
    }

    private Association getAssociationFromHashMap(Association as, String callingAET, String calledAET,
            AAssociateRQ rq, Object forwardAssociationProperty, ProxyAEExtension pae, ForwardRule rule) {
        @SuppressWarnings("unchecked")
        HashMap<String, Association> fwdAssocs = (HashMap<String, Association>) forwardAssociationProperty;
        return (fwdAssocs.containsKey(calledAET))
            ? fwdAssocs.get(calledAET)
            : newForwardAssociation(as, callingAET, calledAET, rq, pae, fwdAssocs, rule);
    }

    private Association newForwardAssociation(Association as, String callingAET, String calledAET, AAssociateRQ rq,
            ProxyAEExtension pae, HashMap<String, Association> fwdAssocs, ForwardRule rule) {
        rq.setCallingAET(callingAET);
        rq.setCalledAET(calledAET);
        Association asInvoked = null;
        try {
            asInvoked = as.getApplicationEntity().getAEExtension(ProxyAEExtension.class)
                    .openForwardAssociation(as, rule, callingAET, calledAET, rq, aeCache);
        } catch (GeneralSecurityException e) {
            LOG.error("Failed to create SSL context: ", e.getMessage());
        } catch (ConfigurationException e) {
            LOG.error("Unable to load configuration for destination AET: ", e.getMessage());
        } catch (Exception e) {
            LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e });;
        }
        return asInvoked;
    }

    protected File createSpoolFile(ProxyAEExtension proxyAEE, Association as) throws DicomServiceException {
        try {
            return File.createTempFile("dcm", ".part", proxyAEE.getCStoreDirectoryPath());
        } catch (Exception e) {
            LOG.debug(as + ": failed to create temp file: " + e.getMessage());
            throw new DicomServiceException(Status.OutOfResources, e);
        }
    }

    protected void forwardObject(Association asAccepted, Association asInvoked, PresentationContext pc, Attributes rq,
            File dataFile, Attributes fmi) throws IOException {
        ProxyAEExtension proxyAEE = asAccepted.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        if (requiresMultiFrameConversion(proxyAEE, asInvoked.getRemoteAET(), rq)) {
            processMultiFrame(proxyAEE, asAccepted, asInvoked, pc, rq, dataFile, fmi);
            return;
        }
        Attributes attrs = parse(asAccepted, dataFile);
        File logFile = null;
        if (proxyAEE.isEnableAuditLog()) {
            String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            proxyAEE.createStartLogFile(true, sourceAET, asInvoked.getRemoteAET(), attrs);
            logFile = proxyAEE.writeLogFile(true, sourceAET, asInvoked.getRemoteAET(), attrs, dataFile.length());
        }
        try {
            forward(proxyAEE, asAccepted, asInvoked, pc, rq, new DataWriterAdapter(attrs), -1, logFile, dataFile);
        } catch (Exception e) {
            asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
            LOG.error("{}: error forwarding object {}: {}", new Object[] { asAccepted, dataFile, e });
        }
    }

    private boolean requiresMultiFrameConversion(ProxyAEExtension proxyAEE, String destinationAET, Attributes rq) {
        String sopClass = rq.getString(Tag.AffectedSOPClassUID);
        HashMap<String, ForwardOption> fwdOptions = proxyAEE.getForwardOptions();
        ForwardOption fwdOption = fwdOptions.get(destinationAET);
        if (fwdOption != null)
            return (fwdOption.isConvertEmf2Sf() 
                        && (sopClass.equals(UID.EnhancedCTImageStorage)
                            || sopClass.equals(UID.EnhancedMRImageStorage) 
                            || sopClass.equals(UID.EnhancedPETImageStorage)));
        return false;
    }

    private void processMultiFrame(ProxyAEExtension proxyAEE, Association asAccepted, Association asInvoked,
            PresentationContext pc, Attributes rq, File dataFile, Attributes fmi) throws IOException {
        Attributes src;
        DicomInputStream dis = new DicomInputStream(dataFile);
        try {
            dis.setIncludeBulkData(IncludeBulkData.LOCATOR);
            src = dis.readDataset(-1, -1);
        } finally {
            SafeClose.close(dis);
        }
        MultiframeExtractor extractor = new MultiframeExtractor();
        int n = src.getInt(Tag.NumberOfFrames, 1);
        long t = 0;
        boolean log = true;
        for (int i = n - 1; i >= 0; --i) {
            long t1 = System.currentTimeMillis();
            Attributes attrs = extractor.extract(src, i);
            long t2 = System.currentTimeMillis();
            t = t + t2 - t1;
            rq.setString(Tag.AffectedSOPInstanceUID, VR.UI, attrs.getString(Tag.SOPInstanceUID));
            rq.setString(Tag.AffectedSOPClassUID, VR.UI, attrs.getString(Tag.SOPClassUID));
            File logFile = null;
            if (proxyAEE.isEnableAuditLog()) {
                String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
                proxyAEE.createStartLogFile(true, sourceAET, asInvoked.getRemoteAET(), attrs);
                logFile = proxyAEE.writeLogFile(true, sourceAET, asInvoked.getRemoteAET(), attrs,
                        attrs.calcLength(DicomEncodingOptions.DEFAULT, true));
            }
            try {
                forward(proxyAEE, asAccepted, asInvoked, pc, rq, new DataWriterAdapter(attrs), i, logFile, null);
            } catch (Exception e) {
                asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
                if (logFile != null)
                    logFile.delete();
                LOG.error("{}: Error forwarding single-frame from multi-frame object: {}",
                        new Object[] { asAccepted, e.getMessage() });
                log = false;
                Attributes rsp = Commands.mkCStoreRSP(rq, Status.UnableToProcess);
                asAccepted.writeDimseRSP(pc, rsp);
                break;
            }
        }
        if (log)
            LOG.info("{}: extracted {} frames from {} in {}sec",
                    new Object[] { asAccepted, n, src.getString(Tag.SOPInstanceUID), t / 1000F });
    }

    protected static File createMappedFileCopy(ProxyAEExtension proxyAEE, Association as, File file, String calledAET)
            throws IOException {
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), calledAET);
        dir.mkdir();
        File dst = new File(dir, file.getName());
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(file).getChannel();
            destination = new FileOutputStream(dst).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            SafeClose.close(source);
            SafeClose.close(destination);
        }
        return dst;
    }

    private static void forward(final ProxyAEExtension proxyAEE, final Association asAccepted, Association asInvoked,
            final PresentationContext pc, final Attributes rq, DataWriter data, final int frame,
            final File logFile, final File dataFile) throws IOException, InterruptedException {
        final String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        final String calledAET = asInvoked.getCalledAET();
        int priority = rq.getInt(Tag.Priority, 0);
        final int msgId = rq.getInt(Tag.MessageID, 0);
        final CMoveInfoObject info = (CMoveInfoObject) asAccepted.getProperty(ProxyAEExtension.FORWARD_CMOVE_INFO);
        int newMsgId = msgId;
        if (info != null || asAccepted.isRequestor())
            newMsgId = asInvoked.nextMessageID();
        DimseRSPHandler rspHandler = new DimseRSPHandler(newMsgId) {

            // onClose can be called in a separate thread, e.g. by network layer
            // need to make sure to writeDimseRSP only once
            boolean isClosed = false;

            @Override
            synchronized public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                if (!isClosed) {
                    super.onDimseRSP(asInvoked, cmd, data);
                    try {
                        if (info != null && frame > 0)
                            return;
                        if (!asInvoked.isRequestor() || info != null)
                            cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                        asAccepted.writeDimseRSP(pc, cmd, data);
                    } catch (IOException e) {
                        LOG.debug(asInvoked + ": Failed to forward C-STORE RSP : " + e.getMessage());
                    } finally {
                        if (cmd.getInt(Tag.Status, -1) == Status.Success && dataFile != null)
                            deleteFile(asAccepted, dataFile);
                    }
                }
            }

            @Override
            synchronized public void onClose(Association as) {
                isClosed = true;
                if (logFile != null)
                    logFile.delete();
                super.onClose(as);
                Attributes cmd = new Attributes();
                if (dataFile != null && proxyAEE.isAcceptDataOnFailedAssociation())
                    try {
                        File copy = createMappedFileCopy(proxyAEE, asAccepted, dataFile, calledAET);
                        rename(as, copy, RetryObject.ConnectionException.getSuffix() + "1");
                        cmd = Commands.mkCStoreRSP(rq, Status.Success);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                else
                    cmd = Commands.mkCStoreRSP(rq, Status.UnableToProcess);
                deleteFile(asAccepted, dataFile);
                if (!as.isRequestor() || info != null)
                    cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                try {
                    asAccepted.writeDimseRSP(pc, cmd);
                } catch (IOException e) {
                    LOG.debug(as + ": Failed to forward C-STORE RSP: " + e.getMessage());
                }
            }
        };

        if (info != null)
            asInvoked.cstore(cuid, iuid, priority, info.getMoveOriginatorAET(), info.getSourceMsgId(), data, tsuid,
                    rspHandler);
        else
            asInvoked.cstore(cuid, iuid, priority, data, tsuid, rspHandler);
    }

    protected static void rename(Association as, File file, String suffix) throws DicomServiceException {
        String path = file.getPath();
        if (file.exists()) {
            File dst = new File(path.substring(0, path.lastIndexOf('.')).concat(suffix));
            if (file.renameTo(dst)) {
                dst.setLastModified(System.currentTimeMillis());
                LOG.info("{}: rename {} to {}", new Object[] { as, file, dst });
            } else {
                LOG.error("{}: failed to rename {} to {}", new Object[] { as, file, dst });
                throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
            }
        } else
            LOG.error("{}: failed to rename {}: file doesn't exist", as, file.getPath());
    }
}
