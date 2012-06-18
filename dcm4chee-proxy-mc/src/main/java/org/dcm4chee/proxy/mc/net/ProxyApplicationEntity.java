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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.AttributeCoercions;
import org.dcm4che.conf.api.ConfigurationException;
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
import org.dcm4che.net.Dimse;
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
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyApplicationEntity extends ApplicationEntity {

    private static final long serialVersionUID = -3552156927326582473L;
    private static final String separator = System.getProperty("file.separator");
    private static final String jbossServerDataDir = System.getProperty("jboss.server.data.dir");
    private static final String currentWorkingDir = System.getProperty("user.dir");
    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FILE_SUFFIX = ".dcm.part";
    public static final String FORWARD_RULES = "forward.rules";

    private String spoolDirectory;
    private String auditDirectory;
    private String nactionDirectory;
    private String neventDirectory;
    private boolean acceptDataOnFailedNegotiation;
    private boolean enableAuditLog;
    private HashMap<String, Schedule> forwardSchedules;
    private List<Retry> retries = new ArrayList<Retry>();
    private List<ForwardRule> forwardRules = new ArrayList<ForwardRule>();
    private final AttributeCoercions attributeCoercions = new AttributeCoercions();

    public boolean isAcceptDataOnFailedNegotiation() {
        return acceptDataOnFailedNegotiation;
    }

    public void setAcceptDataOnFailedNegotiation(boolean acceptDataOnFailedNegotiation) {
        this.acceptDataOnFailedNegotiation = acceptDataOnFailedNegotiation;
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

    public HashMap<String, Schedule> getForwardSchedules() {
        return forwardSchedules;
    }

    public void setForwardSchedules(HashMap<String, Schedule> forwardSchedules) {
        this.forwardSchedules = forwardSchedules;
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

    public void setNactionDirectory(String nactionDirectory) {
        this.nactionDirectory = nactionDirectory;
    }

    public String getNactionDirectory() {
        return nactionDirectory;
    }
    
    public File getNactionDirectoryPath() {
        File path = new File(nactionDirectory);
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, nactionDirectory)
                : new File(currentWorkingDir, nactionDirectory);
        path.mkdirs();
        return path;
    }

    public void setNeventDirectory(String neventDirectory) {
        this.neventDirectory = neventDirectory;
    }

    public String getNeventDirectory() {
        return neventDirectory;
    }
    
    public File getNeventDirectoryPath() {
        File path = new File(neventDirectory);
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, neventDirectory)
                : new File(currentWorkingDir, neventDirectory);
        path.mkdirs();
        return path;
    }

    public static String getSeparator() {
        return separator;
    }

    public List<ForwardRule> getForwardRules() {
        return forwardRules;
    }

    public void setForwardRules(List<ForwardRule> forwardingRules) {
        this.forwardRules = forwardingRules;
    }

    @SuppressWarnings("unchecked")
    public List<ForwardRule> getCurrentForwardRules(Association as) {
        return (List<ForwardRule>) as.getProperty(FORWARD_RULES);
    }

    @Override
    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac) throws IOException {
        filterForwardRulesOnNegotiationRQ(as, rq);
        if (!isAssociationFromDestinationAET(as) && sendNow(as))
            return forwardAAssociateRQ(as, rq, ac, getCurrentForwardRules(as).get(0));
        as.setProperty(FILE_SUFFIX, ".dcm");
        rq.addRoleSelection(new RoleSelection(UID.StorageCommitmentPushModelSOPClass, true, true));
        return super.negotiate(as, rq, ac);
    }

    private boolean sendNow(Association as) {
        List<ForwardRule> matchingForwardRules = getCurrentForwardRules(as);
        return (matchingForwardRules.size() == 1 
                && !forwardBasedOnTemplates(matchingForwardRules)
                && matchingForwardRules.get(0).getDimse() == null
                && matchingForwardRules.get(0).getSopClass() == null
                && (matchingForwardRules.get(0).getCallingAET() == null || 
                        matchingForwardRules.get(0).getCallingAET().equals(as.getCallingAET()))
                && isAvailableDestinationAET(matchingForwardRules.get(0).getDestinationURI()));
    }

    private boolean isAssociationFromDestinationAET(Association asAccepted) {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
        for (Entry<String, Schedule> schedule : pae.getForwardSchedules().entrySet())
            if (asAccepted.getCallingAET().equals(schedule.getKey()))
                return true;
        return false;
    }

    public void filterForwardRulesOnNegotiationRQ(Association as, AAssociateRQ rq) {
        List<ForwardRule> list = new ArrayList<ForwardRule>();
        for (ForwardRule rule : getForwardRules()) {
            String callingAET = rule.getCallingAET();
            if ((callingAET == null || callingAET.equals(rq.getCallingAET()))
                    && rule.getReceiveSchedule().isNow(new GregorianCalendar()))
                list.add(rule);
        }
        as.setProperty(FORWARD_RULES, list);
    }

    public List<ForwardRule> filterForwardRulesOnDimseRQ(Association as, Attributes rq, Dimse dimse, Integer sopClass) {
        List<ForwardRule> rules = new ArrayList<ForwardRule>(getCurrentForwardRules(as));
        for (Iterator<ForwardRule> iterator = rules.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            if (rule.getDimse() != null && rule.getDimse() != dimse) {
                iterator.remove();
                continue;
            }
            if (rule.getSopClass() != null && !rq.getString(sopClass).equals(rule.getSopClass()))
                iterator.remove();
        }
        for (Iterator<ForwardRule> iterator = rules.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            for (ForwardRule fwr : rules) {
                if (rule.getDimse() == null && fwr.getDimse() != null) {
                    iterator.remove();
                    continue;
                }
                if (rule.getSopClass() == null && fwr.getSopClass() != null)
                    iterator.remove();
            }
        }
        return rules;
    }

    private boolean isAvailableDestinationAET(String destinationAET) {
        for(Entry<String, Schedule> entry : forwardSchedules.entrySet())
            if (entry.getKey().equals(destinationAET))
                return entry.getValue().isNow(new GregorianCalendar());
        return false;
    }

    public boolean forwardBasedOnTemplates(List<ForwardRule> forwardRules) {
        for (ForwardRule rule : forwardRules)
            if (rule.getReceiveSchedule() == null || rule.getReceiveSchedule().isNow(new GregorianCalendar()))
                if (rule.getDestinationURI().startsWith("xsl:"))
                    return true;
        return false;
    }

    public HashMap<String, String> getAETsFromForwardRules(Association as, List<ForwardRule> rules)
            throws TransformerFactoryConfigurationError {
        HashMap<String, String> aeList = new HashMap<String, String>();
        for (ForwardRule rule : rules) {
            String callingAET = (rule.getUseCallingAET() == null) ? as.getCallingAET() : rule.getUseCallingAET();
            List<String> destinationAETs = new ArrayList<String>();
            if (rule.getDestinationURI().startsWith("xsl:"))
                try {
                    destinationAETs = getDestinationAETsFromTemplate((Templates) getTemplates(rule.getDestinationURI()));
                } catch (TransformerException e) {
                    LOG.warn("Error parsing template", e);
                }
            else
                destinationAETs.add(rule.getDestinationURI());
            for (String destinationAET : destinationAETs)
                aeList.put(destinationAET, callingAET);
        }
        return aeList;
    }

    public List<String> getDestinationAETsFromTemplate(Templates template) throws TransformerFactoryConfigurationError,
            TransformerConfigurationException {
        SAXTransformerFactory transFac = (SAXTransformerFactory) TransformerFactory.newInstance();
        TransformerHandler handler = transFac.newTransformerHandler(template);
        final List<String> result = new ArrayList<String>();
        handler.setResult(new SAXResult(new DefaultHandler() {

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes)
                    throws SAXException {
                if (qName.equals("Destination")) {
                    result.add(attributes.getValue("aet"));
                }
            }

        }));
        return result;
    }
    
    private AAssociateAC forwardAAssociateRQ(Association as, AAssociateRQ rq, AAssociateAC ac, ForwardRule forwardRule) 
            throws IOException {
        try {
            if (forwardRule.getUseCallingAET() != null)
                rq.setCallingAET(forwardRule.getUseCallingAET());
            else if (!getAETitle().equals("*"))
                rq.setCallingAET(getAETitle());
            rq.setCalledAET(forwardRule.getDestinationURI());
            Association asCalled = connect(getDestinationAE(forwardRule.getDestinationURI()), rq);
            as.setProperty(FORWARD_ASSOCIATION, asCalled);
            AAssociateAC acCalled = asCalled.getAAssociateAC();
            if (forwardRule.isExclusiveUseDefinedTC()) {
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
        } catch (ConfigurationException e) {
            LOG.warn("Unable to load configuration for destination AET", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (AAssociateRJ rj) {
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), rj, ".rj-" + rj.getResult() + "-"
                    + rj.getSource() + "-" + rj.getReason(), rj.getReason());
        } catch (AAbort aa) {
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), aa, ".aa-" + aa.getSource() + "-"
                    + aa.getReason(), aa.getReason());
        } catch (IOException e) {
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), e, ".conn", 0);
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException e) {
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), e, ".conf", 0);
        } catch (GeneralSecurityException e) {
            LOG.warn("Failed to create SSL context", e);
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), e, ".ssl", 0);
        }
    }

    public ApplicationEntity getDestinationAE(String destinationAETitle) throws ConfigurationException {
        ProxyDevice device = (ProxyDevice) getDevice();
        return device.findApplicationEntity(destinationAETitle);
    }

    private AAssociateAC handleNegotiateConnectException(Association as, AAssociateRQ rq,
            AAssociateAC ac, String destinationAETitle, Exception e, String suffix, int reason) 
                    throws IOException, AAbort {
        as.clearProperty(FORWARD_ASSOCIATION);
        LOG.debug(as + ": unable to connect to " + destinationAETitle, e);
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

    public void coerceDataset(String remoteAET, Attributes attrs) throws IOException {
        AttributeCoercion ac = getAttributeCoercion(remoteAET, attrs.getString(Tag.SOPClassUID), Role.SCU,
                Dimse.C_STORE_RQ);
        if (ac != null)
            coerceAttributes(attrs, ac);
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
        for (String calledAET : getSpoolDirectoryPath().list())
            for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                if (calledAET.equals(entry.getKey()))
                    startForwardScheduledFiles(calledAET);
        
        for (String calledAET : getNactionDirectoryPath().list())
            for (Entry<String, Schedule> entry : forwardSchedules.entrySet())
                if (calledAET.equals(entry.getKey()))
                    startForwardScheduledNAction(getNactionDirectoryPath().listFiles(fileFilter()), entry.getKey());
    }

    private void startForwardScheduledNAction(final File[] files, final String destinationAETitle) {
        getDevice().execute(new Runnable() {

            @Override
            public void run() {
                forwardScheduledNAction(files, destinationAETitle);
            }
        });
    }
    
    private void forwardScheduledNAction(File[] files, String destinationAETitle) {
        for (File file : files) {
            try {
                AAssociateRQ rq = new AAssociateRQ();
                rq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModelSOPClass,
                        UID.ImplicitVRLittleEndian));
                Attributes fmi = readFileMetaInformation(file);
                rq.setCallingAET(fmi.getString(Tag.SourceApplicationEntityTitle));
                rq.setCalledAET(destinationAETitle);
                Association as = connect(getDestinationAE(destinationAETitle), rq);
                try {
                    if (as.isReadyForDataTransfer()) {
                        forwardScheduledNAction(as, file, fmi);
                    } else {
                        as.setProperty(FILE_SUFFIX, ".conn");
                        rename(as, file);
                    }
                } finally {
                    if (as != null && as.isReadyForDataTransfer()) {
                        try {
                            as.waitForOutstandingRSP();
                            as.release();
                        } catch (InterruptedException e) {
                            LOG.warn(as + ": unexpected exception", e);
                        } catch (IOException e) {
                            LOG.warn(as + ": failed to release association", e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                LOG.warn("Connection exception", e);
            } catch (IncompatibleConnectionException e) {
                LOG.warn("Incompatible connection", e);
            } catch (ConfigurationException e) {
                LOG.warn("Unable to load configuration", e);
            } catch (IOException e) {
                LOG.warn("Unable to read from file", e);
            } catch (GeneralSecurityException e) {
                LOG.warn("Failed to create SSL context", e);
            }
        }
    }

    private void forwardScheduledNAction(final Association as, final File file, Attributes fmi) throws IOException,
            InterruptedException {
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
        String tsuid = UID.ImplicitVRLittleEndian;
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
                    File dest = new File(getNeventDirectoryPath(), transactionUID);
                    if (file.renameTo(dest)) {
                        dest.setLastModified(System.currentTimeMillis());
                        LOG.debug("{}: RENAME {} to {}", new Object[] { as, file, dest });
                    } else
                        LOG.warn("{}: failed to RENAME {} to {}", new Object[] { as, file, dest });
                    break;
                }
                default: {
                    LOG.warn("{}: failed to forward N-ACTION file {} with error status {}", new Object[] { as, file,
                            Integer.toHexString(status) + 'H' });
                    as.setProperty(FILE_SUFFIX, '.' + Integer.toHexString(status) + 'H');
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

    private void startForwardScheduledFiles(final String calledAET) {
        getDevice().execute(new Runnable() {
            
            @Override
            public void run() {
                forwardScheduledFiles(calledAET);
            }
        });
    }

    private void forwardScheduledFiles(String calledAET) {
        File dir = new File(getSpoolDirectoryPath(), calledAET);
        File[] files = dir.listFiles(fileFilter());
        if (files != null && files.length > 0)
            for (ForwardTask ft : scanFiles(calledAET, files))
                try {
                    processForwardTask(ft);
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
                String file = path.substring(path.lastIndexOf(getSeparator()) + 1);
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
                return file.split(retry.suffix, -1).length - 1 < (Integer) retry.numberOfRetries;
            }
        };
    }

    private void processForwardTask(ForwardTask ft) throws DicomServiceException {
        AAssociateRQ rq = ft.getAAssociateRQ();
        Association asInvoked = null;
        try {
            asInvoked = connect(getDestinationAE(rq.getCalledAET()), rq);
            for (File file : ft.getFiles()) {
                try {
                    if (asInvoked.isReadyForDataTransfer()) {
                        forwardScheduledFiles(asInvoked, file);
                    } else {
                        asInvoked.setProperty(FILE_SUFFIX, ".conn");
                        rename(asInvoked, file);
                    }
                } catch (NoPresentationContextException npc) {
                    handleForwardException(asInvoked, file, npc, ".npc");
                } catch (AssociationStateException ass) {
                    handleForwardException(asInvoked, file, ass, ".ass");
                } catch (IOException ioe) {
                    handleForwardException(asInvoked, file, ioe, ".conn");
                    releaseAS(asInvoked);
                }
            }
        } catch (ConfigurationException ce) {
            LOG.warn("Unable to load configuration", ce);
        } catch (AAssociateRJ rj) {
            handleProcessException(ft, rj, ".rj-" + rj.getResult() + "-" + rj.getSource() + "-" + rj.getReason());
        } catch (AAbort aa) {
            handleProcessException(ft, aa, ".aa-" + aa.getSource() + "-" + aa.getReason());
        } catch (IOException e) {
            handleProcessException(ft, e, ".conn");
        } catch (InterruptedException e) {
            LOG.warn("Connection exception", e);
        } catch (IncompatibleConnectionException e) {
            LOG.warn("Incompatible connection", e);
        } catch (GeneralSecurityException e) {
            LOG.warn("Failed to create SSL context", e);
        } finally {
            if (asInvoked != null && asInvoked.isReadyForDataTransfer()) {
                try {
                    asInvoked.waitForOutstandingRSP();
                    asInvoked.release();
                } catch (InterruptedException e) {
                    LOG.warn(asInvoked + ": unexpected exception", e);
                } catch (IOException e) {
                    LOG.warn(asInvoked + ": failed to release association", e);
                }
            }
        }
    }

    private void handleForwardException(Association as, File file, Exception e, String suffix)
            throws DicomServiceException {
        LOG.debug(as + ": error processing forward task", e);
        as.setProperty(FILE_SUFFIX, suffix);
        rename(as, file);
    }

    private void handleProcessException(ForwardTask ft, Exception e, String suffix) 
    throws DicomServiceException {
        LOG.debug("Connection error", e);
        for (File file : ft.getFiles()) {
            String path = file.getPath();
            File dst = new File(path.concat(suffix));
            if (file.renameTo(dst)) {
                dst.setLastModified(System.currentTimeMillis());
                LOG.debug("{}: RENAME to {}", new Object[] { file, dst });
            }
            else {
                LOG.warn("{}: failed to RENAME to {}", new Object[] { file, dst });
                throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
            }
        }
    }

    private void forwardScheduledFiles(final Association asInvoked, final File file) throws IOException,
            InterruptedException {
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
                        try {
                            writeLogFile(asInvoked, ds[0], fileSize);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        delete(asInvoked, file);
                        break;
                    default: {
                        LOG.warn("{}: failed to forward file {} with error status {}", new Object[] { asInvoked, file,
                                Integer.toHexString(status) + 'H' });
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
            asInvoked.cstore(cuid, iuid, 0, createDataWriter(in, asInvoked, ds, cuid), tsuid, rspHandler);
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

    private File rename(Association as, File file) throws DicomServiceException {
        String path = file.getPath();
        File dst = new File(path.concat((String) as.getProperty(FILE_SUFFIX)));
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("{}: RENAME {} to {}", new Object[] {as, file, dst});
            return dst;
        }
        else {
            LOG.warn("{}: failed to RENAME {} to {}", new Object[] {as, file, dst});
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private DataWriter createDataWriter(DicomInputStream in, Association as, Attributes[] ds,
            String cuid) throws IOException {
        AttributeCoercion ac =
                getAttributeCoercion(as.getRemoteAET(), cuid, Role.SCU, Dimse.C_STORE_RQ);
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
            LOG.warn("{}: failed to M-DELETE {}", as, file);
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
            LOG.warn(as + ": failed to create log file", e);
            throw new IOException(e);
        }
    }

    private File getLogDir(Association as, Attributes attrs) {
        File logDir = new File(getAuditDirectoryPath().getPath() + getSeparator() + as.getCalledAET()
                + getSeparator() + as.getCallingAET() + getSeparator() + attrs.getString(Tag.StudyInstanceUID));
        if (!logDir.exists())
            logDir.mkdirs();
        return logDir;
    }

    public void addAttributeCoercion(AttributeCoercion ac) {
        attributeCoercions.add(ac);
    }

    public AttributeCoercion getAttributeCoercion(String aeTitle, String sopClass,
            Role role, Dimse cmd) {
        return attributeCoercions.findMatching(sopClass, cmd, role, aeTitle);
    }

    public AttributeCoercions getAttributeCoercions() {
        return attributeCoercions;
    }
    
    private ProxyDevice getProxyDevice() {
        return (ProxyDevice) this.getDevice();
    }
    
    public Templates getTemplates(String uri) throws TransformerConfigurationException {
        return getProxyDevice().getTemplates(uri);
    }
}
