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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.AttributeCoercions;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.UID;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.net.AEExtension;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.common.CMoveInfoObject;
import org.dcm4chee.proxy.common.RetryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyAEExtension extends AEExtension {

    public static final Logger LOG = LoggerFactory.getLogger(ProxyAEExtension.class);

    private static final long serialVersionUID = -3552156927326582473L;
    private static final String separator = System.getProperty("file.separator");
    public static final String newline = System.getProperty("line.separator");
    private static final String jbossServerDataDir = System.getProperty("jboss.server.data.dir");
    private static final String currentWorkingDir = System.getProperty("user.dir");
    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FILE_SUFFIX = ".part";
    public static final String FORWARD_RULES = "forward.rules";
    public static final String FORWARD_CMOVE_INFO = "forward.cmove.info";
    public static final String CALLING_AET = "AET";

    private String spoolDirectory;
    private boolean acceptDataOnFailedAssociation;
    private boolean enableAuditLog;
    private HashMap<String, ForwardOption> forwardOptions = new HashMap<String, ForwardOption>();
    private List<Retry> retries = new ArrayList<Retry>();
    private List<ForwardRule> forwardRules = new ArrayList<ForwardRule>();
    private AttributeCoercions attributeCoercions = new AttributeCoercions();
    private String proxyPIXConsumerApplication;
    private String remotePIXManagerApplication;
    private boolean deleteFailedDataWithoutRetryConfiguration;
    private String fallbackDestinationAET;
    private CMoveInfoObject[] CMoveMessageID = new CMoveInfoObject[256];

    public boolean isAcceptDataOnFailedAssociation() {
        return acceptDataOnFailedAssociation;
    }

    public void setAcceptDataOnFailedAssociation(boolean acceptDataOnFailedNegotiation) {
        this.acceptDataOnFailedAssociation = acceptDataOnFailedNegotiation;
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

    public File getAuditDirectoryPath() {
        File path = new File(getSpoolDirectoryPath(), "audit");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "audit")
                : new File(currentWorkingDir, "audit");
        path.mkdirs();
        return path;
    }

    public File getTransferredAuditDirectoryPath() {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.TRANSFERRED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.TRANSFERRED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.TRANSFERRED.getDirectoryName());
        path.mkdirs();
        return path;
    }

    public File getDeleteAuditDirectoryPath() {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.DELETED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.DELETED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.DELETED.getDirectoryName());
        path.mkdirs();
        return path;
    }

    public File getFailedAuditDirectoryPath() {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.FAILED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.FAILED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.FAILED.getDirectoryName());
        path.mkdirs();
        return path;
    }

    public File getNactionDirectoryPath() {
        File path = new File(getSpoolDirectoryPath(), "naction");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "naction")
                : new File(currentWorkingDir, "naction");
        path.mkdirs();
        return path;
    }

    public File getNeventDirectoryPath() {
        File path = new File(getSpoolDirectoryPath(), "nevent");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "nevent")
                : new File(currentWorkingDir, "nevent");
        path.mkdirs();
        return path;
    }

    public File getNCreateDirectoryPath() {
        File path = new File(getSpoolDirectory() + separator + "mpps" + separator + "ncreate");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "ncreate")
                : new File(currentWorkingDir, "mpps" + separator + "ncreate");
        path.mkdirs();
        return path;
    }

    public File getNSetDirectoryPath() {
        File path = new File(getSpoolDirectory() + separator + "mpps" + separator + "nset");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "nset")
                : new File(currentWorkingDir, "mpps" + separator + "nset");
        path.mkdirs();
        return path;
    }
    
    public File getCStoreDirectoryPath() {
        File path = new File(getSpoolDirectory(), "cstore");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "cstore")
                : new File(currentWorkingDir, "cstore");
        path.mkdirs();
        return path;
    }

    public File getDoseSrPath() {
        File path = new File(getSpoolDirectory(), "dose");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "dose")
                : new File(currentWorkingDir, "dose");
        path.mkdirs();
        return path;
    }

    public File getNoRetryPath() {
        File path = new File(getSpoolDirectory(), "noRetry");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "noRetry")
                : new File(currentWorkingDir, "noRetry");
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

    public HashMap<String, ForwardOption> getForwardOptions() {
        return forwardOptions;
    }

    public void setForwardOptions(HashMap<String, ForwardOption> forwardOptions) {
        this.forwardOptions = forwardOptions;
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

    private File getLogDir(AuditDirectory auditDir, String callingAET, String calledAET, String studyIUID, Integer retry)
            throws DicomServiceException {
        File path = null;
        String subDirs = getSeparator() + calledAET + getSeparator() + callingAET + getSeparator() + studyIUID;
        switch (auditDir) {
        case TRANSFERRED:
            path = new File(getTransferredAuditDirectoryPath().getPath() + subDirs);
            break;
        case FAILED:
            path = new File(getFailedAuditDirectoryPath().getPath()  + subDirs + getSeparator() + retry);
            break;
        case DELETED:
            path = new File(getDeleteAuditDirectoryPath().getPath() + subDirs);
            break;
        default:
            LOG.error("Unrecognized Audit Directory: " + auditDir.getDirectoryName());
            break;
        }
        if (path == null)
            throw new DicomServiceException(Status.UnableToProcess);
        path.mkdirs();
        return path;
    }

    public AttributeCoercion getAttributeCoercion(String aeTitle, String sopClass,
            Role role, Dimse cmd) {
        return attributeCoercions.findMatching(sopClass, cmd, role, aeTitle);
    }

    public AttributeCoercions getAttributeCoercions() {
        return attributeCoercions;
    }

    public List<String> getDestinationAETsFromForwardRule(Association as, ForwardRule rule, Attributes data)
            throws ConfigurationException, DicomServiceException {
        List<String> destinationAETs = new ArrayList<String>();
        if (rule.containsTemplateURI()) {
            ProxyDeviceExtension proxyDevExt = as.getApplicationEntity().getDevice()
                    .getDeviceExtension(ProxyDeviceExtension.class);
            destinationAETs.addAll(getDestinationAETsFromTemplate(rule.getDestinationTemplate(), proxyDevExt, data));
        } else
            destinationAETs.addAll(rule.getDestinationAETitles());
        LOG.info("{}: sending data to {} based on ForwardRule \"{}\"",
                new Object[] { as, destinationAETs, rule.getCommonName() });
        return destinationAETs;
    }

    private List<String> getDestinationAETsFromTemplate(String uri, ProxyDeviceExtension proxyDevExt, Attributes data)
            throws ConfigurationException {
        final List<String> result = new ArrayList<String>();
        try {
            Templates templates = proxyDevExt.getTemplates(uri);
            SAXTransformerFactory transFac = (SAXTransformerFactory) TransformerFactory.newInstance();
            TransformerHandler handler = transFac.newTransformerHandler(templates);
            handler.setResult(new SAXResult(new DefaultHandler() {

                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes)
                        throws SAXException {
                    if (qName.equals("Destination")) {
                        result.add(attributes.getValue("aet"));
                    }
                }

            }));
            SAXWriter saxWriter = new SAXWriter(handler);
            saxWriter.write(data);
        } catch (Exception e) {
            LOG.error("Error parsing template {}: {}", uri, e);
            throw new ConfigurationException(e.getMessage());
        }
        if (result.isEmpty()) {
            LOG.error("Parsing template {} returned no result", uri);
            throw new ConfigurationException();
        }
        return result;
    }

    public String getProxyPIXConsumerApplication() {
        return proxyPIXConsumerApplication;
    }

    public void setProxyPIXConsumerApplication(String pixConsumerApplication) {
        this.proxyPIXConsumerApplication = pixConsumerApplication;
    }

    public String getRemotePIXManagerApplication() {
        return remotePIXManagerApplication;
    }

    public void setRemotePIXManagerApplication(String pixManagerApplication) {
        this.remotePIXManagerApplication = pixManagerApplication;
    }

    public boolean isDeleteFailedDataWithoutRetryConfiguration() {
        return deleteFailedDataWithoutRetryConfiguration;
    }

    public void setDeleteFailedDataWithoutRetryConfiguration(boolean deleteFailedDataWithoutRetryConfiguration) {
        this.deleteFailedDataWithoutRetryConfiguration = deleteFailedDataWithoutRetryConfiguration;
    }

    public String getFallbackDestinationAET() {
        return fallbackDestinationAET;
    }

    public void setFallbackDestinationAET(String fallbackDestinationAET) {
        this.fallbackDestinationAET = fallbackDestinationAET;
    }

    public int getNewCMoveMessageID(CMoveInfoObject info) {
        for (int index = 0; index < 256; index++)
            if (CMoveMessageID[index] == null) {
                CMoveMessageID[index] = info;
                return index + 1;
            }
        return -1;
    }

    public CMoveInfoObject getCMoveInfoObject(int msgId) {
        return CMoveMessageID[msgId - 1];
    }

    public void removeCMoveInfoObject(int msgId) {
        CMoveMessageID[msgId - 1] = null;
    }

    public boolean isAssociationFromDestinationAET(Association asAccepted) {
        ProxyAEExtension pae = (ProxyAEExtension) asAccepted.getApplicationEntity().getAEExtension(
                ProxyAEExtension.class);
        for (ForwardRule rule : pae.getForwardRules())
            for (String destinationAET : rule.getDestinationAETitles())
                if (asAccepted.getRemoteAET().equals(destinationAET))
                    return true;
        return false;
    }

    @Override
    public void reconfigure(AEExtension from) {
        ProxyAEExtension proxyAEE = (ProxyAEExtension) from;
        setForwardRules(proxyAEE.forwardRules);
        setForwardOptions(proxyAEE.forwardOptions);
        setRetries(proxyAEE.retries);
        setSpoolDirectory(proxyAEE.spoolDirectory);
        setAcceptDataOnFailedAssociation(proxyAEE.acceptDataOnFailedAssociation);
        setEnableAuditLog(proxyAEE.enableAuditLog);
        setProxyPIXConsumerApplication(proxyAEE.proxyPIXConsumerApplication);
        setRemotePIXManagerApplication(proxyAEE.remotePIXManagerApplication);
        setDeleteFailedDataWithoutRetryConfiguration(proxyAEE.deleteFailedDataWithoutRetryConfiguration);
        setFallbackDestinationAET(proxyAEE.fallbackDestinationAET);
        attributeCoercions.clear();
        for (AttributeCoercion ac : proxyAEE.getAttributeCoercions().getAll())
            addAttributeCoercion(ac);
    }

    public List<ForwardRule> filterForwardRulesOnDimseRQ(Association as, Attributes rq, Dimse dimse) {
        List<ForwardRule> filterList = new ArrayList<ForwardRule>();
        for (ForwardRule rule : getCurrentForwardRules(as)) {
            String rqSopClass = rq.getString(dimse.tagOfSOPClassUID());
            if (rule.getDimse().isEmpty() && rule.getSopClass().isEmpty()
                    || rule.getSopClass().contains(rqSopClass) && rule.getDimse().isEmpty()
                    || rule.getDimse().contains(dimse) 
                        && (rule.getSopClass().isEmpty() || rule.getSopClass().contains(rqSopClass)))
                filterList.add(rule);
        }
        List<ForwardRule> returnList = new ArrayList<ForwardRule>(filterList);
        for (Iterator<ForwardRule> iterator = filterList.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            for (ForwardRule fwr : filterList) {
                if (rule.getCommonName().equals(fwr.getCommonName()))
                    continue;
                if (rule.getDimse().isEmpty() && !fwr.getDimse().isEmpty()) {
                    returnList.remove(rule);
                    break;
                }
                if (rule.getSopClass().isEmpty() && !fwr.getSopClass().isEmpty())
                    returnList.remove(rule);
            }
        }
        return returnList;
    }

    public Attributes coerceDataset(Association as, Role role, Dimse dimse, Attributes attrs, Attributes cmd,
            ProxyDeviceExtension proxyDevExt) throws IOException {
        AttributeCoercion ac = getAttributeCoercion(as.getRemoteAET(), cmd.getString(dimse.tagOfSOPClassUID()), role, dimse);
        return (ac != null)
            ? coerceAttributes(as, attrs, ac, proxyDevExt)
            : attrs;
    }

    public Attributes coerceAttributes(Association as, Attributes attrs, AttributeCoercion ac,
            ProxyDeviceExtension proxyDevExt) {
        Attributes tmp = new Attributes(attrs);
        LOG.debug("{}: apply attribute coercion {} (dimse={}, role={}{}{})",
                new Object[] { 
                    as, 
                    ac.getURI(), 
                    ac.getDimse(), 
                    ac.getRole(), 
                    ac.getAETitle() == null ? "" : ", aet=" + ac.getAETitle(),
                    ac.getSopClass() == null ? "" : ", sopClass=" + ac.getSopClass()
        });
        Attributes modify = new Attributes();
        try {
            SAXWriter w = SAXTransformer.getSAXWriter(proxyDevExt.getTemplates(ac.getURI()), modify);
            w.setIncludeKeyword(false);
            w.write(tmp);
        } catch (Exception e) {
            new IOException(e);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("{}: attribute coercion result:{}{}",
                    new Object[] { as, newline, modify.toString(Integer.MAX_VALUE, 200) });
        tmp.addAll(modify);
        return tmp;
    }

    public void createStartLogFile(AuditDirectory auditDir, String callingAET, String calledAET, String proxyHostname, 
            Properties fileInfo, Integer retry) throws DicomServiceException {
        String studyIUID = fileInfo.containsKey("study-iuid") 
                ? fileInfo.getProperty("study-iuid")
                : fileInfo.getProperty("sop-instance-uid");
        File file = new File(getLogDir(auditDir, callingAET, calledAET, studyIUID, retry), "start.log");
        if (!file.exists()) {
            try {
                Properties prop = new Properties();
                prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
                prop.setProperty("patient-id", fileInfo.getProperty("patient-id"));
                prop.setProperty("hostname", fileInfo.getProperty("hostname"));
                prop.setProperty("proxy-hostname", proxyHostname);
                prop.store(new FileOutputStream(file), null);
            } catch (IOException e) {
                LOG.debug("Failed to create log file: " + e.getMessage());
            }
        }
    }

    public File writeLogFile(AuditDirectory auditDir, String callingAET, String calledAET, Properties fileInfo, 
            long size, Integer retry) {
        Properties prop = new Properties();
        File file = null;
        try {
            String studyIUID = fileInfo.containsKey("study-iuid") 
                    ? fileInfo.getProperty("study-iuid")
                    : fileInfo.getProperty("sop-instance-uid");
            file = new File(getLogDir(auditDir, callingAET, calledAET, studyIUID , retry), 
                    fileInfo.getProperty("sop-instance-uid").concat(".log"));
            prop.setProperty("sop-class-uid", fileInfo.getProperty("sop-class-uid"));
            prop.setProperty("size", String.valueOf(size));
            prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
            prop.store(new FileOutputStream(file), null);
        } catch (IOException e) {
            LOG.debug("Failed to create log file: " + e.getMessage());
        }
        return file;
    }

    public void addAttributeCoercion(AttributeCoercion ac) {
        attributeCoercions.add(ac);
    }

    public Properties getFileInfoProperties(File file) throws IOException {
        Properties prop = new Properties();
        FileInputStream inStream = null;
        try {
            String infoFileName = file.getPath().substring(0, file.getPath().indexOf('.')) + ".info";
            LOG.debug("Loading info file {}", infoFileName);
            inStream = new FileInputStream(infoFileName);
            prop.load(inStream);
        } finally {
            SafeClose.close(inStream);
        }
        return prop;
    }

    public HashMap<String, Association> openForwardAssociations(Association asAccepted, List<ForwardRule> forwardRules,
            Attributes data, ApplicationEntityCache aeCache) throws DicomServiceException {
        HashMap<String, Association> fwdAssocs = new HashMap<String, Association>(forwardRules.size());
        for (ForwardRule rule : forwardRules) {
            String callingAET = (rule.getUseCallingAET() == null) ? asAccepted.getCallingAET() : rule
                    .getUseCallingAET();
            List<String> destinationAETs = new ArrayList<String>();
            try {
                destinationAETs = getDestinationAETsFromForwardRule(asAccepted, rule, data);
            } catch (ConfigurationException e) {
                LOG.error("Failed to get destination AET from forward rule {}: {}", rule.getCommonName(), e);
                asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConfigurationException.getSuffix() + "0");
                return fwdAssocs;
            }
            for (String calledAET : destinationAETs) {
                try {
                    Association asInvoked = openForwardAssociation(asAccepted, rule, callingAET, calledAET,
                            copyOf(asAccepted, rule), aeCache);
                    if (asInvoked != null)
                        fwdAssocs.put(calledAET, asInvoked);
                } catch (IncompatibleConnectionException e) {
                    LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.IncompatibleConnectionException.getSuffix() + "0");
                } catch (GeneralSecurityException e) {
                    LOG.error("Failed to create SSL context: ", e.getMessage());
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.GeneralSecurityException.getSuffix() + "0");
                } catch (ConfigurationException e) {
                    LOG.error("Unable to load configuration for destination AET {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConfigurationException.getSuffix() + "0");
                } catch (ConnectException e) {
                    LOG.error("Unable to connect to {}: {}", new Object[] { calledAET, e });
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, RetryObject.ConnectionException.getSuffix() + "0");
                } catch (Exception e) {
                    LOG.error("Unexpected exception: ", e);
                    asAccepted.setProperty(ProxyAEExtension.FILE_SUFFIX, ".err");
                }
            }
        }
        return fwdAssocs;
    }

    public Association openForwardAssociation(Association asAccepted, ForwardRule rule, String callingAET,
            String calledAET, AAssociateRQ rq, ApplicationEntityCache aeCache) throws IOException,
            InterruptedException, IncompatibleConnectionException, GeneralSecurityException, ConfigurationException {
        rq.setCallingAET(callingAET);
        rq.setCalledAET(calledAET);
        if (forwardOptions.containsKey(callingAET) && forwardOptions.get(callingAET).isConvertEmf2Sf())
            addEnhancedTS(rq);
        else if (forwardOptions.containsKey(calledAET) && forwardOptions.get(calledAET).isConvertEmf2Sf())
            addReducedTS(rq);
        Association asInvoked = getApplicationEntity().connect(aeCache.findApplicationEntity(calledAET), rq);
        asInvoked.setProperty(FORWARD_ASSOCIATION, asAccepted);
        asInvoked.setProperty(ForwardRule.class.getName(), rule);
        return asInvoked;
    }

    private void addEnhancedTS(AAssociateRQ rq) {
        List<PresentationContext> newPcList = new ArrayList<PresentationContext>(3);
        int pcSize = rq.getNumberOfPresentationContexts();
        HashMap<String, String[]> as_ts = new HashMap<String, String[]>(pcSize);
        for (PresentationContext pc : rq.getPresentationContexts())
            as_ts.put(pc.getAbstractSyntax(), pc.getTransferSyntaxes());
        addTsSopClass(UID.CTImageStorage, UID.EnhancedCTImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.MRImageStorage, UID.EnhancedMRImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.PositronEmissionTomographyImageStorage, UID.EnhancedPETImageStorage, newPcList, pcSize, as_ts);
        for (PresentationContext pc : newPcList)
            rq.addPresentationContext(pc);
    }
    
    private void addReducedTS(AAssociateRQ rq) {
        List<PresentationContext> newPcList = new ArrayList<PresentationContext>(3);
        int pcSize = rq.getNumberOfPresentationContexts();
        HashMap<String, String[]> as_ts = new HashMap<String, String[]>(pcSize);
        for (PresentationContext pc : rq.getPresentationContexts())
            as_ts.put(pc.getAbstractSyntax(), pc.getTransferSyntaxes());
        addTsSopClass(UID.EnhancedCTImageStorage, UID.CTImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.EnhancedMRImageStorage, UID.MRImageStorage, newPcList, pcSize, as_ts);
        addTsSopClass(UID.EnhancedPETImageStorage, UID.PositronEmissionTomographyImageStorage, newPcList, pcSize, as_ts);
        for (PresentationContext pc : newPcList)
            rq.addPresentationContext(pc);
    }

    private void addTsSopClass(String tsA, String tsB, List<PresentationContext> newPcList,
            int pcSize, HashMap<String, String[]> as_ts) {
        String[] imageStorageTS = as_ts.get(tsA);
        if (imageStorageTS != null)
            if (!as_ts.containsKey(tsB))
                newPcList.add(new PresentationContext((pcSize + newPcList.size()) * 2 + 1, tsB, imageStorageTS));
    }

    public AAssociateRQ copyOf(Association as, ForwardRule rule) {
        AAssociateRQ rq = as.getAAssociateRQ();
        AAssociateRQ copy = new AAssociateRQ();
        if (rule.isExclusiveUseDefinedTC())
            for (PresentationContext pc : filterMatchingPC(as))
                copy.addPresentationContext(pc);
        else
            for (PresentationContext pc : rq.getPresentationContexts())
                copy.addPresentationContext(pc);
        copy.setReservedBytes(rq.getReservedBytes());
        copy.setProtocolVersion(rq.getProtocolVersion());
        copy.setMaxPDULength(rq.getMaxPDULength());
        copy.setMaxOpsInvoked(rq.getMaxOpsInvoked());
        copy.setMaxOpsPerformed(rq.getMaxOpsPerformed());
        copy.setCalledAET(rq.getCalledAET());
        copy.setCallingAET(rq.getCallingAET());
        copy.setApplicationContext(rq.getApplicationContext());
        copy.setImplClassUID(rq.getImplClassUID());
        copy.setImplVersionName(rq.getImplVersionName());
        copy.setUserIdentityRQ(rq.getUserIdentityRQ());
        for (RoleSelection rs : rq.getRoleSelections())
            copy.addRoleSelection(rs);
        for (ExtendedNegotiation en : rq.getExtendedNegotiations())
            copy.addExtendedNegotiation(en);
        for (CommonExtendedNegotiation cen : rq.getCommonExtendedNegotiations())
            copy.addCommonExtendedNegotiation(cen);
        return copy;
    }


    private List<PresentationContext> filterMatchingPC(Association as) {
        AAssociateRQ rq = as.getAAssociateRQ();
        AAssociateAC ac = as.getAAssociateAC();
        List<PresentationContext> returnList = new ArrayList<PresentationContext>(rq.getNumberOfPresentationContexts());
        for (int i = 0; i < rq.getNumberOfPresentationContexts(); i++) {
            int pcid = i * 2 + 1;
            PresentationContext pcAC = ac.getPresentationContext(pcid);
            PresentationContext pcRQ = rq.getPresentationContext(pcid);
            List<String> tss = new ArrayList<String>(pcAC.getTransferSyntaxes().length);
            tss.add(UID.ImplicitVRLittleEndian);
            for (String otherTss : pcAC.getTransferSyntaxes())
                if (!tss.contains(otherTss))
                    tss.add(otherTss);
            returnList.add(new PresentationContext(pcid, pcRQ.getAbstractSyntax(), (String[]) tss
                    .toArray(new String[tss.size()])));
        }
        return returnList;
    }

    public static String getMatchingTsuid(Association asInvoked, String tsuid, String cuid) {
        Set<String> tsuids = asInvoked.getTransferSyntaxesFor(cuid);
        return tsuids.contains(tsuid) 
                ? tsuid 
                : tsuids.isEmpty() 
                    ? null 
                    : tsuids.iterator().next();
    }

}
