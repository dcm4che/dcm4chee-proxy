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

package org.dcm4chee.proxy.dimse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.AuditMessages.EventActionCode;
import org.dcm4che3.audit.AuditMessages.EventID;
import org.dcm4che3.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che3.audit.ParticipantObjectDescription;
import org.dcm4che3.audit.SOPClass;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IOD;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.ValidationResult;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.AssociationStateException;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.utils.ForwardConnectionUtils;
import org.dcm4chee.proxy.utils.ForwardRuleUtils;
import org.dcm4chee.proxy.utils.InfoFileUtils;
import org.jboss.resteasy.util.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class Mpps extends AbstractDicomService {

    protected static final Logger LOG = LoggerFactory.getLogger(Mpps.class);
    private static AuditLogger logger;

    public Mpps(AuditLogger logger) {
        super(UID.ModalityPerformedProcedureStepSOPClass);
        Mpps.logger = logger;
    }

    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes data)
            throws IOException {
        switch (dimse) {
        case N_CREATE_RQ:
            onNCreateRQ(asAccepted, pc, dimse, cmd, data);
            break;
        case N_SET_RQ:
            onNSetRQ(asAccepted, pc, dimse, cmd, data);
            break;
        default:
            throw new DicomServiceException(Status.UnrecognizedOperation);
        }
    }

    private void onNCreateRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd,
            Attributes data) throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (asInvoked == null)
            try {
                processForwardRules(asAccepted, pc, dimse, cmd, data);
            } catch (ConfigurationException e) {
                LOG.error("{}: error processing {}: {}", new Object[] { asAccepted, dimse, e.getMessage() });
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
                throw new DicomServiceException(Status.ProcessingFailure, e.getMessage());
            }
        else
            try {
                forwardNCreateRQ(asAccepted, asInvoked, pc, dimse, cmd, data);
            } catch (InterruptedException e) {
                LOG.error("{}: error processing {}: {}", new Object[] { asAccepted, dimse, e.getMessage() });
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
                throw new DicomServiceException(Status.ProcessingFailure, e.getCause());
            }
    }

    private void processForwardRules(Association as, PresentationContext pc, Dimse dimse, Attributes cmd,
            Attributes data) throws ConfigurationException, IOException {
        ApplicationEntity ae = as.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        List<ForwardRule> forwardRules = ForwardRuleUtils.filterForwardRulesOnDimseRQ(proxyAEE.getCurrentForwardRules(as),
                cmd.getString(dimse.tagOfSOPClassUID()), dimse);
        if (forwardRules.size() == 0)
            throw new ConfigurationException("no matching forward rule");

        Attributes rsp = (dimse == Dimse.N_CREATE_RQ) 
                ? Commands.mkNCreateRSP(cmd, Status.Success) 
                : Commands.mkNSetRSP(cmd, Status.Success);
        String iuid = rsp.getString(Tag.AffectedSOPInstanceUID);
        String cuid = rsp.getString(Tag.AffectedSOPClassUID);
        String tsuid = UID.ExplicitVRLittleEndian;
        Attributes fmi = Attributes.createFileMetaInformation(iuid, cuid, tsuid);
        for (ForwardRule rule : forwardRules) {
            List<String> destinationAETs = ForwardRuleUtils.getDestinationAETsFromForwardRule(as, rule, data);
            processDestinationAETs(as, dimse, fmi, data, proxyAEE, iuid, rule, destinationAETs);
        }
        try {
            as.writeDimseRSP(pc, rsp, data);
        } catch (AssociationStateException e) {
            Dimse dimseRSP = (dimse == Dimse.N_CREATE_RQ) ? Dimse.N_CREATE_RSP : Dimse.N_SET_RSP;
            LOG.warn("{} << {} failed: {}", new Object[] { as, dimseRSP.toString(), e.getMessage() });
            if(LOG.isDebugEnabled())
                e.printStackTrace();
        }
    }

    private void processDestinationAETs(Association as, Dimse dimse, Attributes fmi, Attributes data,
            ProxyAEExtension pae, String iuid, ForwardRule rule, List<String> destinationAETs)
            throws TransformerFactoryConfigurationError, IOException {
        for (String calledAET : destinationAETs) {
            if (rule.getMpps2DoseSrTemplateURI() != null) {
                File dir = pae.getDoseSrPath();
                processMpps2DoseSRConversion(as, dimse, fmi, data, iuid, dir, calledAET, rule);
            } else {
                File dir = (dimse == Dimse.N_CREATE_RQ) ? pae.getNCreateDirectoryPath() : pae.getNSetDirectoryPath();
                File file = createFile(as, fmi, data, dir, calledAET, rule);
                as.setProperty(ProxyAEExtension.FILE_SUFFIX, ".dcm");
                rename(as, file);
            }
        }
    }

    private void processMpps2DoseSRConversion(Association as, Dimse dimse, Attributes fmi, Attributes data, String iuid,
            File baseDir, String calledAET, ForwardRule rule) throws TransformerFactoryConfigurationError, IOException {
        if (dimse == Dimse.N_CREATE_RQ) {
            File file = createFile(as, fmi, data, baseDir, calledAET, rule);
            as.setProperty(ProxyAEExtension.FILE_SUFFIX, ".ncreate");
            rename(as, file);
        } else
            processNSetMpps2DoseSR(as, dimse, fmi, data, iuid, baseDir, calledAET, rule);
    }

    private void processNSetMpps2DoseSR(Association as, Dimse dimse, Attributes fmi, Attributes data, String iuid,
            File baseDir, String calledAET, ForwardRule rule) throws TransformerFactoryConfigurationError, IOException {
        ApplicationEntity ae = as.getApplicationEntity();
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        String ppsSOPIUID = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        File ncreateFile = getNCreateFile(proxyAEE, iuid);
        if (ncreateFile == null) {
            LOG.error("{}: unable to find matching N-CREATE object for MediaStorageSOPInstanceUID {}", new Object[] {
                    as, ppsSOPIUID });
            throw new DicomServiceException(Status.ProcessingFailure);
        }
        Attributes ncreateAttrs = proxyAEE.parseAttributesWithLazyBulkData(as, ncreateFile);
        Attributes mergedAttrs = new Attributes(data);
        mergedAttrs.merge(ncreateAttrs);
        Properties prop = InfoFileUtils.getFileInfoProperties(proxyAEE, ncreateFile);
        Calendar timeStamp = new GregorianCalendar();
        String patientID = ncreateAttrs.getString(Tag.PatientID);
        String doseIuid = UIDUtils.createUID();
        Attributes doseSrData = transformMpps2DoseSr(as, proxyAEE, mergedAttrs, ppsSOPIUID, iuid, rule, prop,
                timeStamp, patientID, fmi, doseIuid);
        if (doseSrData == null)
            return;

        String cuid = UID.XRayRadiationDoseSRStorage;
        String tsuid = UID.ImplicitVRLittleEndian;
        Attributes doseSrFmi = Attributes.createFileMetaInformation(doseIuid, cuid, tsuid);
        File doseSrFile = createFile(as, doseSrFmi, doseSrData, proxyAEE.getCStoreDirectoryPath(), calledAET, rule);
        LOG.info("{}: created Dose SR file {}", as, doseSrFile.getPath());
        as.setProperty(ProxyAEExtension.FILE_SUFFIX, ".dcm");
        rename(as, doseSrFile);
        AuditMessage msg = createAuditMessage(
                proxyAEE.getApplicationEntity(), 
                timeStamp,
                EventOutcomeIndicator.Success,
                prop.getProperty("hostname"),
                patientID,
                doseSrData.getString(Tag.StudyInstanceUID));
        writeAuditLogMessage(as, msg, timeStamp);
        deleteFile(as, ncreateFile);
    }

    private File getNCreateFile(ProxyAEExtension proxyAEE, String iuid) throws IOException {
        for (String calledAET : proxyAEE.getDoseSrPath().list()) {
            File calledAETPath = new File (proxyAEE.getDoseSrPath(), calledAET);
            for (File file : calledAETPath.listFiles(InfoFileUtils.infoFileFilter())) {
                Properties prop = InfoFileUtils.getFileInfoProperties(proxyAEE, file);
                String fileInstanceUID = prop.getProperty("sop-instance-uid");
                if (fileInstanceUID.equals(iuid))
                    return new File(file.getParent(),file.getName().substring(0, file.getName().lastIndexOf('.')) + ".ncreate");
            }
        }
        return null;
    }

    private void deleteFile(Association as, File file) {
        if(file.delete())
            LOG.debug("{}: DELETE {}", as, file.getPath());
        else
            LOG.error("{}: failed to DELETE {}", as, file.getPath());
        File info = new File(file.getParent(),file.getName().substring(0, file.getName().indexOf('.')) + ".info");
        if (info.delete())
            LOG.debug("{}: DELETE {}", as, info);
        else
            LOG.debug("{}: failed to DELETE {}", as, info);
    }

    private Attributes transformMpps2DoseSr(Association as, ProxyAEExtension proxyAEE, Attributes data,
            String ppsSOPIUID, String iuid, ForwardRule rule, Properties prop, Calendar timeStamp, String patientID,
            Attributes fmi, String doseIuid) throws TransformerFactoryConfigurationError, IOException {
        Attributes doseSrData = new Attributes();
        try {
            Templates templates = proxyAEE.getApplicationEntity().getDevice().getDeviceExtension(ProxyDeviceExtension.class)
                    .getTemplates(rule.getMpps2DoseSrTemplateURI());
            SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory.newInstance();
            TransformerHandler th = factory.newTransformerHandler(templates);
            Transformer tr = th.getTransformer();
            String irradiationEventUID = new String(iuid).concat("1");
            tr.setParameter("IrradiationEventUID", irradiationEventUID);
            String hex = Hex.encodeHex(as.getCallingAET().getBytes());
            BigInteger bi = new BigInteger(hex, 16);
            tr.setParameter("DeviceObserverUID", bi);
            tr.setParameter("PerfomedProcedureStepSOPInstanceUID", ppsSOPIUID);
            th.setResult(new SAXResult(new ContentHandlerAdapter(doseSrData)));
            SAXWriter w = new SAXWriter(th);
            w.setIncludeKeyword(false);
            w.write(data);
            doseSrData.setString(Tag.SOPInstanceUID, VR.UI, doseIuid);
            doseSrData.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
            validateDoseSR(as, rule, doseSrData, irradiationEventUID);
            if (LOG.isDebugEnabled())
                LOG.debug("{}: MPPS to Dose SR transformed dataset:{}{}", new Object[] { as, System.lineSeparator(),
                        doseSrData.toString(Integer.MAX_VALUE, 200) });
            return doseSrData;
        } catch (Exception e) {
            LOG.error("{}: error converting MPPS to Dose SR: {}", as, e.getMessage());
            if(LOG.isDebugEnabled())
                e.printStackTrace();
            Sequence seq = data.getSequence(Tag.ScheduledStepAttributesSequence);
            String studyIUID = seq.get(0).getString(Tag.StudyInstanceUID);
            AuditMessage msg = createAuditMessage(
                    proxyAEE.getApplicationEntity(), 
                    timeStamp,
                    EventOutcomeIndicator.SeriousFailure,
                    prop.getProperty("hostname"),
                    patientID,
                    studyIUID);
            writeAuditLogMessage(as, msg, timeStamp);
            if (!doseSrData.isEmpty())
                storeFailedMPPS(as, fmi, doseSrData, proxyAEE.getDoseSrPath());
            return null;
        } 
    }

    private void validateDoseSR(Association as, ForwardRule rule, Attributes doseSrData, String irradiationEventUID)
            throws Exception {
        if (rule.getDoseSrIODTemplateURI() == null)
            return;

        String iodTemplateURI = StringUtils.replaceSystemProperties(rule.getDoseSrIODTemplateURI()).replace('\\', '/');
        IOD doseSrIOD = IOD.load(iodTemplateURI);
        ValidationResult result = doseSrData.validate(doseSrIOD);
        if (result.isValid())
            LOG.info("{}: Successfully converted and validated MPPS to Dose SR for IrradiationEventUID {}", as,
                    irradiationEventUID);
        else {
            LOG.error("{}: validation of generated Dose SR failed: {}", as, result.asText(doseSrData));
            throw new Exception("invalid Dose SR");
        }
    }

    private void storeFailedMPPS(Association as, Attributes fmi, Attributes data, File path) throws IOException {
        File dir = new File(path, as.getCallingAET());
        dir.mkdir();
        File file = File.createTempFile("dcm", ".failed", dir);
        DicomOutputStream out = null;
        try {
            LOG.debug("{}: create {}", new Object[] { as, file });
            out = new DicomOutputStream(file);
            out.writeDataset(fmi, data);
        } catch (IOException e) {
            LOG.warn("{}: failed to create {}", new Object[] { as, file.getPath() });
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            file.delete();
            throw new DicomServiceException(Status.OutOfResources, e.getCause());
        } finally {
            out.close();
        }
    }

    protected File createFile(Association as, Attributes fmi, Attributes data, File baseDir, String aet,
            ForwardRule rule) throws IOException {
        File dir = new File(baseDir, aet);
        dir.mkdir();
        File file = File.createTempFile("dcm", ".part", dir);
        DicomOutputStream out = null;
        try {
            LOG.debug("{}: create {}", new Object[] { as, file });
            out = new DicomOutputStream(file);
            out.writeDataset(fmi, data);
        } catch (IOException e) {
            LOG.warn("{}: failed to create {}", new Object[] { as, file.getPath() });
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            file.delete();
            throw new DicomServiceException(Status.OutOfResources, e.getCause());
        } finally {
            out.close();
        }
        Properties prop = new Properties();
        prop.setProperty("source-aet", as.getCallingAET());
        prop.setProperty("sop-instance-uid", fmi.getString(Tag.MediaStorageSOPInstanceUID));
        prop.setProperty("sop-class-uid", fmi.getString(Tag.MediaStorageSOPClassUID));
        prop.setProperty("transfer-syntax-uid", fmi.getString(Tag.TransferSyntaxUID));
        prop.setProperty("hostname", as.getConnection().getHostname());
        if (rule.getUseCallingAET() != null)
            prop.setProperty("use-calling-aet", rule.getUseCallingAET());
        String path = file.getPath();
        File info = new File(path.substring(0, path.length() - 5) + ".info");
        FileOutputStream infoOut = new FileOutputStream(info);
        try {
            LOG.debug("{}: create {}", as, info.getPath());
            prop.store(infoOut, null);
        } catch (IOException e) {
            LOG.warn("{}: failed to create {}", new Object[] { as, info.getPath() });
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            file.delete();
            info.delete();
            throw new DicomServiceException(Status.OutOfResources, e.getCause());
        } finally {
            infoOut.close();
        }
        return file;
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
            LOG.warn("{}: failed to RENAME {} to {}", new Object[] { as, file, dst });
            throw new DicomServiceException(Status.OutOfResources, "Failed to rename file");
        }
    }

    private void forwardNCreateRQ(final Association asAccepted, Association asInvoked, final PresentationContext pc,
            Dimse dimse, Attributes rq, Attributes data) throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.error(asAccepted + ": error forwarding N-CREATE-RQ: " + e.getMessage());
                    if(LOG.isDebugEnabled())
                        e.printStackTrace();
                }
            }
        };
        asInvoked.ncreate(cuid, iuid, data, ForwardConnectionUtils.getMatchingTsuid(asInvoked, tsuid, cuid), rspHandler);
    }

    private void onNSetRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd, Attributes data)
            throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyAEExtension.FORWARD_ASSOCIATION);
        if (asInvoked == null)
            try {
                processForwardRules(asAccepted, pc, dimse, cmd, data);
            } catch (ConfigurationException e) {
                LOG.error("{}: error processing {}: {}", new Object[] { asAccepted, dimse, e.getMessage() });
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
            }
        else
            try {
                forwardNSetRQ(asAccepted, asInvoked, pc, dimse, cmd, data);
            } catch (InterruptedException e) {
                throw new DicomServiceException(Status.UnableToProcess, e.getMessage());
            }
    }

    private void forwardNSetRQ(final Association asAccepted, Association asInvoked, final PresentationContext pc,
            Dimse dimse, Attributes rq, Attributes data) throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.RequestedSOPClassUID);
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.error(asAccepted + ": error forwarding N-SET-RQ: ", e.getMessage());
                    if(LOG.isDebugEnabled())
                        e.printStackTrace();
                }
            }
        };
        asInvoked.nset(cuid, iuid, data, ForwardConnectionUtils.getMatchingTsuid(asInvoked, tsuid, cuid), rspHandler);
    }

    private AuditMessage createAuditMessage(ApplicationEntity ae, Calendar timeStamp, String eventOutcomeIndicator,
            String proxyHostname, String patientID, String studyIUID) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                EventID.DICOMInstancesAccessed, 
                EventActionCode.Create, 
                timeStamp, 
                eventOutcomeIndicator, 
                null));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                proxyHostname, 
                AuditMessages.alternativeUserIDForAETitle(ae.getAETitle()),
                null, 
                false, 
                null, 
                null, 
                null, 
                AuditMessages.RoleIDCode.Application));
        ParticipantObjectDescription pod = new ParticipantObjectDescription();
        SOPClass sc = new SOPClass();
        sc.setUID(UID.XRayRadiationDoseSRStorage);
        sc.setNumberOfInstances(1);
        pod.getSOPClass().add(sc);
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                studyIUID, 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, 
                null, 
                null, 
                AuditMessages.ParticipantObjectTypeCode.SystemObject, 
                AuditMessages.ParticipantObjectTypeCodeRole.Report, 
                null, 
                null, 
                pod));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                patientID == null ? "<UNKNOWN>" : patientID,
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                null,
                null,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null,
                null,
                null));
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        return msg;
    }

    private void writeAuditLogMessage(Association as, AuditMessage msg, Calendar timeStamp) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("AuditMessage: " + AuditMessages.toXML(msg));
            logger.write(timeStamp, msg);
        } catch (Exception e) {
            LOG.error(as + ": error writing audit log message: " + e.getMessage());
            if (LOG.isDebugEnabled())
                e.printStackTrace();
        }
    }
}
