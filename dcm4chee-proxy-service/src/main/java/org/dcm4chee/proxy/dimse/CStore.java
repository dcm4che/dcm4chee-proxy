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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import javax.xml.transform.TransformerFactoryConfigurationError;

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
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationStateException;
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
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ForwardSchedule;
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

        Object forwardAssociationProperty = asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        ForwardRule fwdRule = (ForwardRule) asAccepted.getProperty(ForwardRule.class.getName());
        ApplicationEntity ae = asAccepted.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        if (forwardAssociationProperty == null
                || !proxyAEE.getAttributeCoercions().getAll().isEmpty()
                || proxyAEE.isEnableAuditLog()
                || (forwardAssociationProperty instanceof HashMap<?, ?>)
                || (requiresMultiFrameConversion(asAccepted, fwdRule, rq))
                || proxyAEE.isAssociationFromDestinationAET(asAccepted))
            store(asAccepted, pc, rq, data, null);
        else {
            try {
                forward(asAccepted, pc, rq, new InputStreamDataWriter(data), (Association) forwardAssociationProperty, -1);
            } catch (Exception e) {
                LOG.debug(asAccepted + ": error forwarding C-STORE-RQ: " + e.getMessage());
                asAccepted.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
                asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix());
                super.onDimseRQ(asAccepted, pc, dimse, rq, data);
            }
        }
    }

    @Override
    protected void store(Association as, PresentationContext pc, Attributes cmd, PDVInputStream data, Attributes rsp)
            throws IOException {
        File file = createSpoolFile(as);
        MessageDigest digest = getMessageDigest(as);
        Attributes fmi = processInputStream(as, pc, cmd, data, file, digest);
        ProxyAEExtension proxyAEE = as.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        try {
            Object forwardAssociationProperty = as.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
            if (proxyAEE.isAssociationFromDestinationAET(as) && forwardAssociationProperty == null)
                forwardAssociationProperty = getCMoveDestinationAS(as, cmd, proxyAEE);
            if (forwardAssociationProperty != null && forwardAssociationProperty instanceof Association)
                processFile(as, (Association) forwardAssociationProperty, pc, cmd, file, fmi);
            else
                processForwardRules(as, forwardAssociationProperty, pc, cmd, rsp, file, fmi);
        } catch (Exception e) {
            LOG.error(as + ": error processing C-STORE-RQ: ", e);
            throw new DicomServiceException(Status.UnableToProcess);
        } finally {
            deleteFile(as, file);
        }
    }

    private Association getCMoveDestinationAS(Association as, Attributes cmd, ProxyAEExtension proxyAEE)
            throws DicomServiceException {
        int moveOriMsgId = cmd.getInt(Tag.MoveOriginatorMessageID, 0);
        String moveOriAET = cmd.getString(Tag.MoveOriginatorApplicationEntityTitle);
        CMoveInfoObject info = proxyAEE.getCMoveInfoObject(moveOriMsgId);
        Association asInvoked = null;
        if (info == null || !info.getMoveOriginatorAET().equals(moveOriAET)
                || !info.getCalledAET().equals(as.getRemoteAET()))
            return asInvoked;

        AAssociateRQ rq = as.getAAssociateRQ();
        rq.setCalledAET(info.getMoveDestinationAET());
        if (info.getRule().getConversion() != null)
            updatePresentationContext(cmd, rq);
        try {
            asInvoked = as.getApplicationEntity().connect(
                    aeCache.findApplicationEntity(info.getMoveDestinationAET()), rq);
            as.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, asInvoked);
            as.setProperty(ProxyAEExtension.FORWARD_CMOVE_INFO, info);
            as.setProperty(ForwardRule.class.getName(), info.getRule());
            asInvoked.setProperty(ProxyAEExtension.FORWARD_ASSOCIATION, as);
        } catch (Exception e) {
            LOG.error("Unable to connect to {}: {}", info.getMoveDestinationAET(), e.getMessage());
            throw new DicomServiceException(Status.UnableToProcess);
        }
        return asInvoked;
    }

    private void updatePresentationContext(Attributes cmd, AAssociateRQ rq) {
        String sopClass = cmd.getString(Tag.AffectedSOPClassUID);
        if (sopClass.equals(UID.EnhancedCTImageStorage))
            replacePresentationContext(rq, sopClass, UID.CTImageStorage);
        else if (sopClass.equals(UID.EnhancedMRImageStorage))
            replacePresentationContext(rq, sopClass, UID.MRImageStorage);
        else if (sopClass.equals(UID.EnhancedPETImageStorage))
            replacePresentationContext(rq, sopClass, UID.PositronEmissionTomographyImageStorage);
    }

    private void replacePresentationContext(AAssociateRQ rq, String prevAS, String newAS) {
        List<PresentationContext> newPcList = new ArrayList<PresentationContext>();
        for (PresentationContext pc : rq.getPresentationContexts())
            if (pc.getAbstractSyntax().equals(prevAS)) {
                PresentationContext newPC = new PresentationContext(pc.getPCID(), newAS, pc.getTransferSyntaxes());
                newPcList.add(newPC);
            }
        for (PresentationContext pc : newPcList) {
            rq.removePresentationContext(rq.getPresentationContext(pc.getPCID()));
            rq.addPresentationContext(pc);
        }
    }

    private Attributes processInputStream(Association as, PresentationContext pc, Attributes rq, PDVInputStream data,
            File file, MessageDigest digest) throws FileNotFoundException, IOException {
        LOG.debug("{}: M-WRITE {}", as, file);
        FileOutputStream fout = new FileOutputStream(file);
        BufferedOutputStream bout = new BufferedOutputStream(digest == null ? fout : new DigestOutputStream(fout,
                digest));
        DicomOutputStream out = new DicomOutputStream(bout, UID.ExplicitVRLittleEndian);
        Attributes fmi = createFileMetaInformation(as, rq, pc.getTransferSyntax());
        out.writeFileMetaInformation(fmi);
        try {
            data.copyTo(out);
        } finally {
            SafeClose.close(out);
        }
        return fmi;
    }

    private void processForwardRules(Association as, Object forwardAssociationProperty, PresentationContext pc,
            Attributes rq, Attributes rsp, File file, Attributes fmi) throws IOException, DicomServiceException,
            ConfigurationException {
        ApplicationEntity ae = as.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        Association asInvoked = null;
        List<ForwardRule> forwardRules = proxyAEE.filterForwardRulesOnDimseRQ(as, rq, Dimse.C_STORE_RQ);
        if (forwardRules.size() == 0)
            throw new ConfigurationException("no matching forward rule");

        if (forwardRules.size() == 1 && forwardRules.get(0).getDestinationAETitles().size() == 1)
            asInvoked = setSingleForwardDestination(as, forwardAssociationProperty, proxyAEE, asInvoked,
                    forwardRules.get(0));
        boolean writeDimseRSP = false;
        for (ForwardRule rule : forwardRules)
            writeDimseRSP = processForwardRule(as, pc, rq, file, fmi, proxyAEE, asInvoked, writeDimseRSP, rule);
        if (writeDimseRSP) {
            rsp = Commands.mkCStoreRSP(rq, Status.Success);
            try {
                as.writeDimseRSP(pc, rsp);
            } catch (AssociationStateException e) {
                LOG.warn("{} << C-STORE-RSP failed: {}", as, e.getMessage());
            }
        }
    }

    private boolean processForwardRule(Association as, PresentationContext pc, Attributes rq, File file,
            Attributes fmi, ProxyAEExtension pae, Association asInvoked, boolean writeDimseRSP, ForwardRule rule)
            throws TransformerFactoryConfigurationError, DicomServiceException, IOException {
        String callingAET = (rule.getUseCallingAET() == null) ? as.getCallingAET() : rule.getUseCallingAET();
        List<String> destinationAETs = pae.getDestinationAETsFromForwardRule(as, rule);
        for (String calledAET : destinationAETs) {
            File copy = createMappedFile(as, file, fmi, callingAET, calledAET);
            boolean keepCopy = false;
            try {
                keepCopy = processFile(as, asInvoked, pc, rq, copy, fmi);
            } finally {
                if (!keepCopy)
                    deleteFile(as, copy);
                writeDimseRSP = writeDimseRSP || keepCopy;
            }
        }
        return writeDimseRSP;
    }

    private Association setSingleForwardDestination(Association as, Object forwardAssociationProperty,
            ProxyAEExtension pae, Association asInvoked, ForwardRule rule) {
        String calledAET = rule.getDestinationAETitles().get(0);
        ForwardSchedule forwardSchedule = pae.getForwardSchedules().get(calledAET);
        if (forwardSchedule == null || forwardSchedule.getSchedule().isNow(new GregorianCalendar())) {
            String callingAET = (rule.getUseCallingAET() == null) ? as.getCallingAET() : rule.getUseCallingAET();
            asInvoked = getSingleForwardDestination(as, callingAET, calledAET, as.getAAssociateRQ(),
                    forwardAssociationProperty, pae, rule);
        }
        return asInvoked;
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
        return as.getApplicationEntity().getAEExtension(ProxyAEExtension.class)
                .openForwardAssociation(as, rule, callingAET, calledAET, rq, aeCache);
    }

    private void deleteFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: M-DELETE {}", as, file);
        else
            LOG.debug("{}: failed to M-DELETE {}", as, file);
    }

    protected File createSpoolFile(Association as) throws DicomServiceException {
        try {
            ApplicationEntity ae = as.getApplicationEntity();
            ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
            return File.createTempFile("dcm", ".part", proxyAEE.getCStoreDirectoryPath());
        } catch (Exception e) {
            LOG.debug(as + ": failed to create temp file: " + e.getMessage());
            throw new DicomServiceException(Status.OutOfResources, e);
        }
    }

    protected boolean processFile(Association asAccepted, Association asInvoked, PresentationContext pc, Attributes rq,
            File file, Attributes fmi) throws IOException {
        ApplicationEntity ae = asAccepted.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        ForwardRule rule = (ForwardRule) asAccepted.getProperty(ForwardRule.class.getName());
        if (requiresMultiFrameConversion(asAccepted, rule, rq)) {
            processMultiFrame(proxyAEE, asAccepted, asInvoked, pc, rq, file, fmi);
            return false;
        }
        if (asInvoked == null) {
            rename(asAccepted, file);
            return true;
        }
        Attributes attrs = parse(asAccepted, file);
        if (proxyAEE.isEnableAuditLog()) {
            proxyAEE.createStartLogFile(asAccepted.getRemoteAET(), asInvoked.getRemoteAET(), attrs.getString(Tag.StudyInstanceUID));
            proxyAEE.writeLogFile(asAccepted.getRemoteAET(), asInvoked.getRemoteAET(), attrs, file.length());
        }
        try {
            forward(asAccepted, pc, rq, new DataWriterAdapter(attrs), asInvoked, -1);
        } catch (AssociationStateException ass) {
            handleForwardException(asAccepted, pc, rq, attrs, RetryObject.AssociationStateException.getSuffix(), ass, file);
        } catch (Exception e) {
            handleForwardException(asAccepted, pc, rq, attrs, RetryObject.ConnectionException.getSuffix(), e, file);
        }
        return false;
    }

    private boolean requiresMultiFrameConversion(Association asAccepted, ForwardRule rule, Attributes rq) {
        String sopClass = rq.getString(Tag.AffectedSOPClassUID);
        return rule != null
                && rule.getConversion() == ForwardRule.conversionType.Emf2Sf
                && (sopClass.equals(UID.EnhancedCTImageStorage) 
                        || sopClass.equals(UID.EnhancedMRImageStorage) 
                        || sopClass.equals(UID.EnhancedPETImageStorage))
                && (asAccepted.isRequestor() 
                        || asAccepted.getProperty(ProxyAEExtension.FORWARD_CMOVE_INFO) != null);
    }

    private void processMultiFrame(ProxyAEExtension pae, Association asAccepted, Association asInvoked,
            PresentationContext pc, Attributes rq, File file, Attributes fmi) throws IOException {
        Attributes src;
        DicomInputStream dis = new DicomInputStream(file);
        try {
            dis.setIncludeBulkData(IncludeBulkData.LOCATOR);
            src = dis.readDataset(-1, -1);
        } finally {
            SafeClose.close(dis);
        }
        MultiframeExtractor extractor = new MultiframeExtractor();
        int n = src.getInt(Tag.NumberOfFrames, 1);
        long t = 0;
        for (int i = n - 1; i >= 0; --i) {
            long t1 = System.currentTimeMillis();
            Attributes attrs = extractor.extract(src, i);
            long t2 = System.currentTimeMillis();
            t = t + t2 - t1;
            rq.setString(Tag.AffectedSOPInstanceUID, VR.UI, attrs.getString(Tag.SOPInstanceUID));
            rq.setString(Tag.AffectedSOPClassUID, VR.UI, attrs.getString(Tag.SOPClassUID));
            if (pae.isEnableAuditLog()) {
                pae.createStartLogFile(asAccepted.getRemoteAET(), asInvoked.getRemoteAET(),
                        attrs.getString(Tag.StudyInstanceUID));
                pae.writeLogFile(asAccepted.getRemoteAET(), asInvoked.getRemoteAET(), attrs,
                        attrs.calcLength(DicomEncodingOptions.DEFAULT, true));
            }
            try {
                forward(asAccepted, pc, rq, new DataWriterAdapter(attrs), asInvoked, i);
            } catch (InterruptedException e) {
                LOG.error("{}: Error forwarding to {} : {}",
                        new Object[] { asAccepted, asInvoked.getRemoteAET(), e.getMessage() });
                throw new DicomServiceException(Status.UnableToProcess);
            }
        }
        LOG.info("{}: extracted {} frames from {} in {}sec",
                new Object[] { asAccepted, n, src.getString(Tag.SOPInstanceUID), t / 1000F });
    }

    protected File createMappedFile(Association as, File file, Attributes fmi, String callingAET, String calledAET)
            throws DicomServiceException {
        ApplicationEntity ae = as.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
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
            fmi.setString(Tag.SourceApplicationEntityTitle, VR.AE, callingAET);
            LOG.debug(as + ": M-COPY " + file.getPath() + " to " + dst.getPath());
            out.writeDataset(fmi, in.readDataset(-1, -1));
        } catch (Exception e) {
            LOG.debug(as + ": failed to M-COPY " + file.getPath() + " to " + dst.getPath());
            dst.delete();
            throw new DicomServiceException(Status.OutOfResources, e);
        } finally {
            SafeClose.close(in);
            SafeClose.close(out);
        }
        return dst;
    }

    private void handleForwardException(Association as, PresentationContext pc, Attributes rq, Attributes attrs,
            String suffix, Exception e, File file) throws DicomServiceException, IOException {
        LOG.debug(as + ": error forwarding C-STORE-RQ: " + e.getMessage());
        as.clearProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        as.setProperty(ProxyAEExtension.FILE_SUFFIX, suffix + "1");
        rename(as, file);
        Attributes rsp = Commands.mkCStoreRSP(rq, Status.Success);
        as.writeDimseRSP(pc, rsp);
    }

    private static void forward(final Association asAccepted, final PresentationContext pc, Attributes rq,
            DataWriter data, Association asInvoked, final int frame) throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        int priority = rq.getInt(Tag.Priority, 0);
        final int msgId = rq.getInt(Tag.MessageID, 0);
        final CMoveInfoObject info = (CMoveInfoObject) asAccepted.getProperty(ProxyAEExtension.FORWARD_CMOVE_INFO);
        int newMsgId = msgId;
        if (info != null || asAccepted.isRequestor())
                newMsgId = asInvoked.nextMessageID();
        DimseRSPHandler rspHandler = new DimseRSPHandler(newMsgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    if (info != null && frame > 0)
                        return;
                    if (!asInvoked.isRequestor() || info != null)
                        cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.debug(asInvoked + ": Failed to forward C-STORE RSP : " + e.getMessage());
                }
            }
        };
        if (info != null)
            asInvoked.cstore(cuid, iuid, priority, info.getMoveOriginatorAET(), info.getSourceMsgId(), data, tsuid,
                    rspHandler);
        else
            asInvoked.cstore(cuid, iuid, priority, data, tsuid, rspHandler);
    }

    protected File rename(Association as, File file) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.substring(0, path.length() - 5).concat(
                (String) as.getProperty(ProxyAEExtension.FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: RENAME {} to {}", new Object[] { as, file, dst });
            return dst;
        } else {
            LOG.debug("{}: failed to RENAME {} to {}", new Object[] { as, file, dst });
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }
}
