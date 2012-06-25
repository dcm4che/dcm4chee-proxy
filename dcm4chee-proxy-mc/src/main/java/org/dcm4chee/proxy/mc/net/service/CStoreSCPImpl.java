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

package org.dcm4chee.proxy.mc.net.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
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
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCStoreSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.mc.net.ForwardRule;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class CStoreSCPImpl extends BasicCStoreSCP {

    public CStoreSCPImpl(String... sopClasses) {
        super(sopClasses);
    }

    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            PDVInputStream data) throws IOException {
        if (dimse != Dimse.C_STORE_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null)
            super.onDimseRQ(asAccepted, pc, dimse, rq, data);
        else if (!((ProxyApplicationEntity) asAccepted.getApplicationEntity()).getAttributeCoercions().getAll()
                .isEmpty()
                || ((ProxyApplicationEntity) asAccepted.getApplicationEntity()).isEnableAuditLog())
            store(asAccepted, pc, rq, data, null);
        else {
            try {
                forward(asAccepted, pc, rq, new InputStreamDataWriter(data), asInvoked);
            } catch (Exception e) {
                LOG.debug(asAccepted + ": error forwarding C-STORE-RQ: " + e.getMessage());
                asAccepted.clearProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
                asAccepted.setProperty(ProxyApplicationEntity.FILE_SUFFIX, ".conn");
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
        boolean keepFile = false;
        try {
            keepFile = process(as, pc, rq, rsp, file, digest, fmi);
        } catch (ConfigurationException e) {
            LOG.error(as + ": error processing C-STORE-RQ: " + e.getMessage());
        } finally {
            if (!keepFile)
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
            out.close();
        }
        return fmi;
    }

    private boolean process(Association as, PresentationContext pc, Attributes rq, Attributes rsp, File file,
            MessageDigest digest, Attributes fmi) throws IOException, DicomServiceException, ConfigurationException {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) as.getApplicationEntity();
        List<ForwardRule> forwardRules = pae.filterForwardRulesOnDimseRQ(as, rq, Dimse.C_STORE_RQ);
        HashMap<String, String> aets = pae.getAETsFromForwardRules(as, forwardRules);
        if (aets.entrySet().size() == 0)
            throw new ConfigurationException("no destination");

        boolean keepSpoolFile = false;
        for (Entry<String, String> entry : aets.entrySet()) {
            File copy = createMappedFile(as, file, pae.getSpoolDirectoryPath(), fmi, entry.getValue(), entry.getKey());
            boolean keepCopy = true;
            try {
                Attributes attrs = parse(as, copy);
                pae.coerceDataset(as.getRemoteAET(), Role.SCU, Dimse.C_STORE_RQ, attrs);
                keepCopy = processFile(pae, as, pc, rq, rsp, copy, digest, fmi, attrs);
            } finally {
                if (!keepCopy)
                    deleteFile(as, copy);
                keepSpoolFile = keepSpoolFile && keepCopy;
            }
        }
        return keepSpoolFile;
    }

    private void deleteFile(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: M-DELETE {}", as, file);
        else
            LOG.debug("{}: failed to M-DELETE {}", as, file);
    }

    protected File createSpoolFile(Association as) throws DicomServiceException {
        try {
            ProxyApplicationEntity ae = (ProxyApplicationEntity) as.getApplicationEntity();
            File dir = new File(ae.getSpoolDirectoryPath(), "spool");
            dir.mkdir();
            return File.createTempFile("dcm", ".part", dir);
        } catch (Exception e) {
            LOG.debug(as + ": failed to create temp file: " + e.getMessage());
            throw new DicomServiceException(Status.OutOfResources, e);
        }
    }

    protected boolean processFile(ProxyApplicationEntity pae, Association asAccepted, PresentationContext pc,
            Attributes rq, Attributes rsp, File file, MessageDigest digest, Attributes fmi, Attributes attrs)
            throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            rename(asAccepted, file, rq);
            return true;
        }
        pae.coerceDataset(asInvoked.getRemoteAET(), Role.SCP, Dimse.C_STORE_RQ, attrs);
        if (pae.isEnableAuditLog()) {
            pae.createStartLogFile(asInvoked, attrs);
            pae.writeLogFile(asInvoked, attrs, file.length());
        }
        try {
            forward(asAccepted, pc, rq, new DataWriterAdapter(attrs), asInvoked);
        } catch (AssociationStateException ass) {
            handleForwardException(asAccepted, pc, rq, attrs, file, ".ass", ass);
        } catch (Exception e) {
            handleForwardException(asAccepted, pc, rq, attrs, file, ".conn", e);
        }
        return false;
    }

    protected File createMappedFile(Association as, File file, File spoolDir, Attributes fmi, String callingAET,
            String calledAET) throws DicomServiceException {
        String separator = ProxyApplicationEntity.getSeparator();
        String path = file.getPath();
        String fileName = path.substring(path.lastIndexOf(separator) + 1, path.length());
        File dir = new File(spoolDir, calledAET);
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
        as.setProperty(ProxyApplicationEntity.FILE_SUFFIX, suffix);
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
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
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
