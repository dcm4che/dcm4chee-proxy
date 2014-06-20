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
 * Java(TM), hosted at https://github.com/dcm4che.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.dcm4che3.conf.api.AttributeCoercion;
import org.dcm4che3.conf.api.AttributeCoercions;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.TransferCapability.Role;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.common.CMoveInfoObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyAEExtension extends AEExtension {

    public static final Logger LOG = LoggerFactory.getLogger(ProxyAEExtension.class);

    private static final long serialVersionUID = -3552156927326582473L;
    private static final String separator = System.getProperty("file.separator");
    private static final String newline = System.getProperty("line.separator");
    private static final String jbossServerDataDir = System.getProperty("jboss.server.data.dir");
    private static final String currentWorkingDir = System.getProperty("user.dir");
    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FILE_SUFFIX = ".part";
    public static final String FORWARD_RULES = "forward.rules";
    public static final String FORWARD_RULE = "forward.rule";
    public static final String FORWARD_CMOVE_INFO = "forward.cmove.info";
    public static final String CALLING_AET = "calling.aet";
    public static final String PIDS = "pids";

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
    private boolean mergeStgCmtMessagesUsingANDLogic;
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
    
    public final File getSpoolDirectoryPath() throws IOException {
        File path = new File(spoolDirectory);
        if (!path.isAbsolute())
            path = jbossServerDataDir != null 
                ? new File(jbossServerDataDir, spoolDirectory)
                : new File(currentWorkingDir, spoolDirectory);
        makeDirs(path);
        return path;
    }

    public final File getAuditDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectoryPath(), "audit");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "audit")
                : new File(currentWorkingDir, "audit");
        makeDirs(path);
        return path;
    }

    public File getTransferredAuditDirectoryPath() throws IOException {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.TRANSFERRED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.TRANSFERRED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.TRANSFERRED.getDirectoryName());
        makeDirs(path);
        return path;
    }

    public File getDeleteAuditDirectoryPath() throws IOException {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.DELETED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.DELETED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.DELETED.getDirectoryName());
        makeDirs(path);
        return path;
    }

    public final File getFailedAuditDirectoryPath() throws IOException {
        File path = new File(getAuditDirectoryPath(), AuditDirectory.FAILED.getDirectoryName());
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, AuditDirectory.FAILED.getDirectoryName())
                : new File(currentWorkingDir, AuditDirectory.FAILED.getDirectoryName());
        makeDirs(path);
        return path;
    }

    public File getNactionDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectoryPath(), "naction");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "naction")
                : new File(currentWorkingDir, "naction");
        makeDirs(path);
        return path;
    }

    public File getNeventDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectoryPath(), "nevent");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "nevent")
                : new File(currentWorkingDir, "nevent");
        makeDirs(path);
        return path;
    }

    public File getNCreateDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectory() + separator + "mpps" + separator + "ncreate");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "ncreate")
                : new File(currentWorkingDir, "mpps" + separator + "ncreate");
        makeDirs(path);
        return path;
    }

    public File getNSetDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectory() + separator + "mpps" + separator + "nset");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "nset")
                : new File(currentWorkingDir, "mpps" + separator + "nset");
        makeDirs(path);
        return path;
    }
    
    public File getCStoreDirectoryPath() throws IOException {
        File path = new File(getSpoolDirectory(), "cstore");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "cstore")
                : new File(currentWorkingDir, "cstore");
        makeDirs(path);
        return path;
    }

    public static void makeDirs(File path) throws IOException {
        if (!path.mkdirs())
            if (!path.exists())
                throw new IOException("Cannot create path " + path);
    }

    public File getDoseSrPath() throws IOException {
        File path = new File(getSpoolDirectory(), "dose");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "dose")
                : new File(currentWorkingDir, "dose");
        makeDirs(path);
        return path;
    }

    public File getNoRetryPath() throws IOException {
        File path = new File(getSpoolDirectory(), "noRetry");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "noRetry")
                : new File(currentWorkingDir, "noRetry");
        makeDirs(path);
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

    public final String getSeparator() {
        return separator;
    }

    public final String getNewline() {
        return newline;
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

    public AttributeCoercion getAttributeCoercion(String aeTitle, String sopClass,
            Role role, Dimse dimse) {
        return attributeCoercions.findAttributeCoercion(sopClass, dimse, role, aeTitle);
    }

    public AttributeCoercions getAttributeCoercions() {
        return attributeCoercions;
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

    public boolean isMergeStgCmtMessagesUsingANDLogic() {
        return mergeStgCmtMessagesUsingANDLogic;
    }

    public void setMergeStgCmtMessagesUsingANDLogic(boolean mergeStgCmtMessagesWithANDLogic) {
        this.mergeStgCmtMessagesUsingANDLogic = mergeStgCmtMessagesWithANDLogic;
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
        setMergeStgCmtMessagesUsingANDLogic(proxyAEE.mergeStgCmtMessagesUsingANDLogic);
        attributeCoercions.clear();
        for (AttributeCoercion ac : proxyAEE.getAttributeCoercions())
            addAttributeCoercion(ac);
    }

    public void addAttributeCoercion(AttributeCoercion ac) {
        attributeCoercions.add(ac);
    }

    public Attributes parseAttributesWithLazyBulkData(Association as, File file)
            throws IOException {
        DicomInputStream in = null;
        try {
            in = new DicomInputStream(file);
            in.setIncludeBulkData(IncludeBulkData.URI);
            return in.readDataset(-1, -1);
        } catch (IOException e) {
            LOG.warn(as + ": Failed to decode dataset:", e);
            throw new DicomServiceException(Status.CannotUnderstand);
        } finally {
            in.close();
        }
    }


}
