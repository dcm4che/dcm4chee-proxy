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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
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

    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FILE_SUFFIX = ".dcm.part";

    private static SAXTransformerFactory saxTransformerFactory =
            (SAXTransformerFactory) TransformerFactory.newInstance();

    private String useCallingAETitle;
    private ApplicationEntity destination;
    private Templates attributeCoercion;
    private Schedule schedule;
    private File spoolDirectory;
    private String attributeCoercionURI;
    private int forwardPriority;
    private List<Retry> retries = new ArrayList<Retry>();
    private boolean acceptDataOnFailedNegotiation;

    public boolean isAcceptDataOnFailedNegotiation() {
        return acceptDataOnFailedNegotiation;
    }

    public void setAcceptDataOnFailedNegotiation(boolean acceptDataOnFailedNegotiation) {
        this.acceptDataOnFailedNegotiation = acceptDataOnFailedNegotiation;
    }

    public ProxyApplicationEntity(String aeTitle) {
        super(aeTitle);
    }

    public final void setSpoolDirectory(File spoolDirectory) {
        spoolDirectory.mkdirs();
        this.spoolDirectory = spoolDirectory;
    }

    public final File getSpoolDirectory() {
        return spoolDirectory;
    }

    public void setUseCallingAETitle(String useCallingAETitle) {
        this.useCallingAETitle = useCallingAETitle;
    }

    public String getUseCallingAETitle() {
        return useCallingAETitle;
    }

    public final void setForwardDestination(ApplicationEntity destination) {
        this.destination = destination;
    }

    public final ApplicationEntity getForwardDestination() {
        return destination;
    }

    public void setAttributeCoercionURI(String uri) throws TransformerConfigurationException {
        this.attributeCoercion = uri != null
                ? saxTransformerFactory.newTemplates(new StreamSource(uri))
                : null;
        this.attributeCoercionURI = uri;
    }

    public final String getAttributeCoercionURI() {
        return attributeCoercionURI;
    }

    public final Templates getAttributeCoercion() {
        return attributeCoercion;
    }

    public final boolean isCoerceAttributes() {
        return attributeCoercion != null;
    }

    public final void setForwardSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public final Schedule getForwardSchedule() {
        return schedule;
    }

    public void setRetries(List<Retry> retries) {
        this.retries = retries;
    }

    public List<Retry> getRetries() {
        return retries;
    }

    @Override
    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac)
            throws IOException {
        final Calendar now = new GregorianCalendar();
        if (schedule == null || schedule.sendNow(now))
            return forwardAAssociateRQ(as, rq, ac, true);
        as.setProperty(FILE_SUFFIX, ".dcm");
        return super.negotiate(as, rq, ac);
    }
    
    private AAssociateAC forwardAAssociateRQ(Association as, AAssociateRQ rq, AAssociateAC ac,
            boolean sendNow) throws IOException {
        try {
            if (useCallingAETitle != null)
                rq.setCallingAET(useCallingAETitle);
            if (!destination.getAETitle().equals("*"))
                rq.setCalledAET(destination.getAETitle());
            Association as2;
            as2 = connect(destination, rq);
            if (sendNow)
                as.setProperty(FORWARD_ASSOCIATION, as2);
            else
                releaseAS(as2);
            AAssociateAC ac2 = as2.getAAssociateAC();
            for (PresentationContext pc : ac2.getPresentationContexts())
                ac.addPresentationContext(pc);
            for (RoleSelection rs : ac2.getRoleSelections())
                ac.addRoleSelection(rs);
            for (ExtendedNegotiation extNeg : ac2.getExtendedNegotiations())
                ac.addExtendedNegotiation(extNeg);
            for (CommonExtendedNegotiation extNeg : ac2.getCommonExtendedNegotiations())
                ac.addCommonExtendedNegotiation(extNeg);
            return ac;
        } catch (AAssociateRJ rj) {
            return handleNegotiateConnectException(as, rq, ac, rj, ".rj-" + rj.getResult() + "-" 
                    + rj.getSource() + "-" + rj.getReason());
        } catch (AAbort aa) {
            return handleNegotiateConnectException(as, rq, ac, aa, ".aa-" + aa.getSource() + "-"
                    + aa.getReason());
        } catch (IOException e) {
            return handleNegotiateConnectException(as, rq, ac, e, ".conn");
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception:", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException ic) {
            return handleNegotiateConnectException(as, rq, ac, ic, ".conf"); 
        }
    }

    private AAssociateAC handleNegotiateConnectException(Association as, AAssociateRQ rq, AAssociateAC ac,
            Exception e, String suffix) throws IOException, AAbort {
        LOG.warn("Unable to connect to " + destination.getAETitle() + " (" + e.getMessage() + ")");
        if (acceptDataOnFailedNegotiation) {
            as.setProperty(FILE_SUFFIX, suffix);
            return super.negotiate(as, rq, ac);
        }
        throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
    }

    @Override
    protected void onClose(Association as) {
        super.onClose(as);
        Association as2 = (Association) as.getProperty(FORWARD_ASSOCIATION);
        if (as2 != null)
            try {
                as2.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + as2, e);
            }
    }
    
    protected void releaseAS(Association as) {
        Association as2 = (Association) as.clearProperty(FORWARD_ASSOCIATION);
        if (as2 != null)
            try {
                as2.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + as2, e);
            }
    }

    public Attributes readAndCoerceDataset(File file) throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(file);
        try {
            in.setIncludeBulkDataLocator(true);
            attrs = in.readDataset(-1, -1);
        } finally {
            SafeClose.close(in);
        }
        
        coerceAttributes(attrs);
        return attrs;
    }

    private void coerceAttributes(Attributes attrs) {
        Attributes modify = new Attributes();
        try {
            SAXWriter w = SAXTransformer.getSAXWriter(attributeCoercion, modify);
            w.setIncludeKeyword(false);
            w.write(attrs);
        } catch (Exception e) {
            new IOException(e);
        }
        attrs.addAll(modify);
    }

    public void forwardFiles() {
        final Calendar now = new GregorianCalendar();
        if (schedule != null && !schedule.sendNow(now))
            return;

        String aet = getAETitle();
        if (aet.equals("*")) {
            for (String calledAET : spoolDirectory.list())
                startForwardFiles(calledAET);
        } else {
            startForwardFiles(aet);
        }
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
        File dir = new File(spoolDirectory, calledAET);
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
                for (Retry retry : retries)
                    if (path.endsWith(retry.suffix)
                            && (now > pathname.lastModified() + retryDelay(retry, path)))
                        return true;
                return false;
            }

            private double retryDelay(Retry retry, String path) {
                String file = path.substring(path.lastIndexOf(System.getProperty("file.separator")) + 1);
                int power = file.split("\\.").length - 2;
                return retry.delay * 1000 * Math.pow(2, power);
            }
        };
    }

    private void process(ForwardTask ft) throws DicomServiceException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        if (useCallingAETitle != null)
            rq.setCallingAET(useCallingAETitle);
        if (!destination.getAETitle().equals("*"))
            rq.setCalledAET(destination.getAETitle());
        Association as2 = null;
        try {
            as2 = connect(destination, rq);
            for (File file : ft.getFiles()) {
                if (!as2.isReadyForDataTransfer())
                    break;
                forward(as2, file);
            }
        } catch (AAssociateRJ rj) {
            handleNegotiateConnectException(ft, rj, ".rj-" + rj.getResult() + "-" 
                    + rj.getSource() + "-" + rj.getReason());
        } catch (AAbort aa) {
            handleNegotiateConnectException(ft, aa, ".aa-" + aa.getSource() + "-"
                    + aa.getReason());
        } catch (IOException e) {
            handleNegotiateConnectException(ft, e, ".conn");
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception:", e);
        } catch (IncompatibleConnectionException e) {
            LOG.warn(e.getMessage());
        } finally {
            if (as2 != null && as2.isReadyForDataTransfer()) {
                try {
                    as2.waitForOutstandingRSP();
                    as2.release();
                } catch (InterruptedException e) {
                    LOG.warn(as2 + ": Unexpected exception:", e);
                } catch (IOException e) {
                    LOG.warn(as2 + ": Failed to release association:", e);
                }
            }
        }
    }

    private void handleNegotiateConnectException(ForwardTask ft, Exception e, String suffix) 
    throws DicomServiceException {
        LOG.warn("Unable to connect to " + destination.getAETitle() + " (" + e.getMessage() + ")");
        for (File file : ft.getFiles()) {
            String path = file.getPath();
            File dst = new File(path.concat(suffix));
            if (file.renameTo(dst)) {
                dst.setLastModified(System.currentTimeMillis());
                LOG.info("{}: M-RENAME to {}", new Object[] { file, dst });
            }
            else {
                LOG.warn("{}: Failed to M-RENAME to {}", new Object[] { file, dst });
                throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
            }
        }
    }

    private void forward(final Association as2, final File file) throws DicomServiceException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            Attributes fmi = in.readFileMetaInformation();
            String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
            String tsuid = fmi.getString(Tag.TransferSyntaxUID);
            DimseRSPHandler rspHandler = new DimseRSPHandler(as2.nextMessageID()) {
                
                @Override
                public void onDimseRSP(Association as2, Attributes cmd, Attributes data) {
                    super.onDimseRSP(as2, cmd, data);
                    int status = cmd.getInt(Tag.Status, -1);
                    switch (status) {
                    case Status.Success:
                    case Status.CoercionOfDataElements:
                        delete(as2, file);
                        break;
                    default: {
                        LOG.warn("{}: Failed to forward file {} with error status {}", 
                                new Object[]{ as2, file, Integer.toHexString(status) + 'H' });
                        as2.setProperty(FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
                        try {
                            rename(as2, file);
                        } catch (DicomServiceException e) {
                            e.printStackTrace();
                        }
                    }
                    }
                }
            };
            as2.cstore(cuid, iuid, forwardPriority, createDataWriter(in), tsuid, rspHandler);
        } catch (NoPresentationContextException npc) {
            handleForwardException(as2, file, npc, ".npc");
        } catch (AssociationStateException as) {
            handleForwardException(as2, file, as, ".ass");
        } catch (IOException e) {
            handleForwardException(as2, file, e, ".conn");
        } catch (InterruptedException e) {
            LOG.warn(as2 + ": Unexpected exception:", e);
        } finally {
            SafeClose.close(in);
        }
      
    }

    private void handleForwardException(final Association as, final File file,
            Exception e, String suffix) throws DicomServiceException {
        LOG.warn(as + ": Failed to forward file:" + file + " : " + e.getMessage());
        as.setProperty(FILE_SUFFIX, suffix);
        rename(as, file);
    }
    
    private void rename(Association as, File file) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.info("{}: M-RENAME {} to {}", new Object[] {as, file, dst});
        }
        else {
            LOG.warn("{}: Failed to M-RENAME {} to {}", new Object[] {as, file, dst});
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private DataWriter createDataWriter(DicomInputStream in) throws IOException {
        if (isCoerceAttributes()) {
            in.setIncludeBulkDataLocator(true);
            Attributes attrs = in.readDataset(-1, -1);
            coerceAttributes(attrs);
            return new DataWriterAdapter(attrs);
        }
        return new InputStreamDataWriter(in);
    }

    private static void delete(Association as, File file) {
        if (file.delete())
            LOG.info("{}: M-DELETE {}", as, file);
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

}
