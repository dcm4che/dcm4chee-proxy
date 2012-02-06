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

package org.dcm4chee.proxy.mc.conf.ldap;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che.conf.api.AttributeCoercion;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.data.UID;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Connection;
import org.dcm4che.net.Device;
import org.dcm4che.net.SSLManagerFactory;
import org.dcm4che.net.TransferCapability;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;
import org.dcm4chee.proxy.mc.net.ProxyDevice;
import org.dcm4chee.proxy.mc.net.Retry;
import org.dcm4chee.proxy.mc.net.Schedule;
import org.junit.Test;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyConfigurationTestUtils {

    private static final String[] OTHER_DEVICES = {
        "storescp",
        "storescu"
    };
    
    private static final String[] OTHER_AES = {
        "STORESCP",
        "STORESCU",
    };

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
    };

    private static final KeyStore KEYSTORE = loadKeyStore();
    private static KeyStore loadKeyStore() {
        try {
            return SSLManagerFactory.loadKeyStore("JKS", "resource:cacerts.jks", "secret");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static void cleanUp(DicomConfiguration config) throws ConfigurationException {
        config.unregisterAETitle("DCM4CHEE-PROXY");
        for (String aet : OTHER_AES)
            config.unregisterAETitle(aet);

        try {
            config.removeDevice("dcm4chee-proxy");
        }  catch (ConfigurationNotFoundException e) {}
        for (String name : OTHER_DEVICES)
            try {
                config.removeDevice(name);
            }  catch (ConfigurationNotFoundException e) {}
    }
    
    @Test
    public static void testPersist(DicomConfiguration config) throws Exception {
        for (int i = 0; i < OTHER_AES.length; i++) {
            String aet = OTHER_AES[i];
            config.registerAETitle(aet);
            config.persist(createDevice(config, OTHER_DEVICES[i], aet,
                    "localhost", 11113 + i, 2763 + i));
        }
        for (int i = OTHER_AES.length; i < OTHER_DEVICES.length; i++)
            config.persist(createDevice(config, OTHER_DEVICES[i]));

        config.registerAETitle("DCM4CHEE-PROXY");
        config.persist(createProxyDevice(config, "dcm4chee-proxy"));
        config.findApplicationEntity("DCM4CHEE-PROXY");
    }

    private static ProxyDevice createProxyDevice(DicomConfiguration config, String name) throws Exception {
        ProxyDevice device = new ProxyDevice(name);
        device.setThisNodeCertificates(config.deviceRef(name),
                (X509Certificate) KEYSTORE.getCertificate(name));
        for (String other : OTHER_DEVICES)
            device.setAuthorizedNodeCertificates(config.deviceRef(other),
                    (X509Certificate) KEYSTORE.getCertificate(other));
        device.setSchedulerInterval(60);
        
        ProxyApplicationEntity ae = new ProxyApplicationEntity("DCM4CHEE-PROXY");
        ae.setAssociationAcceptor(true);
        ae.setAssociationInitiator(true);
        ae.setSpoolDirectory("proxy");
        ae.setAcceptDataOnFailedNegotiation(false);
        ae.setDestinationAETitle("STORESCP");
        ae.setExclusiveUseDefinedTC(false);
        ae.setEnableAuditLog(true);
        ae.setAuditDirectory("audit");
        ae.setNactionDirectory("naction");
        ae.setNeventDirectory("nevent");
        ae.addAttributeCoercion(new AttributeCoercion(null, 
                AttributeCoercion.DIMSE.C_STORE_RQ, 
                TransferCapability.Role.SCP,
                "ENSURE_PID",
                "resource:dcm4chee-proxy-ensure-pid.xsl"));
        ae.addAttributeCoercion(new AttributeCoercion(null, 
                AttributeCoercion.DIMSE.C_STORE_RQ, 
                TransferCapability.Role.SCU,
                "WITHOUT_PN",
                "resource:dcm4chee-proxy-nullify-pn.xsl"));
        Schedule schedule = new Schedule();
        schedule.setDays("Mon-Fri");
        schedule.setHours("8-18");
        ae.setForwardSchedule(schedule);
        List<String> ignoreScheduleSOPClasses = new ArrayList<String>();
        ignoreScheduleSOPClasses.add("1.2.840.10008.1.20.1");
        ignoreScheduleSOPClasses.add("1.2.840.10008.5.1.4.31");
        ignoreScheduleSOPClasses.add("1.2.840.10008.5.1.4.1.2.1.1");
        ignoreScheduleSOPClasses.add("1.2.840.10008.5.1.4.1.2.2.1");
        ignoreScheduleSOPClasses.add("1.2.840.10008.5.1.4.1.2.3.1");
        ae.setIgnoreScheduleSOPClasses(ignoreScheduleSOPClasses);
        List<Retry> retries = new ArrayList<Retry>();
        retries.add(new Retry(".conn", 60, 5));
        retries.add(new Retry(".ass", 60, 5));
        ae.setRetries(retries);
        addVerificationStorageTransferCapabilities(ae);
        addStorageTransferCapabilities(ae, IMAGE_CUIDS, IMAGE_TSUIDS);
        addStorageTransferCapabilities(ae, VIDEO_CUIDS, VIDEO_TSUIDS);
        addStorageTransferCapabilities(ae, OTHER_CUIDS, OTHER_TSUIDS);
        device.addApplicationEntity(ae);
        Connection dicom = new Connection("dicom", "localhost", 11112);
        dicom.setMaxOpsInvoked(0);
        dicom.setMaxOpsPerformed(0);
//        dicom.setInstalled(true);
        device.addConnection(dicom);
        ae.addConnection(dicom);
        Connection dicomTLS = new Connection("dicom-tls", "localhost", 2762);
        dicomTLS.setMaxOpsInvoked(0);
        dicomTLS.setMaxOpsPerformed(0);
        dicomTLS.setTlsCipherSuites(
                Connection.TLS_RSA_WITH_AES_128_CBC_SHA, 
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
//        dicomTLS.setInstalled(false);
        device.addConnection(dicomTLS);
        ae.addConnection(dicomTLS);
        return device;
    }
    
    private static Device createDevice(DicomConfiguration config, String name) throws Exception {
        Device device = new Device(name);
        device.setThisNodeCertificates(config.deviceRef(name), (X509Certificate) KEYSTORE
                .getCertificate(name));
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
        Connection dicomTLS = new Connection("dicom-tls", host, tlsPort);
        dicomTLS.setTlsCipherSuites(Connection.TLS_RSA_WITH_AES_128_CBC_SHA,
                Connection.TLS_RSA_WITH_3DES_EDE_CBC_SHA);
        device.addConnection(dicomTLS);
        ae.addConnection(dicomTLS);
        return device;
    }

    private static void addVerificationStorageTransferCapabilities(
            ProxyApplicationEntity ae) {
        String cuid = UID.VerificationSOPClass;
        String name = UID.nameOf(cuid).replace('/', ' ');
        ae.addTransferCapability(
                new TransferCapability(name + " SCP", cuid, TransferCapability.Role.SCP,
                        UID.ImplicitVRLittleEndian));
        ae.addTransferCapability(
                new TransferCapability(name + " SCU", cuid, TransferCapability.Role.SCU,
                        UID.ImplicitVRLittleEndian));
        
    }

    private static void addStorageTransferCapabilities(ProxyApplicationEntity ae,
            String[] cuids, String[] tss) {
        for (String cuid : cuids) {
            String name = UID.nameOf(cuid).replace('/', ' ');
            ae.addTransferCapability(
                    new TransferCapability(name + " SCP", cuid, TransferCapability.Role.SCP, tss));
            ae.addTransferCapability(
                    new TransferCapability(name + " SCU", cuid, TransferCapability.Role.SCU, tss));
        }
    }
}
