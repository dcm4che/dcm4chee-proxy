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

package org.dcm4chee.proxy.mc.net;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.AttributeCoercions;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.AttributeCoercion.DIMSE;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationStateException;
import org.dcm4che.net.DataWriter;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.InputStreamDataWriter;
import org.dcm4che.net.NoPresentationContextException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyApplicationEntity extends ApplicationEntity {

    private static final String separator = System.getProperty("file.separator");
    private static final String jbossServerDataDir = System.getProperty("jboss.server.data.dir");
    private static final String currentWorkingDir = System.getProperty("user.dir");
    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FILE_SUFFIX = ".dcm.part";

    private String useCallingAETitle;
    private String destinationAETitle;
    private Schedule forwardSchedule;
    private String spoolDirectory;
    private int forwardPriority;
    private List<Retry> retries = new ArrayList<Retry>();
    private boolean acceptDataOnFailedNegotiation;
    private boolean exclusiveUseDefinedTC;
    private boolean enableAuditLog;
    private String auditDirectory;
    private final AttributeCoercions attributeCoercions = new AttributeCoercions();

    public boolean isAcceptDataOnFailedNegotiation() {
        return acceptDataOnFailedNegotiation;
    }

    public void setAcceptDataOnFailedNegotiation(boolean acceptDataOnFailedNegotiation) {
        this.acceptDataOnFailedNegotiation = acceptDataOnFailedNegotiation;
    }

    public void setExclusiveUseDefinedTC(boolean exclusiveUseDefinedTC) {
        this.exclusiveUseDefinedTC = exclusiveUseDefinedTC;
    }

    public boolean isExclusiveUseDefinedTC() {
        return exclusiveUseDefinedTC;
    }

    public ProxyApplicationEntity(String aeTitle) {
        super(aeTitle);
    }

    public final void setSpoolDirectory(String spoolDirectoryString) {
        this.spoolDirectory = spoolDirectoryString;
    }

    public final String getSpoolDirectory() {
        return spoolDirectory;
    }
    
    public File getSpoolDirectoryPath() {
        File path = new File(spoolDirectory);
        if (!path.isAbsolute())
            path = jbossServerDataDir != null 
                ? new File(jbossServerDataDir, spoolDirectory)
                : new File(currentWorkingDir, spoolDirectory);
        path.mkdirs();
        return path;
    }

    public int getForwardPriority() {
        return forwardPriority;
    }

    public void setForwardPriority(int forwardPriority) {
        this.forwardPriority = forwardPriority;
    }

    public void setUseCallingAETitle(String useCallingAETitle) {
        this.useCallingAETitle = useCallingAETitle;
    }

    public String getUseCallingAETitle() {
        return useCallingAETitle;
    }

    public String getDestinationAETitle() {
        return destinationAETitle;
    }

    public void setDestinationAETitle(String destinationAET) {
        this.destinationAETitle = destinationAET;
    }

    public final void setForwardSchedule(Schedule schedule) {
        this.forwardSchedule = schedule;
    }

    public final Schedule getForwardSchedule() {
        return forwardSchedule;
    }

    public void setRetries(List<Retry> retries) {
        this.retries = retries;
    }

    public List<Retry> getRetries() {
        return retries;
    }

    public void setEnableAuditLog(boolean enableAuditLog) {
        this.enableAuditLog = enableAuditLog;
    }

    public boolean isEnableAuditLog() {
        return enableAuditLog;
    }

    public void setAuditDirectory(String auditDirectoryString) {
        this.auditDirectory = auditDirectoryString;
    }

    public String getAuditDirectory() {
        return auditDirectory;
    }

    public File getAuditDirectoryPath() {
        File path = new File(auditDirectory);
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, auditDirectory)
                : new File(currentWorkingDir, auditDirectory);
        path.mkdirs();
        return path;
    }

    @Override
    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac)
            throws IOException {
        final Calendar now = new GregorianCalendar();
        if ((forwardSchedule == null || forwardSchedule.sendNow(now)) && !rq.getCallingAET().equals(destinationAETitle))
            return forwardAAssociateRQ(as, rq, ac, true);
        as.setProperty(FILE_SUFFIX, ".dcm");
        rq.addRoleSelection(new RoleSelection(UID.StorageCommitmentPushModelSOPClass, true, true));
        return super.negotiate(as, rq, ac);
    }
    
    private AAssociateAC forwardAAssociateRQ(Association as, AAssociateRQ rq, AAssociateAC ac,
            boolean sendNow) throws IOException {
        try {
            if (useCallingAETitle != null)
                rq.setCallingAET(useCallingAETitle);
            else if (!getAETitle().equals("*"))
                rq.setCallingAET(getAETitle());
            rq.setCalledAET(destinationAETitle);
            Association asCalled = connect(getDestinationAE(), rq);
            if (sendNow)
                as.setProperty(FORWARD_ASSOCIATION, asCalled);
            else
                releaseAS(asCalled);
            AAssociateAC acCalled = asCalled.getAAssociateAC();
            if (exclusiveUseDefinedTC) {
                AAssociateAC acProxy = super.negotiate(as, rq, new AAssociateAC());
                for (PresentationContext pcCalled : acCalled.getPresentationContexts()) {
                    final PresentationContext pcLocal = acProxy.getPresentationContext(pcCalled.getPCID());
                    ac.addPresentationContext(pcLocal.isAccepted() ? pcCalled : pcLocal);
                }
            } else
                for (PresentationContext pc : acCalled.getPresentationContexts())
                    ac.addPresentationContext(pc);
            for (RoleSelection rs : acCalled.getRoleSelections())
                ac.addRoleSelection(rs);
            for (ExtendedNegotiation extNeg : acCalled.getExtendedNegotiations())
                ac.addExtendedNegotiation(extNeg);
            for (CommonExtendedNegotiation extNeg : acCalled.getCommonExtendedNegotiations())
                ac.addCommonExtendedNegotiation(extNeg);
            return ac;
        } catch (KeyManagementException kme) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, kme);
        } catch (ConfigurationException ce) {
            LOG.warn("Unable to load configuration for destination AET:", ce);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (AAssociateRJ rj) {
            return handleNegotiateConnectException(as, rq, ac, rj, ".rj-" + rj.getResult() + "-" 
                    + rj.getSource() + "-" + rj.getReason(), rj.getReason());
        } catch (AAbort aa) {
            return handleNegotiateConnectException(as, rq, ac, aa, ".aa-" + aa.getSource() + "-"
                    + aa.getReason(), aa.getReason());
        } catch (IOException e) {
            return handleNegotiateConnectException(as, rq, ac, e, ".conn", 0);
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception:", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException ic) {
            return handleNegotiateConnectException(as, rq, ac, ic, ".conf", 0); 
        }
    }

    private ApplicationEntity getDestinationAE() throws ConfigurationException {
        ProxyDevice device = (ProxyDevice) getDevice();
        return device.findApplicationEntity(destinationAETitle);
    }

    private AAssociateAC handleNegotiateConnectException(Association as, AAssociateRQ rq,
            AAssociateAC ac, Exception e, String suffix, int reason) throws IOException, AAbort {
        as.clearProperty(FORWARD_ASSOCIATION);
        LOG.debug("Unable to connect to " + destinationAETitle + " (" + e.getMessage() + ")");
        if (acceptDataOnFailedNegotiation) {
            as.setProperty(FILE_SUFFIX, suffix);
            return super.negotiate(as, rq, ac);
        }
        throw new AAbort(AAbort.UL_SERIVE_PROVIDER, reason);
    }

    @Override
    protected void onClose(Association asAccepted) {
        super.onClose(asAccepted);
        Association asInvoked = (Association) asAccepted.getProperty(FORWARD_ASSOCIATION);
        if (asInvoked != null)
            try {
                asInvoked.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + asInvoked, e);
            }
    }
    
    protected void releaseAS(Association asAccepted) {
        Association asInvoked = (Association) asAccepted.clearProperty(FORWARD_ASSOCIATION);
        if (asInvoked != null)
            try {
                asInvoked.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + asInvoked, e);
            }
    }

    public Attributes readAndCoerceDataset(File file, String remoteAET) throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(file);
        try {
            in.setIncludeBulkDataLocator(true);
            attrs = in.readDataset(-1, -1);
        } finally {
            SafeClose.close(in);
        }
        AttributeCoercion ac =
                getAttributeCoercion(remoteAET, attrs.getString(Tag.SOPClassUID), Role.SCU,
                        DIMSE.C_STORE_RQ);
        if (ac != null)
            ;
        coerceAttributes(attrs, ac);
        return attrs;
    }

    private void coerceAttributes(Attributes attrs, AttributeCoercion ac) {
        Attributes modify = new Attributes();
        try {
            SAXWriter w = SAXTransformer.getSAXWriter(getTemplates(ac.getURI()), modify);
            w.setIncludeKeyword(false);
            w.write(attrs);
        } catch (Exception e) {
            new IOException(e);
        }
        attrs.addAll(modify);
    }

    public void forwardFiles() {
        final Calendar now = new GregorianCalendar();
        if (forwardSchedule != null && !forwardSchedule.sendNow(now))
            return;

        for (String calledAET : getSpoolDirectoryPath().list())
            startForwardFiles(calledAET);
    }

    private void startForwardFiles(final String calledAET) {
        getDevice().execute(new Runnable() {
            
            @Override
            public void run() {
                forwardFiles(calledAET);
            }
        });
    }

    private void forwardFiles(String calledAET) {
        File dir = new File(getSpoolDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter());
        if (files != null && files.length > 0)
            for (ForwardTask ft : scanFiles(calledAET, files))
                try {
                    process(ft);
                } catch (DicomServiceException e) {
                    e.printStackTrace();
                }
    }

    private FileFilter fileFilter() {
        final long now = System.currentTimeMillis();
        return new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                String path = pathname.getPath();
                if (path.endsWith(".dcm"))
                    return true;
                String file = path.substring(path.lastIndexOf(separator) + 1);
                for (Retry retry : retries)
                    if (path.endsWith(retry.suffix) && numRetry(retry, file) 
                            && (now > pathname.lastModified() + retryDelay(retry, file)))
                        return true;
                return false;
            }

            private double retryDelay(Retry retry, String file) {
                int power = file.split("\\.").length - 2;
                return retry.delay * 1000 * Math.pow(2, power);
            }
            
            private boolean numRetry(Retry retry, String file) {
                return file.split(retry.suffix, -1).length -1 < (Integer) retry.numretry;
            }
        };
    }

    private void process(ForwardTask ft) throws DicomServiceException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        if (getUseCallingAETitle() != null)
            rq.setCallingAET(getUseCallingAETitle());
        if (!destinationAETitle.equals("*"))
            rq.setCalledAET(getDestinationAETitle());
        Association asInvoked = null;
        try {
            asInvoked = connect(getDestinationAE(), rq);
            for (File file : ft.getFiles()) {
                try {
                    if (asInvoked.isReadyForDataTransfer()){
                        forward(asInvoked, file);
                    }
                    else {
                        asInvoked.setProperty(FILE_SUFFIX, ".conn");
                        rename(asInvoked, file);
                    }
                } catch (NoPresentationContextException npc) {
                    handleForwardException(asInvoked, file, npc, ".npc");
                } catch (AssociationStateException ass) {
                    handleForwardException(asInvoked, file, ass, ".ass");
                } catch (IOException ioe){
                    handleForwardException(asInvoked, file, ioe, ".conn");
                    releaseAS(asInvoked);
                }
            }
        } catch (KeyManagementException kme) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, kme);
        } catch (ConfigurationException ce) {
            LOG.warn(asInvoked + ": unable to load configuration: ", ce.getMessage());
        } catch (AAssociateRJ rj) {
            handleProcessException(ft, rj, ".rj-" + rj.getResult() + "-" 
                    + rj.getSource() + "-" + rj.getReason());
        } catch (AAbort aa) {
            handleProcessException(ft, aa, ".aa-" + aa.getSource() + "-"
                    + aa.getReason());
        } catch (IOException e) {
            handleProcessException(ft, e, ".conn");
        } catch (InterruptedException e) {
            LOG.warn(asInvoked + ": connection exception: " + e.getMessage());
        } catch (IncompatibleConnectionException e) {
            LOG.warn(asInvoked + ": incompatible connection: " + e.getMessage());
        } finally {
            if (asInvoked != null && asInvoked.isReadyForDataTransfer()) {
                try {
                    asInvoked.waitForOutstandingRSP();
                    asInvoked.release();
                } catch (InterruptedException e) {
                    LOG.warn(asInvoked + ": Unexpected exception:", e);
                } catch (IOException e) {
                    LOG.warn(asInvoked + ": Failed to release association:", e);
                }
            }
        }
    }

    private void handleForwardException(Association as, File file, Exception e, String suffix)
            throws DicomServiceException {
        LOG.debug(as + ": " + e.getMessage());
        as.setProperty(FILE_SUFFIX, suffix);
        rename(as, file);
    }

    private void handleProcessException(ForwardTask ft, Exception e, String suffix) 
    throws DicomServiceException {
        LOG.debug(destinationAETitle + " connection error: " + e.getMessage());
        for (File file : ft.getFiles()) {
            String path = file.getPath();
            File dst = new File(path.concat(suffix));
            if (file.renameTo(dst)) {
                dst.setLastModified(System.currentTimeMillis());
                LOG.debug("{}: M-RENAME to {}", new Object[] { file, dst });
            }
            else {
                LOG.warn("{}: Failed to M-RENAME to {}", new Object[] { file, dst });
                throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
            }
        }
    }

    private void forward(final Association asInvoked, final File file) throws IOException,
    InterruptedException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            Attributes fmi = in.readFileMetaInformation();
            final String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            final String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
            final String tsuid = fmi.getString(Tag.TransferSyntaxUID);
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
                        try {
                            writeLogFile(asInvoked, ds[0], fileSize);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        delete(asInvoked, file);
                        break;
                    default: {
                        LOG.warn("{}: Failed to forward file {} with error status {}",
                                new Object[] { asInvoked, file, Integer.toHexString(status) + 'H' });
                        asInvoked.setProperty(FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                        try {
                            rename(asInvoked, file);
                        } catch (DicomServiceException e) {
                            e.printStackTrace();
                        }
                    }
                    }
                }
            };
            asInvoked.cstore(cuid, iuid, forwardPriority, 
                    createDataWriter(in, asInvoked, ds, cuid), tsuid, rspHandler);
        } finally {
            SafeClose.close(in);
        }

    }

    public void createStartLogFile(final Association as, final Attributes attrs)
            throws IOException {
        File file = new File(getLogDir(as, attrs), "start.log");
        if (!file.exists()) {
            Properties prop = new Properties();
            prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
            prop.store(new FileOutputStream(file), null);
        }
    }

    private void rename(Association as, File file) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: M-RENAME {} to {}", new Object[] {as, file, dst});
        }
        else {
            LOG.warn("{}: Failed to M-RENAME {} to {}", new Object[] {as, file, dst});
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private DataWriter createDataWriter(DicomInputStream in, Association as, Attributes[] ds,
            String cuid) throws IOException {
        AttributeCoercion ac =
                getAttributeCoercion(as.getRemoteAET(), cuid, Role.SCU, DIMSE.C_STORE_RQ);
        if (ac != null || enableAuditLog) {
            in.setIncludeBulkDataLocator(true);
            Attributes attrs = in.readDataset(-1, -1);
            coerceAttributes(attrs, ac);
            ds[0] = attrs;
            if (isEnableAuditLog())
                createStartLogFile(as, ds[0]);
            return new DataWriterAdapter(attrs);
        }
        return new InputStreamDataWriter(in);
    }

    private static void delete(Association as, File file) {
        if (file.delete())
            LOG.debug("{}: M-DELETE {}", as, file);
        else
            LOG.warn("{}: Failed to M-DELETE {}", as, file);
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
            LOG.warn("Failed to read " + file, e);
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
    
    public void writeLogFile(Association as, Attributes attrs, long size)
    throws IOException {
        Properties prop = new Properties();
        try {
            File file = new File(getLogDir(as, attrs), 
                    attrs.getString(Tag.SOPInstanceUID).concat(".log"));
            prop.setProperty("SOPClassUID", attrs.getString(Tag.SOPClassUID));
            prop.setProperty("size", String.valueOf(size));
            prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
            prop.store(new FileOutputStream(file), null);
        } catch (Exception e) {
            LOG.warn(as + ": Failed to create log file:", e);
            throw new IOException(e);
        }
    }

    private File getLogDir(Association as, Attributes attrs) {
        File logDir = new File(getAuditDirectoryPath().getPath() + separator + as.getCalledAET()
                + separator + as.getCallingAET() + separator + attrs.getString(Tag.StudyInstanceUID));
        if (!logDir.exists())
            logDir.mkdirs();
        return logDir;
    }

    public AttributeCoercion addAttributeCoercion(AttributeCoercion ac) {
        return attributeCoercions.add(ac);
    }

    public AttributeCoercion getAttributeCoercion(String aeTitle, String sopClass,
            Role role, AttributeCoercion.DIMSE cmd) {
        return attributeCoercions.get(sopClass, cmd, role, aeTitle);
    }

    public AttributeCoercions getAttributeCoercions() {
        return attributeCoercions;
    }
    
    private ProxyDevice getProxyDevice() {
        return (ProxyDevice) this.getDevice();
    }
    
    private Templates getTemplates(String uri) throws TransformerConfigurationException {
        return getProxyDevice().getTemplates(uri);
    }
}
