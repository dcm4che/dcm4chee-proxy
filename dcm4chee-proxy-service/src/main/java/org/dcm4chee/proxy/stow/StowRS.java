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
package org.dcm4chee.proxy.stow;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.lf5.util.StreamUtils;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Attributes.Visitor;
import org.dcm4che.data.BulkData;
import org.dcm4che.data.Fragments;
import org.dcm4che.data.Sequence;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.data.VR;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.DicomInputStream.IncludeBulkData;
import org.dcm4che.io.DicomOutputStream;
import org.dcm4che.io.SAXReader;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.mime.MultipartInputStream;
import org.dcm4che.mime.MultipartParser;
import org.dcm4che.net.Association;
import org.dcm4che.net.DataWriterAdapter;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.TransferCapability;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.Proxy;
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.conf.ForwardOption;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.utils.AttributeCoercionUtils;
import org.dcm4chee.proxy.utils.ForwardConnectionUtils;
import org.dcm4chee.proxy.utils.ForwardRuleUtils;
import org.dcm4chee.proxy.utils.LogUtils;
import org.dcm4chee.proxy.wado.MediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
@Path("/stow-rs/{AETitle}")
public class StowRS implements MultipartParser.Handler, StreamingOutput {

    private static final Logger LOG = LoggerFactory.getLogger(StowRS.class);

    private static final int TRANSFER_SYNTAX_NOT_SUPPORTED = 0xC122;
    private static final int DIFF_STUDY_INSTANCE_UID = 0xC123;
    private String name;
    @Context
    private HttpServletRequest request;
    @Context
    private UriInfo uriInfo;
    @HeaderParam("Content-Type")
    private MediaType contentType;
    @PathParam("AETitle")
    private String aet;
    private String wadoURL;
    private CreatorType creatorType;
    private String studyInstanceUID;
    private ProxyAEExtension proxyAEE;
    private MultipartParser parser;
    private final List<FileInfo> files = new ArrayList<FileInfo>();
    private final Map<String, FileInfo> bulkdata = new HashMap<String, FileInfo>();
    private final Attributes response = new Attributes();
    private Sequence sopSequence;
    private Sequence failedSOPSequence;
    List<ForwardRule> fwdRules = new ArrayList<>();

    @Override
    public String toString() {
        if (name == null) {
            if (request == null)
                return super.toString();
            name = request.getRemoteHost() + ':' + request.getRemotePort();
        }
        return name;
    }

    @POST
    @Path("/studies")
    @Consumes({ "multipart/related", "multipart/form-data" })
    public Response storeInstances(InputStream in) throws DicomServiceException {
        return storeInstances(null, in);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related")
    public Response storeInstances(@PathParam("StudyInstanceUID") String studyInstanceUID, InputStream in)
            throws DicomServiceException {
        LOG.info("{} >> STOW-RS[{}, Content-Type={}]", new Object[] { this, request.getRequestURL(), contentType });
        init(studyInstanceUID);
        try {
            parser.parse(in, this);
        } catch (IOException e) {
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
        initResponse();
        creatorType.storeInstances(this);
        return response();
    }

    private Response response() {
        if (sopSequence.isEmpty())
            throw new WebApplicationException(Status.CONFLICT);

        return Response.status(failedSOPSequence.isEmpty() ? Status.OK : Status.ACCEPTED).entity(this)
                .type(MediaTypes.APPLICATION_DICOM_XML_TYPE).build();
    }

    private void init(String studyInstanceUID) throws DicomServiceException {
        String boundary = contentType.getParameters().get("boundary");
        if (boundary == null)
            throw new WebApplicationException(Status.BAD_REQUEST);

        this.studyInstanceUID = studyInstanceUID;
        creatorType = CreatorType.valueOf(contentType);
        parser = new MultipartParser(boundary);
        ProxyDeviceExtension proxyDevExt = Proxy.getInstance().getDevice().getDeviceExtension(ProxyDeviceExtension.class);
        proxyAEE = proxyDevExt.getDevice().getApplicationEntities().iterator().next().getAEExtension(ProxyAEExtension.class);
    }

    public void initResponse() {
        wadoURL = uriInfo.getBaseUri() + "wado/" + aet + "/studies/";
        if (studyInstanceUID != null)
            response.setString(Tag.RetrieveURI, VR.UT, wadoURL + studyInstanceUID);
        else
            response.setNull(Tag.RetrieveURI, VR.UT);
        sopSequence = response.newSequence(Tag.ReferencedSOPSequence, files.size());
        failedSOPSequence = response.newSequence(Tag.FailedSOPSequence, files.size());
    }

    @Override
    public void bodyPart(int partNumber, MultipartInputStream in) throws IOException {
        Map<String, String> headerParams = in.readHeaderParams();
        String mediaTypeStr = headerParams.get("content-type");
        String bulkdataURI = headerParams.get("content-location");
        LOG.info("{} >> {}:STOW-RS[Content-Type={}, Content-Location={}]", new Object[] { this, partNumber,
                mediaTypeStr, bulkdataURI });
        try {
            MediaType mediaType = (mediaTypeStr != null) ? MediaType.valueOf(mediaTypeStr) : MediaType.TEXT_PLAIN_TYPE;
            if (creatorType.accept(mediaType, bulkdataURI)) {
                if (in.isZIP()) {
                    ZipInputStream zip = new ZipInputStream(in);
                    ZipEntry zipEntry;
                    while ((zipEntry = zip.getNextEntry()) != null) {
                        if (!zipEntry.isDirectory())
                            storeSpoolFile(zip, mediaType, bulkdataURI);
                    }
                } else {
                    storeSpoolFile(in, mediaType, bulkdataURI);
                }
            } else {
                LOG.info("{}: Ignore Part with Content-Type={}", this, mediaType);
                in.skipAll();
            }
        } catch (IllegalArgumentException e) {
            LOG.info("{}: Ignore Part with illegal Content-Type={}", this, mediaTypeStr);
            in.skipAll();
        }
    }

    private void storeSpoolFile(InputStream in, MediaType mediaType, String bulkdataURI) throws IOException,
            FileNotFoundException {
        File file = File.createTempFile("dcm", ".part", proxyAEE.getCStoreDirectoryPath());
        LOG.info("{}: WRITE {}", this, file);
        OutputStream out = new FileOutputStream(file);
        try {
            StreamUtils.copy(in, out);
        } finally {
            SafeClose.close(out);
        }
        FileInfo fileInfo = new FileInfo(file, mediaType);
        if (creatorType.isBulkdata(mediaType))
            bulkdata.put(bulkdataURI, fileInfo);
        else {
            files.add(fileInfo);
        }
    }

    private void writeDicomInstance(File file, Attributes fmi, Attributes dataset) throws IOException {
        LOG.info("{}: WRITE {}", this, file);
        OutputStream out = new FileOutputStream(file);
        try {
            @SuppressWarnings("resource")
            DicomOutputStream dos = new DicomOutputStream(out, UID.ExplicitVRLittleEndian);
            dos.writeDataset(fmi, dataset);
            dos.flush();
        } finally {
            SafeClose.close(out);
        }
    }

    private enum CreatorType {
        DicomCreator {
            @Override
            void storeInstances(StowRS stowRS) {
                stowRS.storeDicomInstances();
            }

            @Override
            boolean accept(MediaType mediaType, String bulkdataURI) {
                String type = mediaType.getType();
                String subtype = mediaType.getSubtype();
                return type.equalsIgnoreCase("application")
                        && (subtype.equalsIgnoreCase("dicom")
                                || subtype.equalsIgnoreCase("octet-stream")
                                || subtype.equalsIgnoreCase("zip")
                                || subtype.equalsIgnoreCase("x-zip"));
            }

            @Override
            boolean isBulkdata(MediaType mediaType) {
                return false;
            }

            @Override
            MessageDigest digest(MessageDigest digest) {
                return digest;
            }

            @Override
            String fileSuffix(MediaType mediaType) {
                return ".dcm";
            }
        },
        MetadataBulkdataCreator {
            @Override
            void storeInstances(StowRS stowRS) {
                stowRS.storeMetadataAndBulkData();
            }

            @Override
            boolean accept(MediaType mediaType, String bulkdataURI) {
                return !(isBulkdata(mediaType) && bulkdataURI == null);
            }

            @Override
            boolean isBulkdata(MediaType mediaType) {
                String type = mediaType.getType();
                String subtype = mediaType.getSubtype();
                return !(type.equalsIgnoreCase("application") && subtype.equalsIgnoreCase("dicom+xml"));
            }

            @Override
            MessageDigest digest(MessageDigest digest) {
                return null;
            }

            @Override
            String fileSuffix(MediaType mediaType) {
                String subtype = mediaType.getSubtype();
                return "." + subtype.substring(subtype.indexOf('+') + 1).toLowerCase();
            }
        };

        abstract void storeInstances(StowRS stowRS);

        abstract boolean accept(MediaType mediaType, String bulkdataURI);

        abstract boolean isBulkdata(MediaType mediaType);

        abstract MessageDigest digest(MessageDigest digest);

        abstract String fileSuffix(MediaType mediaType);

        public static CreatorType valueOf(MediaType contentType) {
            if (contentType.getSubtype().equalsIgnoreCase("form-data"))
                return DicomCreator;

            String type = contentType.getParameters().get("type");
            if (type == null)
                throw new WebApplicationException(Status.BAD_REQUEST);
            if (type.equalsIgnoreCase(MediaTypes.APPLICATION_DICOM))
                return DicomCreator;
            else if (type.equalsIgnoreCase(MediaTypes.APPLICATION_DICOM_XML))
                return MetadataBulkdataCreator;

            throw new WebApplicationException(Status.UNSUPPORTED_MEDIA_TYPE);
        }

    }

    private static class FileInfo {
        final File file;
        final MediaType mediaType;
        Attributes attrs;

        FileInfo(File file, MediaType mediaType) {
            this.file = file;
            this.mediaType = mediaType;
        }
    }

    private Attributes readFileMetaInformation(File file) throws IOException {
        DicomInputStream in = new DicomInputStream(file);
        try {
            return in.readFileMetaInformation();
        } finally {
            SafeClose.close(in);
        }
    }

    private void storeDicomInstances() {
        for (FileInfo fileInfo : files) {
            try {
                fileInfo.attrs = readFileMetaInformation(fileInfo.file);
                Attributes fmi, attrs;
                DicomInputStream in = new DicomInputStream(fileInfo.file);
                try {
                    in.setIncludeBulkData(IncludeBulkData.URI);
                    fmi = in.readFileMetaInformation();
                    attrs = in.readDataset(-1, -1);
                } finally {
                    SafeClose.close(in);
                }
                String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
                if (sourceAET == null)
                    sourceAET = request.getRemoteAddr();
                Properties prop = setInfoFileProperties(fmi, attrs, sourceAET);
                validateStudyIUID(attrs);
                String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
                attrs = AttributeCoercionUtils.coerceAttributes(proxyAEE, sourceAET, cuid, TransferCapability.Role.SCU,
                        Dimse.C_STORE_RQ, attrs, this);
                if (proxyAEE.getApplicationEntity().getAETitle().equals(aet)) {
                    if (fwdRules.isEmpty())
                        setForwardRules(attrs, cuid, sourceAET);
                    processForwardRules(fileInfo, fmi, attrs, sourceAET, prop, cuid);
                } else {
                    processSingleForwardDestination(fileInfo, attrs, fmi, null, prop, sourceAET);
                }
                deleteFile(fileInfo.file);
            } catch (DicomServiceException | ConfigurationException e) {
                throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
            } catch (IOException ioe) {
                throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }
    }

    private void processForwardRules(FileInfo fileInfo, Attributes fmi, Attributes attrs, String sourceAET,
            Properties prop, String cuid) {
        if (fwdRules.isEmpty()) {
            LOG.error("{}: No active forward rule matching request", this);
            storageFailed(fileInfo.attrs, org.dcm4che.net.Status.ProcessingFailure);
            return;
        }
        if (fwdRules.size() == 1) {
            processSingleForwardDestination(fileInfo, attrs, fmi, fwdRules.get(0), prop, sourceAET);
            return;
        }
        List<String> prevDestinationAETs = new ArrayList<>();
        for (ForwardRule rule : fwdRules) {
            if (rule.getUseCallingAET() != null)
                prop.setProperty("use-calling-aet", rule.getUseCallingAET());
            else
                prop.remove("use-calling-aet");
            try {
                for (String destinationAET : getDestinationAETsFromForwardRule(rule, attrs)) {
                    File dst = null;
                    try {
                        if (prevDestinationAETs.contains(destinationAET)) {
                            LOG.info("{}: Found previously used destination AET {} in rule {}, will not send data again",
                                    new Object[] { this, destinationAET, rule.getCommonName() });
                            LOG.info("{}: Please check configured forward rules for overlapping time with duplicate destination AETs");
                            continue;
                        }
                        Attributes destAttrs = AttributeCoercionUtils.coerceAttributes(proxyAEE, destinationAET, cuid,
                                TransferCapability.Role.SCP, Dimse.C_STORE_RQ, attrs, this);
                        dst = storeDestinationAETCopy(fileInfo, fmi, destinationAET, destAttrs);
                        storeInfoFile(prop, dst);
                        prevDestinationAETs.add(destinationAET);
                    } catch (Exception e) {
                        LOG.info("{}: Failed to store file to destination AET directory: {}", this, e);
                        if (LOG.isDebugEnabled())
                            e.printStackTrace();
                        int failureReason = e instanceof DicomServiceException ? ((DicomServiceException) e).getStatus()
                                : org.dcm4che.net.Status.ProcessingFailure;
                        storageFailed(fileInfo.attrs, failureReason);
                        if (dst != null && dst.exists())
                            deleteFile(dst);
                    }
                }
                setSopRef(fmi, attrs);
            } catch (ConfigurationException e) {
                LOG.error("{}: Failed to retrieve Destination AET from forward rule {}", this, rule.getCommonName());
                if (LOG.isDebugEnabled())
                    e.printStackTrace();
                storageFailed(fileInfo.attrs, org.dcm4che.net.Status.ProcessingFailure);
            }
        }
    }

    private File storeDestinationAETCopy(FileInfo fileInfo, Attributes fmi, String destinationAET, Attributes destAttrs)
            throws IOException, FileNotFoundException, SyncFailedException {
        File dst;
        dst = createDestinationAETFile(fileInfo.file.getName(), destinationAET);
        FileOutputStream fout = new FileOutputStream(dst);
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        DicomOutputStream out = new DicomOutputStream(bout, UID.ExplicitVRLittleEndian);
        try {
            out.writeDataset(fmi, destAttrs);
            fout.flush();
            fout.getFD().sync();
            LOG.info("{}: copy {} to {}", new Object[]{this, fileInfo.file.getPath(), dst.getPath()});
        } finally {
            out.close();
            bout.close();
            fout.close();
        }
        return dst;
    }

    private void storeInfoFile(Properties prop, File file) throws IOException {
        String path = file.getPath();
        File infoFile = new File(path.substring(0, path.length() - 4) + ".info");
        FileOutputStream infoOut = new FileOutputStream(infoFile);
        try {
            prop.store(infoOut, null);
            infoOut.flush();
            infoOut.getFD().sync();
        } finally {
            infoOut.close();
        }
        LOG.debug("{}: store info file {}", this, infoFile.getPath());
    }

    private Properties setInfoFileProperties(Attributes fmi, Attributes attrs, String sourceAET) {
        Properties prop = new Properties();
        prop.setProperty("hostname", request.getRemoteHost());
        String patID = attrs.getString(Tag.PatientID);
        prop.setProperty("patient-id", (patID == null || patID.length() == 0) ? "<UNKNOWN>" : patID);
        prop.setProperty("study-iuid", attrs.getString(Tag.StudyInstanceUID));
        prop.setProperty("sop-instance-uid", attrs.getString(Tag.SOPInstanceUID));
        prop.setProperty("sop-class-uid", attrs.getString(Tag.SOPClassUID));
        prop.setProperty("transfer-syntax-uid", fmi.getString(Tag.TransferSyntaxUID));
        prop.setProperty("source-aet", (sourceAET == null)
                ? proxyAEE.getApplicationEntity().getAETitle()
                : sourceAET);
        return prop;
    }

    private File createDestinationAETFile(String fileName, String destinationAET) throws IOException {
        File dir = new File(proxyAEE.getCStoreDirectoryPath(), destinationAET);
        dir.mkdir();
        return new File(dir, fileName.substring(0, fileName.lastIndexOf('.')) + ".dcm");
    }

    private void setSopRef(Attributes fmi, Attributes attrs) {
        Attributes sopRef = new Attributes(5);
        sopRef.setString(Tag.ReferencedSOPClassUID, VR.UI, fmi.getString(Tag.MediaStorageSOPClassUID));
        String iuid = fmi.getString(Tag.MediaStorageSOPInstanceUID);
        sopRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        sopRef.setString(Tag.RetrieveURI, VR.UT, wadoURL
                + attrs.getString(Tag.StudyInstanceUID) + "/series/"
                + attrs.getString(Tag.SeriesInstanceUID) + "/instances/"
                + iuid);
        sopSequence.add(sopRef);
    }

    private void storeMetadataAndBulkData() {
        for (FileInfo fileInfo : files) {
            try {
                fileInfo.attrs = SAXReader.parse(fileInfo.file.toURI().toString());
            } catch (Exception e) {
                throw new WebApplicationException(e, Status.BAD_REQUEST);
            }
        }
        for (FileInfo fileInfo : files) {
            resolveBulkdata(fileInfo.attrs);
            String tsuid = resolveBulkdata(fileInfo.attrs);
            Attributes fmi = fileInfo.attrs.createFileMetaInformation(tsuid);
            if (!checkTransferCapability(fmi))
                continue;

            String cuid = fmi.getString(Tag.MediaStorageSOPClassUID);
            String sourceAET = fmi.getString(Tag.SourceApplicationEntityTitle);
            if (sourceAET == null)
                sourceAET = request.getRemoteAddr();
            Properties prop = setInfoFileProperties(fmi, fileInfo.attrs, sourceAET);
            if (proxyAEE.getApplicationEntity().getAETitle().equals(aet))
                processForwardRules(fileInfo, fmi, cuid, sourceAET, prop);
            else
                processSingleForwardDestination(fileInfo, fileInfo.attrs, fmi, null, prop, sourceAET);
        }
    }

    private void processForwardRules(FileInfo fileInfo, Attributes fmi, String cuid,
            String sourceAET, Properties prop) {
        File file = null;
        try {
            if (fwdRules.isEmpty())
                setForwardRules(fileInfo.attrs, cuid, sourceAET);
            List<String> prevDestinationAETs = new ArrayList<>();
            for (ForwardRule rule : fwdRules) {
                if (rule.getUseCallingAET() != null)
                    prop.setProperty("use-calling-aet", rule.getUseCallingAET());
                else
                    prop.remove("use-calling-aet");
                for (String destinationAET : getDestinationAETsFromForwardRule(rule, fileInfo.attrs)) {
                    if (prevDestinationAETs.contains(destinationAET)) {
                        LOG.info("{}: Found previously used destination AET {} in rule {}, will not send data again",
                                new Object[] { this, destinationAET, rule.getCommonName() });
                        LOG.info("{}: Please check configured forward rules for overlapping time with duplicate destination AETs");
                        continue;
                    }
                    Attributes destAttrs = AttributeCoercionUtils.coerceAttributes(proxyAEE, destinationAET, cuid,
                            TransferCapability.Role.SCP, Dimse.C_STORE_RQ, fileInfo.attrs, this);
                    validateStudyIUID(destAttrs);
                    file = createDestinationAETFile(fileInfo.file.getName(), destinationAET);
                    writeDicomInstance(file, fmi, destAttrs);
                    storeInfoFile(prop, file);
                }
            }
        } catch (Exception e) {
            LOG.info("{}: Storage Failed {}", this, e);
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            int failureReason = e instanceof DicomServiceException ? ((DicomServiceException) e).getStatus()
                    : org.dcm4che.net.Status.ProcessingFailure;
            storageFailed(fmi, failureReason);
            if (file != null && file.exists())
                deleteFile(file);
        }
    }

    private void setForwardRules(Attributes attrs, String cuid, String sourceAET) throws ConfigurationException {
        fwdRules = ForwardRuleUtils.filterForwardRulesByCallingAET(proxyAEE, sourceAET);
        fwdRules = ForwardRuleUtils.filterForwardRulesOnDimseRQ(fwdRules, cuid, Dimse.C_STORE_RQ);
    }

    private List<String> getDestinationAETsFromForwardRule(ForwardRule rule, Attributes attrs)
            throws ConfigurationException {
        if (rule.containsTemplateURI())
            return ForwardRuleUtils.getDestinationAETsFromTemplate(proxyAEE, rule.getDestinationTemplate(), attrs);
        else
            return rule.getDestinationAETitles();
    }

    private String resolveBulkdata(final Attributes attrs) {
        final String[] tsuids = { UID.ExplicitVRLittleEndian };
        attrs.accept(new Visitor() {
            @Override
            public void visit(Attributes attrs, int tag, VR vr, Object value) {
                if (value instanceof Sequence) {
                    Sequence sq = (Sequence) value;
                    for (Attributes item : sq)
                        resolveBulkdata(item);
                } else if (value instanceof BulkData) {
                    FileInfo fileInfo = bulkdata.get(((BulkData) value).uri);
                    if (fileInfo != null) {
                        String tsuid = MediaTypes.transferSyntaxOf(fileInfo.mediaType);
                        BulkData bd = new BulkData(
                                fileInfo.file.toURI().toString(),
                                0, (int) fileInfo.file.length(),
                                attrs.bigEndian());
                        if (tsuid.equals(UID.ExplicitVRLittleEndian)) {
                            attrs.setValue(tag, vr, bd);
                        } else {
                            Fragments frags = attrs.newFragments(tag, vr, 2);
                            frags.add(null);
                            frags.add(bd);
                            tsuids[0] = tsuid;
                        }
                    }
                }
            }
        });
        return tsuids[0];
    }

    private void validateStudyIUID(Attributes attrs) throws DicomServiceException {
        if (studyInstanceUID != null && !studyInstanceUID.equals(attrs.getString(Tag.StudyInstanceUID)))
            throw new DicomServiceException(DIFF_STUDY_INSTANCE_UID);
    }

    private boolean checkTransferCapability(Attributes fmi) {
        TransferCapability tc = proxyAEE.getApplicationEntity().getTransferCapabilityFor(fmi.getString(Tag.MediaStorageSOPClassUID),
                Role.SCP);
        if (tc == null) {
            storageFailed(fmi, org.dcm4che.net.Status.SOPclassNotSupported);
            return false;
        }
        if (!tc.containsTransferSyntax(fmi.getString(Tag.TransferSyntaxUID))) {
            storageFailed(fmi, TRANSFER_SYNTAX_NOT_SUPPORTED);
            return false;
        }
        return true;
    }

    private void storageFailed(Attributes fmi, int failureReason) {
        Attributes sopRef = new Attributes(3);
        sopRef.setString(Tag.ReferencedSOPClassUID, VR.UI, fmi.getString(Tag.MediaStorageSOPClassUID));
        sopRef.setString(Tag.ReferencedSOPInstanceUID, VR.UI, fmi.getString(Tag.MediaStorageSOPInstanceUID));
        sopRef.setInt(Tag.FailureReason, VR.US, failureReason);
        failedSOPSequence.add(sopRef);
    }

    private void deleteFile(File file) {
        if (file.delete())
            LOG.info("{}: DELETE {}", this, file);
        else
            LOG.warn("{}: DELETE {} failed!", this, file);
    }

    @Override
    public void write(OutputStream out) throws IOException, WebApplicationException {
        try {
            SAXTransformer.getSAXWriter(new StreamResult(out)).write(response);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    private void processSingleForwardDestination(FileInfo fileInfo, Attributes attrs, Attributes fmi, ForwardRule rule,
            Properties prop, String callingAET) {
        String calledAET = null;
        if (rule != null) {
            if (rule.getUseCallingAET() != null) {
                prop.setProperty("use-calling-aet", rule.getUseCallingAET());
                callingAET = rule.getUseCallingAET();
            }
            calledAET = rule.getDestinationAETitles().get(0);
        } else {
            calledAET = aet;
            callingAET = proxyAEE.getApplicationEntity().getAETitle();
        }
        ForwardOption forwardOption = proxyAEE.getForwardOptions().get(calledAET);
        if (forwardOption == null || forwardOption.getSchedule().isNow(new GregorianCalendar())) {
            Association as = null;
            try {
                as = ForwardConnectionUtils.openForwardAssociation(proxyAEE, rule, callingAET, calledAET,
                        fmi.getString(Tag.MediaStorageSOPClassUID), fmi.getString(Tag.TransferSyntaxUID),
                        this.toString());
                if (as != null)
                    forwardFile(as, new DataWriterAdapter(attrs), fileInfo.file, prop, fileInfo.file.length(),
                            fmi);
                else
                    storeToCalledAETSpoolDir(fileInfo, calledAET, prop, fmi);
            } finally {
                if (as != null)
                    try {
                        as.waitForOutstandingRSP();
                        as.release();
                    } catch (InterruptedException e) {
                        LOG.error(as + ": unexpected exception: " + e.getMessage());
                        if (LOG.isDebugEnabled())
                            e.printStackTrace();
                    } catch (IOException e) {
                        LOG.error(as + ": failed to release association: " + e.getMessage());
                        if (LOG.isDebugEnabled())
                            e.printStackTrace();
                    }
            }
        } else
            storeToCalledAETSpoolDir(fileInfo, calledAET, prop, fmi);
        setSopRef(fmi, fileInfo.attrs);
    }

    private void storeToCalledAETSpoolDir(FileInfo fileInfo, String calledAET, Properties prop, Attributes fmi) {
        File file = null;
        try {
            Attributes destAttrs = AttributeCoercionUtils.coerceAttributes(proxyAEE, aet, prop.getProperty("sop-class-uid"),
                    TransferCapability.Role.SCP, Dimse.C_STORE_RQ, fileInfo.attrs, this);
            validateStudyIUID(destAttrs);
            file = createDestinationAETFile(fileInfo.file.getName(), aet);
            writeDicomInstance(file, fmi, destAttrs);
            storeInfoFile(prop, file);
        } catch (Exception e) {
            LOG.info("{}: Storage Failed {}", this, e);
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            int failureReason = e instanceof DicomServiceException ? ((DicomServiceException) e).getStatus()
                    : org.dcm4che.net.Status.ProcessingFailure;
            storageFailed(fmi, failureReason);
            if (file != null && file.exists())
                deleteFile(file);
        }
    }

    private void forwardFile(Association as, DataWriterAdapter data, final File file,
            final Properties prop, final long fileSize, Attributes fmi) {
        final String cuid = prop.getProperty("sop-class-uid");
        final String iuid = prop.getProperty("sop-instance-uid");
        final String tsuid = prop.getProperty("transfer-syntax-uid");
        DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

            @Override
            public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
                super.onDimseRSP(as, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case org.dcm4che.net.Status.Success:
                case org.dcm4che.net.Status.CoercionOfDataElements: {
                    if (proxyAEE.isEnableAuditLog())
                        LogUtils.writeLogFile(proxyAEE, AuditDirectory.TRANSFERRED, as.getCallingAET(),
                                as.getRemoteAET(), prop, fileSize, -1);
                    break;
                }
                default: {
                    LOG.debug("{}: failed to forward file {} with error status {}", new Object[] { as, file,
                            Integer.toHexString(status) + 'H' });
                    try {
                        renameFile(proxyAEE, '.' + Integer.toHexString(status) + 'H', file, as.getCalledAET(), prop);
                    } catch (Exception e) {
                        LOG.error("{}: error renaming file {}: {}",
                                new Object[] { as, file.getPath(), e.getMessage() });
                        if(LOG.isDebugEnabled())
                            e.printStackTrace();
                    }
                }
                }
            }
        };
        try {
            if (proxyAEE.isEnableAuditLog()) {
                String sourceAET = prop.getProperty("source-aet");
                LogUtils.createStartLogFile(proxyAEE, AuditDirectory.TRANSFERRED, sourceAET, as.getRemoteAET(), as
                        .getConnection().getHostname(), prop, 0);
            }
            as.cstore(cuid, iuid, 0, data, tsuid, rspHandler);
        } catch (Exception e) {
            LOG.info("{}: Storage Failed {}", this, e);
            if (LOG.isDebugEnabled())
                e.printStackTrace();
            int failureReason = e instanceof DicomServiceException ? ((DicomServiceException) e).getStatus()
                    : org.dcm4che.net.Status.ProcessingFailure;
            storageFailed(fmi, failureReason);
        }
    }

    private void renameFile(ProxyAEExtension proxyAEE, String suffix, File file, String calledAET, Properties prop) {
        File dst;
        String path = file.getPath();
        if (path.endsWith(".snd"))
            dst = new File(path.substring(0, path.length() - 4), suffix);
        else
            dst = new File(path, suffix);
        if (file.renameTo(dst)) {
            dst.setLastModified(System.currentTimeMillis());
            LOG.debug("Rename {} to {}", new Object[] { file, dst });
            try {
                writeFailedAuditLogMessage(proxyAEE, dst, null, calledAET, prop);
            } catch (IOException e) {
                LOG.error("Failed to write audit log message");
                if (LOG.isDebugEnabled())
                    e.printStackTrace();
            }
        } else {
            LOG.error("Failed to rename {} to {}", new Object[] { file, dst });
        }
    }

    private void writeFailedAuditLogMessage(ProxyAEExtension proxyAEE, File file, Attributes fmi, String calledAET,
            Properties prop) throws IOException {
        if (proxyAEE.isEnableAuditLog() && file.getPath().contains("cstore")) {
            String sourceAET = prop.getProperty("source-aet");
            LogUtils.createStartLogFile(proxyAEE, AuditDirectory.FAILED, sourceAET, calledAET, proxyAEE.getApplicationEntity()
                    .getConnections().get(0).getHostname(), prop, 0);
            LogUtils.writeLogFile(proxyAEE, AuditDirectory.FAILED, sourceAET, calledAET, prop, file.length(), 0);
        }
    }
}
