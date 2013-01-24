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
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map.Entry;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomOutputStream;
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
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ForwardSchedule;
import org.dcm4chee.proxy.conf.ProxyApplicationEntity;
import org.dcm4chee.proxy.conf.Schedule;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class CStore extends BasicCStoreSCP {

    public CStore(String... sopClasses) {
        super(sopClasses);
    }

    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            PDVInputStream data) throws IOException {
        if (dimse != Dimse.C_STORE_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        Object forwardAssociationProperty = asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (forwardAssociationProperty == null
                || !((ProxyApplicationEntity) asAccepted.getApplicationEntity()).getAttributeCoercions().getAll().isEmpty()
                || ((ProxyApplicationEntity) asAccepted.getApplicationEntity()).isEnableAuditLog()
                || (forwardAssociationProperty instanceof HashMap<?, ?>))
            store(asAccepted, pc, rq, data, null);
        else {
            try {
                forward(asAccepted, pc, rq, new InputStreamDataWriter(data), (Association) forwardAssociationProperty);
            } catch (Exception e) {
                LOG.debug(asAccepted + ": error forwarding C-STORE-RQ: " + e.getMessage());
                asAccepted.clearProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
                asAccepted.setProperty(ProxyApplicationEntity.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix());
                super.onDimseRQ(asAccepted, pc, dimse, rq, data);
            }
        }
    }

    @Override
    protected void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data, Attributes rsp)
            throws IOException {
        File file = createSpoolFile(as);
        MessageDigest digest = getMessageDigest(as);
        Attributes fmi = processInputStream(as, pc, rq, data, file, digest);
        try {
            Object forwardAssociationProperty = as.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
            if (forwardAssociationProperty != null && forwardAssociationProperty instanceof Association) {
                ProxyApplicationEntity pae = (ProxyApplicationEntity) as.getApplicationEntity();
                Attributes attrs = parse(as, file);
                processFile(pae, as, (Association) forwardAssociationProperty, pc, rq, rsp, file, digest, fmi, attrs);
            } 
            else
                processForwardRules(as, forwardAssociationProperty, pc, rq, rsp, file, digest, fmi);
        } catch (ConfigurationException e) {
            LOG.error(as + ": error processing C-STORE-RQ: " + e.getMessage());
            throw new DicomServiceException(Status.UnableToProcess);
        } finally {
            deleteFile(as, file);
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

    private void processForwardRules(Association as, Object forwardAssociationProperty, PresentationContext pc, Attributes rq, Attributes rsp, File file,
            MessageDigest digest, Attributes fmi) throws IOException, DicomServiceException, ConfigurationException {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) as.getApplicationEntity();
        Association asInvoked = null;
        HashMap<String, String> aets = pae.filterForwardAETs(as, rq, Dimse.C_STORE_RQ);
        if (aets.entrySet().size() == 0)
            throw new ConfigurationException("no destination");

        if (aets.entrySet().size() == 1) {
            Entry<String, String> entry = aets.entrySet().iterator().next();
            ForwardSchedule forwardSchedule = pae.getForwardSchedules().get(entry.getKey());
            if (forwardSchedule == null || forwardSchedule.getSchedule().isNow(new GregorianCalendar()))
                asInvoked = getSingleForwardDestination(as, entry.getValue(), entry.getKey(), as.getAAssociateRQ(),
                        forwardAssociationProperty, pae);
        }
        boolean writeDimseRSP = false;
        for (Entry<String, String> entry : aets.entrySet()) {
            File copy = createMappedFile(as, file, fmi, entry.getValue(), entry.getKey());
            boolean keepCopy = false;
            try {
                Attributes attrs = parse(as, copy);
                pae.coerceDataset(as.getRemoteAET(), Role.SCU, Dimse.C_STORE_RQ, attrs);
                keepCopy = processFile(pae, as, asInvoked, pc, rq, rsp, copy, digest, fmi, attrs);
            } finally {
                if (!keepCopy)
                    deleteFile(as, copy);
                writeDimseRSP = writeDimseRSP || keepCopy;
            }
        } if (writeDimseRSP) {
            rsp = Commands.mkCStoreRSP(rq, Status.Success);
            try {
                as.writeDimseRSP(pc, rsp);
            } catch (AssociationStateException e) {
                LOG.warn("{} << C-STORE-RSP failed: {}", as, e.getMessage());
            }
        }
    }

    private Association getSingleForwardDestination(Association as, String callingAET, String calledAET,
            AAssociateRQ rq, Object forwardAssociationProperty, ProxyApplicationEntity pae) {
        return (forwardAssociationProperty == null)
            ? newForwardAssociation(as, callingAET, calledAET, rq, pae, new HashMap<String, Association>(1))
            : (forwardAssociationProperty instanceof Association)
                ? (Association) forwardAssociationProperty
                : getAssociationFromHashMap(as, callingAET, calledAET, rq, forwardAssociationProperty, pae);
    }

    private Association getAssociationFromHashMap(Association as, String callingAET, String calledAET,
            AAssociateRQ rq, Object forwardAssociationProperty, ProxyApplicationEntity pae) {
        @SuppressWarnings("unchecked")
        HashMap<String, Association> fwdAssocs = (HashMap<String, Association>) forwardAssociationProperty;
        return (fwdAssocs.containsKey(calledAET))
            ? fwdAssocs.get(calledAET)
            : newForwardAssociation(as, callingAET, calledAET, rq, pae, fwdAssocs);
    }

    private Association newForwardAssociation(Association as, String callingAET, String calledAET,
            AAssociateRQ rq, ProxyApplicationEntity pae, HashMap<String, Association> fwdAssocs) {
        Association asInvoked = pae.openForwardAssociation(as, callingAET, calledAET, rq);
        fwdAssocs.put(calledAET, asInvoked);
        as.setProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION, fwdAssocs);
        return asInvoked;
    }

    private void deleteFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: M-DELETE {}", as, file);
        else
            LOG.debug("{}: failed to M-DELETE {}", as, file);
    }

    protected File createSpoolFile(Association as) throws DicomServiceException {
        try {
            ProxyApplicationEntity pae = (ProxyApplicationEntity) as.getApplicationEntity();
            return File.createTempFile("dcm", ".part", pae.getCStoreDirectoryPath());
        } catch (Exception e) {
            LOG.debug(as + ": failed to create temp file: " + e.getMessage());
            throw new DicomServiceException(Status.OutOfResources, e);
        }
    }

    protected boolean processFile(ProxyApplicationEntity pae, Association asAccepted, Association asInvoked,
            PresentationContext pc, Attributes rq, Attributes rsp, File file, MessageDigest digest, Attributes fmi, 
            Attributes attrs) throws IOException {
        if (asInvoked == null) {
            rename(asAccepted, file, rq);
            return true;
        }
        if (pae.isEnableAuditLog()) {
            pae.createStartLogFile(asInvoked, attrs);
            pae.writeLogFile(asInvoked, attrs, file.length());
        }
        try {
            forward(asAccepted, pc, rq, new DataWriterAdapter(attrs), asInvoked);
        } catch (AssociationStateException ass) {
            handleForwardException(asAccepted, pc, rq, attrs, file, RetryObject.AssociationStateException.getSuffix(), ass);
        } catch (Exception e) {
            handleForwardException(asAccepted, pc, rq, attrs, file, RetryObject.ConnectionException.getSuffix(), e);
        }
        return false;
    }

    protected File createMappedFile(Association as, File file, Attributes fmi, String callingAET, String calledAET)
            throws DicomServiceException {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) as.getApplicationEntity();
        String separator = ProxyApplicationEntity.getSeparator();
        String path = file.getPath();
        String fileName = path.substring(path.lastIndexOf(separator) + 1, path.length());
        File dir = new File(pae.getCStoreDirectoryPath(), calledAET);
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

    private File handleForwardException(Association as, PresentationContext pc, Attributes rq, Attributes attrs,
            File file, String suffix, Exception e) throws DicomServiceException, IOException {
        LOG.debug(as + ": error forwarding C-STORE-RQ: " + e.getMessage());
        as.clearProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, suffix + "1");
        rename(as, file, attrs);
        Attributes rsp = Commands.mkCStoreRSP(rq, Status.Success);
        as.writeDimseRSP(pc, rsp);
        return null;
    }

    private static void forward(final Association asAccepted, final PresentationContext pc, Attributes rq,
            DataWriter data, Association asInvoked) throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        int priority = rq.getInt(Tag.Priority, 0);
        final int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(asAccepted.isRequestor() ? asInvoked.nextMessageID() : msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    if (!asInvoked.isRequestor())
                        cmd.setInt(Tag.MessageIDBeingRespondedTo, VR.US, msgId);
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.debug(asInvoked + ": Failed to forward C-STORE RSP: " + e.getMessage());
                }
            }
        };
        asInvoked.cstore(cuid, iuid, priority, data, tsuid, rspHandler);
    }

    protected File rename(Association as, File file, Attributes attrs) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.substring(0, path.length() - 5).concat(
                (String) as.getProperty(ProxyApplicationEntity.FILE_SUFFIX)));
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
