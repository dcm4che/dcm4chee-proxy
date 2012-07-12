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

package org.dcm4chee.proxy.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
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
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRJ;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;
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

    @Override
    protected void setApplicationEntityAttributes(ApplicationEntity from) {
        super.setApplicationEntityAttributes(from);
        reconfigureForwardRules((ProxyApplicationEntity) from);
        reconfigureForwardSchedules((ProxyApplicationEntity) from);
        reconfigureRetries((ProxyApplicationEntity) from);
    }

    private void reconfigureRetries(ProxyApplicationEntity ae) {
        retries.clear();
        retries.addAll(ae.retries);
    }

    private void reconfigureForwardRules(ProxyApplicationEntity ae) {
        forwardRules.clear();
        forwardRules.addAll(ae.forwardRules);
    }

    private void reconfigureForwardSchedules(ProxyApplicationEntity ae) {
        forwardSchedules.clear();
        forwardSchedules.putAll(ae.forwardSchedules);
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

    public File getAuditDirectoryPath() {
        File path = new File(getSpoolDirectoryPath(), "audit");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "audit")
                : new File(currentWorkingDir, "audit");
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
        File path = new File(getSpoolDirectory() + "mpps" + separator + "ncreate");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "ncreate")
                : new File(currentWorkingDir, "mpps" + separator + "ncreate");
        path.mkdirs();
        return path;
    }

    public File getNSetDirectoryPath() {
        File path = new File(getSpoolDirectory() + "mpps" + separator + "nset");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "mpps" + separator + "nset")
                : new File(currentWorkingDir, "mpps" + separator + "nset");
        path.mkdirs();
        return path;
    }
    
    public File getCStoreDirectoryPath() {
        File path = new File(getSpoolDirectory() + "cstore");
        if (!path.isAbsolute())
            path = jbossServerDataDir != null
                ? new File(jbossServerDataDir, "cstore")
                : new File(currentWorkingDir, "cstore");
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

    private void filterForwardRulesOnNegotiationRQ(Association as, AAssociateRQ rq) {
        List<ForwardRule> list = new ArrayList<ForwardRule>();
        for (ForwardRule rule : getForwardRules()) {
            String callingAET = rule.getCallingAET();
            if ((callingAET == null || callingAET.equals(rq.getCallingAET()))
                    && rule.getReceiveSchedule().isNow(new GregorianCalendar()))
                list.add(rule);
        }
        as.setProperty(FORWARD_RULES, list);
    }

    public List<ForwardRule> filterForwardRulesOnDimseRQ(Association as, Attributes rq, Dimse dimse) {
        List<ForwardRule> rules = new ArrayList<ForwardRule>();
        for (ForwardRule rule : getCurrentForwardRules(as))
            if (equals(rule.getDimse(), dimse) && equals(rule.getSopClass(), rq.getString(dimse.tagOfSOPClassUID())))
                rules.add(rule);
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

    private boolean equals(Object o1, Object o2) {
        return o1 == null || o2 == null || o1.equals(o2);
    }

    private boolean isAvailableDestinationAET(String destinationAET) {
        for(Entry<String, Schedule> entry : forwardSchedules.entrySet())
            if (entry.getKey().equals(destinationAET))
                return entry.getValue().isNow(new GregorianCalendar());
        return false;
    }

    private boolean forwardBasedOnTemplates(List<ForwardRule> forwardRules) {
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

    private List<String> getDestinationAETsFromTemplate(Templates template) throws TransformerFactoryConfigurationError,
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
            asCalled.setProperty(FORWARD_ASSOCIATION, as);
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
            LOG.error("Unable to load configuration for destination AET: " + e.getMessage());
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
            LOG.debug("Unexpected exception", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException e) {
            return handleNegotiateConnectException(as, rq, ac, forwardRule.getDestinationURI(), e, ".conf", 0);
        } catch (GeneralSecurityException e) {
            LOG.error("Failed to create SSL context", e);
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
        LOG.debug(as + ": unable to connect to {} ({})", new Object[]{destinationAETitle, e.getMessage()});
        if (acceptDataOnFailedNegotiation) {
            as.setProperty(FILE_SUFFIX, suffix);
            return super.negotiate(as, rq, ac);
        }
        throw new AAbort(AAbort.UL_SERIVE_PROVIDER, reason);
    }

    @Override
    protected void onClose(Association asAccepted) {
        super.onClose(asAccepted);
        Association[] asInvoked;
        Object forwardAssociationProperty = asAccepted.getProperty(FORWARD_ASSOCIATION);
        if (forwardAssociationProperty == null)
            return;

        if (forwardAssociationProperty instanceof Association) {
            ArrayList<Association> list = new ArrayList<Association>(1);
            list.add((Association) forwardAssociationProperty);
            asInvoked = list.toArray(new Association[1]);
        } else {
            @SuppressWarnings("unchecked")
            HashMap<String, Association> fwdAssocs = (HashMap<String, Association>) forwardAssociationProperty;
            asInvoked = fwdAssocs.values().toArray(new Association[fwdAssocs.size()]);
        }
        for (Association as : asInvoked) {
            if (as != null && as.isRequestor())
                try {
                    as.release();
                } catch (IOException e) {
                    LOG.debug("Failed to release {} ({})", new Object[] { as, e });
                }
        }
    }

    public void coerceDataset(String remoteAET, Role role, Dimse dimse, Attributes attrs) throws IOException {
        AttributeCoercion ac = getAttributeCoercion(remoteAET, attrs.getString(dimse.tagOfSOPClassUID()), role, dimse);
        if (ac != null)
            coerceAttributes(attrs, ac);
    }

    public void coerceAttributes(Attributes attrs, AttributeCoercion ac) {
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

    public void createStartLogFile(final Association as, final Attributes attrs)
            throws IOException {
        File file = new File(getLogDir(as, attrs), "start.log");
        
        if (!file.exists()) {
            Properties prop = new Properties();
            prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
            prop.store(new FileOutputStream(file), null);
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
            LOG.debug(as + ": failed to create log file: " + e.getMessage());
            throw new IOException(e);
        }
    }

    private File getLogDir(Association as, Attributes attrs) {
        File path = new File(getAuditDirectoryPath().getPath() + getSeparator() + as.getCalledAET()
                + getSeparator() + as.getCallingAET() + getSeparator() + attrs.getString(Tag.StudyInstanceUID));
        path.mkdirs();
        return path;
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
    
    public HashMap<String, String> filterForwardAETs(Association asAccepted, Attributes rq, Dimse dimse) {
        List<ForwardRule> forwardRules = filterForwardRulesOnDimseRQ(asAccepted, rq, dimse);
        return getAETsFromForwardRules(asAccepted, forwardRules);
    }
    
    public HashMap<String, Association> openForwardAssociations(Association asAccepted, Attributes rq, Dimse dimse,
            HashMap<String, String> aets) {
        HashMap<String, Association> fwdAssocs = new HashMap<String, Association>(aets.size());
        for (Entry<String, String> entry : aets.entrySet()) {
            String callingAET = entry.getValue();
            String calledAET = entry.getKey();
            Association asInvoked = openForwardAssociation(asAccepted, callingAET, calledAET, asAccepted.getAAssociateRQ());
            fwdAssocs.put(calledAET, asInvoked);
        }
        return fwdAssocs;
    }
    
    public Association openForwardAssociation(Association asAccepted, String callingAET, String calledAET, 
            AAssociateRQ rq) {
        rq.setCallingAET(callingAET);
        rq.setCalledAET(calledAET);
        Association asInvoked = null;
        try {
            asInvoked = connect(getDestinationAE(calledAET), rq);
            asInvoked.setProperty(FORWARD_ASSOCIATION, asAccepted);
        } catch (IncompatibleConnectionException e) {
            LOG.error("Unable to connect to {} ({})", new Object[] { calledAET, e.getMessage() });
        } catch (GeneralSecurityException e) {
            LOG.error("Failed to create SSL context ({})", new Object[] { e.getMessage() });
        } catch (ConfigurationException e) {
            LOG.error("Unable to load configuration for destination AET ({})", new Object[] { e.getMessage() });
        } catch (ConnectException e) {
            LOG.error("Unable to connect to {} ({})", new Object[] { calledAET, e.getMessage() });
        } catch (Exception e) {
            LOG.debug("Unexpected exception: " + e.getMessage());
        }
        return asInvoked;
    }
}