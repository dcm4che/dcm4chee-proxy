/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contentsOfthis file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copyOfthe License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is partOfdcm4che, an implementationOfDICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial DeveloperOfthe Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contentsOfthis file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisionsOfthe GPL or the LGPL are applicable instead
 *Ofthose above. If you wish to allow useOfyour versionOfthis file only
 * under the termsOfeither the GPL or the LGPL, and not to allow others to
 * use your versionOfthis file under the termsOfthe MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your versionOfthis file under
 * the termsOfany oneOfthe MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.proxy.conf;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.ldap.LdapDicomConfiguration;
import org.dcm4che.conf.ldap.hl7.LdapHL7Configuration;
import org.dcm4che.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che.conf.prefs.hl7.PreferencesHL7Configuration;
import org.dcm4che.data.Code;
import org.dcm4che.data.Issuer;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Connection.Protocol;
import org.dcm4che.net.Device;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.QueryOption;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.hl7.HL7Application;
import org.dcm4che.net.hl7.HL7DeviceExtension;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.RetryObject;
import org.dcm4chee.proxy.conf.ldap.LdapProxyConfigurationExtension;
import org.dcm4chee.proxy.conf.prefs.PreferencesProxyConfigurationExtension;
import org.junit.Before;
import org.junit.Test;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyDeviceTest {

    private static final String[] OTHER_DEVICES = {
        "dcm4chee-arc",
        "storescp",
        "storescu",
        "findscu",
    };
    
    private static final String[] OTHER_AES = {
        "DCM4CHEE",
        "STORESCP",
        "STORESCU",
        "FINDSCU",
    };

    private static final String PIX_CONSUMER = "HL7SND^DCM4CHEE-PROXY";
    private static final String PIX_MANAGER = "HL7RCV^DCM4CHEE";

    private static final Issuer SITE_A =
            new Issuer("Site A", "1.2.40.0.13.1.1.999.111.1111", "ISO");

    private static final Code INST_A =
            new Code("111.1111", "99DCM4CHEE", null, "Site A");

    private static final String[] IMAGE_TSUIDS = {
        UID.ImplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
        UID.JPEGBaseline1,
        UID.JPEGExtended24,
        UID.JPEGLossless,
        UID.JPEGLosslessNonHierarchical14,
        UID.JPEGLSLossless,
        UID.JPEGLSLossyNearLossless,
        UID.JPEG2000LosslessOnly,
        UID.JPEG2000,
        UID.RLELossless
    };
    private static final String[] VIDEO_TSUIDS = {
        UID.JPEGBaseline1,
        UID.MPEG2,
        UID.MPEG2MainProfileHighLevel,
        UID.MPEG4AVCH264BDCompatibleHighProfileLevel41,
        UID.MPEG4AVCH264HighProfileLevel41
    };
    private static final String[] OTHER_TSUIDS = {
        UID.ImplicitVRLittleEndian,
        UID.ExplicitVRLittleEndian,
        UID.DeflatedExplicitVRLittleEndian,
        UID.ExplicitVRBigEndian,
    };
    private static final String[] IMAGE_CUIDS = {
        UID.ComputedRadiographyImageStorage,
        UID.DigitalXRayImageStorageForPresentation,
        UID.DigitalXRayImageStorageForProcessing,
        UID.DigitalMammographyXRayImageStorageForPresentation,
        UID.DigitalMammographyXRayImageStorageForProcessing,
        UID.DigitalIntraOralXRayImageStorageForPresentation,
        UID.DigitalIntraOralXRayImageStorageForProcessing,
        UID.CTImageStorage,
        UID.EnhancedCTImageStorage,
        UID.UltrasoundMultiFrameImageStorageRetired,
        UID.UltrasoundMultiFrameImageStorage,
        UID.MRImageStorage,
        UID.EnhancedMRImageStorage,
        UID.EnhancedMRColorImageStorage,
        UID.NuclearMedicineImageStorageRetired,
        UID.UltrasoundImageStorageRetired,
        UID.UltrasoundImageStorage,
        UID.EnhancedUSVolumeStorage,
        UID.SecondaryCaptureImageStorage,
        UID.MultiFrameGrayscaleByteSecondaryCaptureImageStorage,
        UID.MultiFrameGrayscaleWordSecondaryCaptureImageStorage,
        UID.MultiFrameTrueColorSecondaryCaptureImageStorage,
        UID.XRayAngiographicImageStorage,
        UID.EnhancedXAImageStorage,
        UID.XRayRadiofluoroscopicImageStorage,
        UID.EnhancedXRFImageStorage,
        UID.XRayAngiographicBiPlaneImageStorageRetired,
        UID.XRay3DAngiographicImageStorage,
        UID.XRay3DCraniofacialImageStorage,
        UID.BreastTomosynthesisImageStorage,
        UID.IntravascularOpticalCoherenceTomographyImageStorageForPresentation,
        UID.IntravascularOpticalCoherenceTomographyImageStorageForProcessing,
        UID.NuclearMedicineImageStorage,
        UID.VLEndoscopicImageStorage,
        UID.VLMicroscopicImageStorage,
        UID.VLSlideCoordinatesMicroscopicImageStorage,
        UID.VLPhotographicImageStorage,
        UID.OphthalmicPhotography8BitImageStorage,
        UID.OphthalmicPhotography16BitImageStorage,
        UID.OphthalmicTomographyImageStorage,
        UID.VLWholeSlideMicroscopyImageStorage,
        UID.PositronEmissionTomographyImageStorage,
        UID.EnhancedPETImageStorage,
        UID.RTImageStorage,
    };
    private static final String[] VIDEO_CUIDS = {
        UID.VideoEndoscopicImageStorage,
        UID.VideoMicroscopicImageStorage,
        UID.VideoPhotographicImageStorage,
    };
    private static final String[] OTHER_CUIDS = {
        UID.MRSpectroscopyStorage,
        UID.MultiFrameSingleBitSecondaryCaptureImageStorage,
        UID.StandaloneOverlayStorageRetired,
        UID.StandaloneCurveStorageRetired,
        UID.TwelveLeadECGWaveformStorage,
        UID.GeneralECGWaveformStorage,
        UID.AmbulatoryECGWaveformStorage,
        UID.HemodynamicWaveformStorage,
        UID.CardiacElectrophysiologyWaveformStorage,
        UID.BasicVoiceAudioWaveformStorage,
        UID.GeneralAudioWaveformStorage,
        UID.ArterialPulseWaveformStorage,
        UID.RespiratoryWaveformStorage,
        UID.StandaloneModalityLUTStorageRetired,
        UID.StandaloneVOILUTStorageRetired,
        UID.GrayscaleSoftcopyPresentationStateStorageSOPClass,
        UID.ColorSoftcopyPresentationStateStorageSOPClass,
        UID.PseudoColorSoftcopyPresentationStateStorageSOPClass,
        UID.BlendingSoftcopyPresentationStateStorageSOPClass,
        UID.XAXRFGrayscaleSoftcopyPresentationStateStorage,
        UID.RawDataStorage,
        UID.SpatialRegistrationStorage,
        UID.SpatialFiducialsStorage,
        UID.DeformableSpatialRegistrationStorage,
        UID.SegmentationStorage,
        UID.SurfaceSegmentationStorage,
        UID.RealWorldValueMappingStorage,
        UID.StereometricRelationshipStorage,
        UID.LensometryMeasurementsStorage,
        UID.AutorefractionMeasurementsStorage,
        UID.KeratometryMeasurementsStorage,
        UID.SubjectiveRefractionMeasurementsStorage,
        UID.VisualAcuityMeasurementsStorage,
        UID.SpectaclePrescriptionReportStorage,
        UID.OphthalmicAxialMeasurementsStorage,
        UID.IntraocularLensCalculationsStorage,
        UID.MacularGridThicknessAndVolumeReportStorage,
        UID.OphthalmicVisualFieldStaticPerimetryMeasurementsStorage,
        UID.BasicStructuredDisplayStorage,
        UID.BasicTextSRStorage,
        UID.EnhancedSRStorage,
        UID.ComprehensiveSRStorage,
        UID.ProcedureLogStorage,
        UID.MammographyCADSRStorage,
        UID.KeyObjectSelectionDocumentStorage,
        UID.ChestCADSRStorage,
        UID.XRayRadiationDoseSRStorage,
        UID.ColonCADSRStorage,
        UID.ImplantationPlanSRStorage,
        UID.EncapsulatedPDFStorage,
        UID.EncapsulatedCDAStorage,
        UID.StandalonePETCurveStorageRetired,
        UID.RTDoseStorage,
        UID.RTStructureSetStorage,
        UID.RTBeamsTreatmentRecordStorage,
        UID.RTPlanStorage,
        UID.RTBrachyTreatmentRecordStorage,
        UID.RTTreatmentSummaryRecordStorage,
        UID.RTIonPlanStorage,
        UID.RTIonBeamsTreatmentRecordStorage,
        UID.StorageCommitmentPushModelSOPClass,
        UID.ModalityPerformedProcedureStepSOPClass,
    };

    private static final String[] QUERY_CUIDS = {
        UID.PatientRootQueryRetrieveInformationModelFIND,
        UID.StudyRootQueryRetrieveInformationModelFIND,
        UID.PatientStudyOnlyQueryRetrieveInformationModelFINDRetired,
        UID.ModalityWorklistInformationModelFIND,
        UID.PatientRootQueryRetrieveInformationModelGET,
        UID.StudyRootQueryRetrieveInformationModelGET,
        UID.PatientStudyOnlyQueryRetrieveInformationModelGETRetired,
        UID.PatientRootQueryRetrieveInformationModelMOVE,
        UID.StudyRootQueryRetrieveInformationModelMOVE,
        UID.PatientStudyOnlyQueryRetrieveInformationModelMOVERetired
    };

    private static final String[] HL7_MESSAGE_TYPES = {
        "ADT^A02",
        "ADT^A03",
        "ADT^A06",
        "ADT^A07",
        "ADT^A08",
        "ADT^A40",
        "ORM^O01"
    };

    private DicomConfiguration config;

    @Before
    public void setUp() throws Exception {
        config = System.getProperty("ldap") == null
                ? newPreferencesProxyConfiguration()
                : newLdapProxyConfiguration();
        cleanUp();
    }

    private DicomConfiguration newLdapProxyConfiguration()
            throws ConfigurationException {
        LdapDicomConfiguration config = new LdapDicomConfiguration();
        LdapHL7Configuration hl7Config = new LdapHL7Configuration();
        config.addDicomConfigurationExtension(hl7Config);
        LdapProxyConfigurationExtension proxyConfig = new LdapProxyConfigurationExtension();
        config.addDicomConfigurationExtension(proxyConfig);
//        config.addDicomConfigurationExtension(
//                new LdapAuditLoggerConfiguration());
        return config;
    }

    private DicomConfiguration newPreferencesProxyConfiguration() {
        PreferencesDicomConfiguration config = new PreferencesDicomConfiguration();
        PreferencesHL7Configuration hl7Config = new PreferencesHL7Configuration();
        config.addDicomConfigurationExtension(hl7Config);
        PreferencesProxyConfigurationExtension proxyConfig = new PreferencesProxyConfigurationExtension();
        config.addDicomConfigurationExtension(proxyConfig);
//        config.addDicomConfigurationExtension(
//                new PreferencesAuditLoggerConfiguration());
        return config;
    }
    
    private void cleanUp() throws Exception {
        config.unregisterAETitle("DCM4CHEE-PROXY");
        for (String aet : OTHER_AES)
            config.unregisterAETitle(aet);
        try {
            config.removeDevice("dcm4chee-proxy");
        } catch (ConfigurationNotFoundException e) {}
//        try {
//            config.removeDevice("syslog");
//        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("hl7rcv");
        } catch (ConfigurationNotFoundException e) {}
        for (String name : OTHER_DEVICES)
            try {
                config.removeDevice(name);
            }  catch (ConfigurationNotFoundException e) {}
    }
    
    public static void cleanUp(DicomConfiguration config) throws ConfigurationException {
        config.unregisterAETitle("DCM4CHEE-PROXY");
        for (String aet : OTHER_AES)
            config.unregisterAETitle(aet);

        try {
            config.removeDevice("dcm4chee-proxy");
        }  catch (ConfigurationNotFoundException e) {}
//        try {
//            config.removeDevice("syslog");
//        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("hl7rcv");
        } catch (ConfigurationNotFoundException e) {}
        for (String name : OTHER_DEVICES)
            try {
                config.removeDevice(name);
            }  catch (ConfigurationNotFoundException e) {}
    }
    
    @Test
    public void test() throws Exception {
        for (int i = 0; i < OTHER_AES.length; i++) {
            String aet = OTHER_AES[i];
            config.registerAETitle(aet);
            config.persist(createDevice(config, OTHER_DEVICES[i], aet, "localhost", 11112 + i, 2762 + i));
        }
        for (int i = OTHER_AES.length; i < OTHER_DEVICES.length; i++)
            config.persist(createDevice(config, OTHER_DEVICES[i]));
        config.persist(createHL7Device("hl7rcv", SITE_A, INST_A, PIX_MANAGER, "localhost", 2576, 12576));
        config.registerAETitle("DCM4CHEE-PROXY");
        config.persist(createProxyDevice("dcm4chee-proxy"));
        config.findApplicationEntity("DCM4CHEE-PROXY");
        if (config instanceof PreferencesDicomConfiguration)
            export(System.getProperty("export"));
    }

    private void export(String name) throws Exception {
        if (name == null)
            return;

        OutputStream os = new FileOutputStream(name);
        try {
            ((PreferencesDicomConfiguration) config)
                    .getDicomConfigurationRoot().exportSubtree(os);
        } finally {
            SafeClose.close(os);
        }
    }

    private Device createProxyDevice(String name) throws Exception {
        Device device = new Device(name);
        HL7DeviceExtension hl7DevExt = new HL7DeviceExtension();
        device.addDeviceExtension(hl7DevExt);
        ProxyDeviceExtension proxyDev = new ProxyDeviceExtension();
        device.addDeviceExtension(proxyDev);
        
        proxyDev.setSchedulerInterval(10);
        proxyDev.setForwardThreads(1);
        proxyDev.setConfigurationStaleTimeout(60);
        
        ApplicationEntity ae = new ApplicationEntity("DCM4CHEE-PROXY");
        ProxyAEExtension proxyAEE = new ProxyAEExtension();
        ae.addAEExtension(proxyAEE);
        ae.setAssociationAcceptor(true);
        ae.setAssociationInitiator(true);
        proxyAEE.setSpoolDirectory("/tmp/proxy/");
        proxyAEE.setAcceptDataOnFailedAssociation(true);
        proxyAEE.setEnableAuditLog(true);
        proxyAEE.setDeleteFailedDataWithoutRetryConfiguration(true);
        proxyAEE.setFallbackDestinationAET("DCM4CHEE");
        
        proxyAEE.addAttributeCoercion(new AttributeCoercion(null, 
                Dimse.C_STORE_RQ, 
                TransferCapability.Role.SCP,
                "ENSURE_PID",
                "file:${jboss.server.config.dir}/dcm4chee-proxy/dcm4chee-proxy-ensure-pid.xsl"));
        
        proxyAEE.addAttributeCoercion(new AttributeCoercion(null, 
                Dimse.C_STORE_RQ, 
                TransferCapability.Role.SCU,
                "WITHOUT_PN",
                "file:${jboss.server.config.dir}/dcm4chee-proxy/dcm4chee-proxy-nullify-pn.xsl"));
        
        HashMap<String, ForwardOption> fwdOptions = new HashMap<String, ForwardOption>();
        
        ForwardOption fwdOptionStoreScp = new ForwardOption();
        fwdOptionStoreScp.setDestinationAET("STORESCP");
        fwdOptionStoreScp.setDescription("Example ForwardOption for STORESCP");
        fwdOptionStoreScp.setConvertEmf2Sf(true);
        Schedule scheduleStoreScp = new Schedule();
        scheduleStoreScp.setDays("Wed");
        scheduleStoreScp.setHours("8-18");
        fwdOptionStoreScp.setSchedule(scheduleStoreScp);
        fwdOptions.put("STORESCP", fwdOptionStoreScp);
        
        proxyAEE.setForwardOptions(fwdOptions);
        
        List<ForwardRule> forwardRules = new ArrayList<ForwardRule>();
        
        ForwardRule forwardRulePublic = new ForwardRule();
        forwardRulePublic.setCommonName("Public");
        List<String> destinationURIPublic = new ArrayList<String>();
        destinationURIPublic.add("aet:DCM4CHEE");
        forwardRulePublic.setDestinationURIs(destinationURIPublic);
        Schedule receiveSchedulePublic = new Schedule();
        receiveSchedulePublic.setDays("Sun-Tue,Thu-Sat");
        forwardRulePublic.setReceiveSchedule(receiveSchedulePublic);
        forwardRulePublic.setRunPIXQuery(Boolean.TRUE);
        forwardRulePublic.setDescription("Example ForwardRule");
        forwardRules.add(forwardRulePublic);
        
        ForwardRule forwardRuleMPPS2DoseSR = new ForwardRule();
        forwardRuleMPPS2DoseSR.setCommonName("MPPS2XrayDoseSR");
        forwardRuleMPPS2DoseSR.setCallingAET("MPPSSCU");
        List<String> destinationURIMPPS2DoseSR = new ArrayList<String>();
        destinationURIMPPS2DoseSR.add("aet:DCM4CHEE");
        forwardRuleMPPS2DoseSR.setDestinationURIs(destinationURIMPPS2DoseSR);
        List<String> sopClass = new ArrayList<String>();
        sopClass.add("1.2.840.10008.3.1.2.3.3");
        forwardRuleMPPS2DoseSR.setSopClass(sopClass);
        forwardRuleMPPS2DoseSR.setMpps2DoseSrTemplateURI("file:${jboss.server.config.dir}/dcm4chee-proxy/dcm4chee-proxy-mpps2xraydosesr.xsl");
        forwardRuleMPPS2DoseSR.setDescription("Example ForwardRule for MPPS to Dose SR conversion");
        forwardRules.add(forwardRuleMPPS2DoseSR);

        ForwardRule forwardRulePrivate = new ForwardRule();
        forwardRulePrivate.setCommonName("Private");
        List<String > destinationURIPrivate = new ArrayList<String>();
        destinationURIPrivate.add("aet:STORESCP");
        destinationURIPrivate.add("aet:DCM4CHEE");
        forwardRulePrivate.setDestinationURIs(destinationURIPrivate);
        Schedule receiveSchedulePrivate = new Schedule();
        receiveSchedulePrivate.setDays("Wed");
        receiveSchedulePrivate.setHours("9-18");
        forwardRulePrivate.setReceiveSchedule(receiveSchedulePrivate);
        List<String> sopClassesList = new ArrayList<String>();
        sopClassesList.add(UID.CTImageStorage);
        sopClassesList.add(UID.MRImageStorage);
        forwardRulePrivate.setSopClass(sopClassesList);
        forwardRulePrivate.setRunPIXQuery(Boolean.TRUE);
        forwardRulePrivate.setDescription("Example ForwardRule");
        forwardRules.add(forwardRulePrivate);
        
        proxyAEE.setForwardRules(forwardRules);
        
        List<Retry> retries = new ArrayList<Retry>();
        retries.add(new Retry(RetryObject.ConnectionException, 20, 10, true));
        retries.add(new Retry(RetryObject.AssociationStateException, 20, 10, true));
        proxyAEE.setRetries(retries);
        
        addVerificationStorageTransferCapabilities(ae);
        addTCs(ae, null, Role.SCP, IMAGE_CUIDS, IMAGE_TSUIDS);
        addTCs(ae, null, Role.SCP, VIDEO_CUIDS, VIDEO_TSUIDS);
        addTCs(ae, null, Role.SCP, OTHER_CUIDS, OTHER_TSUIDS);
        addTCs(ae, null, Role.SCU, IMAGE_CUIDS, IMAGE_TSUIDS);
        addTCs(ae, null, Role.SCU, VIDEO_CUIDS, VIDEO_TSUIDS);
        addTCs(ae, null, Role.SCU, OTHER_CUIDS, OTHER_TSUIDS);
        addTCs(ae, EnumSet.allOf(QueryOption.class), Role.SCP, QUERY_CUIDS, UID.ImplicitVRLittleEndian);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", "localhost", 22222);
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
        dicom.setInstalled(true);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        
        HL7Application hl7App = new HL7Application("*");
        hl7App.setAcceptedMessageTypes(HL7_MESSAGE_TYPES);
        hl7DevExt.addHL7Application(hl7App);
        Connection hl7 = new Connection("hl7", "localhost", 22575);
        hl7.setProtocol(Protocol.HL7);
        device.addConnection(hl7);
        hl7App.addConnection(hl7);

        proxyAEE.setProxyPIXConsumerApplication(PIX_CONSUMER);
        proxyAEE.setRemotePIXManagerApplication(PIX_MANAGER);

        return device;
    }
    
    private static Device createDevice(DicomConfiguration config, String name) throws Exception {
        Device device = new Device(name);
        return device;
    }

    private Device createHL7Device(String name, Issuer issuer, Code institutionCode, String appName, String host,
            int port, int tlsPort) throws Exception {
        Device device = new Device(name);
        HL7DeviceExtension hl7Device = new HL7DeviceExtension();
        device.addDeviceExtension(hl7Device);
        init(device, issuer, institutionCode);
        HL7Application hl7app = new HL7Application(appName);
        hl7Device.addHL7Application(hl7app);
        Connection hl7Connection = new Connection("hl7", host, port);
        hl7Connection.setProtocol(Protocol.HL7);
        device.addConnection(hl7Connection);
        hl7app.addConnection(hl7Connection);
        return device;
    }

    private static Device init(Device device, Issuer issuer, Code institutionCode)
            throws Exception {
        device.setIssuerOfPatientID(issuer);
        device.setIssuerOfAccessionNumber(issuer);
        if (institutionCode != null) {
            device.setInstitutionNames(institutionCode.getCodeMeaning());
            device.setInstitutionCodes(institutionCode);
        }
        return device;
    }

    private static Device createDevice(DicomConfiguration config, String name, String aet,
            String host, int port, int tlsPort) throws Exception {
        Device device = createDevice(config, name);
        ApplicationEntity ae = new ApplicationEntity(aet);
        ae.setAssociationAcceptor(true);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", host, port);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        return device;
    }

    private static void addVerificationStorageTransferCapabilities(ApplicationEntity ae) {
        String cuid = UID.VerificationSOPClass;
        String name = UID.nameOf(cuid).replace('/', ' ');
        ae.addTransferCapability(
                new TransferCapability(name + " SCP", cuid, TransferCapability.Role.SCP,
                        UID.ImplicitVRLittleEndian));
        ae.addTransferCapability(
                new TransferCapability(name + " SCU", cuid, TransferCapability.Role.SCU,
                        UID.ImplicitVRLittleEndian));
        
    }

    private void addTCs(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
            TransferCapability.Role role, String[] cuids, String... tss) {
        for (String cuid : cuids)
            addTC(ae, queryOpts, role, cuid, tss);
    }

    private void addTC(ApplicationEntity ae, EnumSet<QueryOption> queryOpts,
            TransferCapability.Role role, String cuid, String... tss) {
        String name = UID.nameOf(cuid).replace('/', ' ');
        TransferCapability tc = new TransferCapability(name + ' ' + role, cuid, role, tss);
        tc.setQueryOptions(queryOpts);
        ae.addTransferCapability(tc);
    }
}
