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

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
package org.dcm4chee.proxy.audit;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.ParticipantObjectDescription;
import org.dcm4che.audit.SOPClass;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.util.SafeClose;
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class AuditLog {

    protected static final Logger LOG = LoggerFactory.getLogger(AuditLog.class);

    ApplicationEntity ae;

    private static AuditLogger logger;

    public AuditLog(AuditLogger logger) {
        AuditLog.logger = logger;
        AuditLogger.setDefaultLogger(logger);
    }

    public void scanLogDir(ApplicationEntity ae) {
        this.ae = ae;
        ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
        File failedPath = proxyAEE.getFailedAuditDirectoryPath();
        for (String calledAET : failedPath.list())
            scanCalledAETDir(new File(failedPath, calledAET), AuditDirectory.FAILED);
        File transferredPath = proxyAEE.getTransferredAuditDirectoryPath();
        for (String calledAET : transferredPath.list())
            scanCalledAETDir(new File(transferredPath, calledAET), AuditDirectory.TRANSFERRED);
        File deletePath = proxyAEE.getDeleteAuditDirectoryPath();
        for (String calledAET : deletePath.list())
            scanCalledAETDir(new File(deletePath, calledAET), AuditDirectory.DELETED);
    }

    private void scanCalledAETDir(File calledAETDir, AuditDirectory auditDir) {
        for (String callingAET : calledAETDir.list()) {
            File callingAETDir = new File(calledAETDir.getPath(), callingAET);
            for (String studyIUID : callingAETDir.list()) {
                File studyIUIDDir = new File(callingAETDir.getPath(), studyIUID);
                scanStudyDir(studyIUIDDir, auditDir);
            }
        }
    }

    private void scanStudyDir(final File studyIUIDDir, final AuditDirectory auditDir) {
        ae.getDevice().execute(new Runnable() {
            @Override
            public void run() {
                if (auditDir == AuditDirectory.FAILED)
                    checkRetryLog(studyIUIDDir, auditDir);
                else
                    checkLog(studyIUIDDir, auditDir);
            }
        });
    }

    private void checkRetryLog(File studyIUIDDir, AuditDirectory auditDir) {
        for (String numRetry : studyIUIDDir.list()) {
            String separator = System.getProperty("file.separator");
            File startLog = new File(studyIUIDDir + separator + numRetry + separator + "start.log");
            long lastModified = startLog.lastModified();
            long now = System.currentTimeMillis();
            ProxyDeviceExtension proxyDev = (ProxyDeviceExtension) ae.getDevice().getDeviceExtension(ProxyDeviceExtension.class);
            if (!(now > lastModified + proxyDev.getSchedulerInterval() * 1000 * 2))
                return;

            writeFailedLogMessage(studyIUIDDir, numRetry, auditDir, separator);
        }
    }

    private void checkLog(File studyIUIDDir, AuditDirectory auditDir) {
        String separator = System.getProperty("file.separator");
        File startLog = new File(studyIUIDDir + separator + "start.log");
        long lastModified = startLog.lastModified();
        long now = System.currentTimeMillis();
        ProxyDeviceExtension proxyDev = (ProxyDeviceExtension) ae.getDevice().getDeviceExtension(ProxyDeviceExtension.class);
        if (!(now > lastModified + proxyDev.getSchedulerInterval() * 1000 * 2))
            return;

        writeLogMessage(studyIUIDDir, auditDir, separator);
    }

    private void writeLogMessage(File studyIUIDDir, AuditDirectory auditDir, String separator) {
        File[] logFiles = studyIUIDDir.listFiles(fileFilter());
        if (logFiles != null && logFiles.length > 1) {
            Log log = new Log();
            log.files = logFiles.length;
            for (File file : logFiles)
                readProperties(file, log);
            float mb = log.totalSize / 1048576F;
            float time = (log.t2 - log.t1) / 1000F;
            String path = studyIUIDDir.getPath();
            String studyIUID = path.substring(path.lastIndexOf(separator) + 1);
            path = path.substring(0, path.lastIndexOf(separator));
            String callingAET = path.substring(path.lastIndexOf(separator) + 1);
            path = path.substring(0, path.lastIndexOf(separator));
            String calledAET = path.substring(path.lastIndexOf(separator) + 1);
            Calendar timeStamp = new GregorianCalendar();
            timeStamp.setTimeInMillis(log.t2);
            AuditMessage msg = new AuditMessage();
            try {
                switch (auditDir) {
                case TRANSFERRED:
                    writeSentServerLogMessage(log, mb, time, studyIUID, callingAET, calledAET);
                    msg = createAuditMessage(log, studyIUID, calledAET, log.hostname, callingAET, timeStamp,
                            EventID.DICOMInstancesTransferred, EventActionCode.Read, EventOutcomeIndicator.Success);
                    break;
                case DELETED:
                    msg = createAuditMessage(log, studyIUID, ae.getAETitle(), ae.getConnections().get(0).getHostname(),
                            callingAET, timeStamp, EventID.DICOMInstancesAccessed, EventActionCode.Delete,
                            EventOutcomeIndicator.Success);
                    break;
                default:
                    LOG.error("Unrecognized Audit Directory: " + auditDir.getDirectoryName());
                    break;
                }
                LOG.debug("AuditMessage: " + AuditMessages.toXML(msg));
                logger.write(timeStamp, msg);
            } catch (Exception e) {
                LOG.error("Failed to write audit log message: " + e.getMessage());
                LOG.debug(e.getMessage(), e);
            }
            boolean deleteStudyDir = deleteLogFiles(logFiles);
            if (deleteStudyDir)
                deleteStudyDir(studyIUIDDir, separator);
        }
    }

    private void deleteStudyDir(File studyIUIDDir, String separator) {
        for (String dir : studyIUIDDir.list()) {
            File subDir = new File(studyIUIDDir + separator + dir);
            if (subDir.listFiles().length > 0)
                return;

            LOG.debug("Delete dir " + subDir.getAbsolutePath());
            if (!subDir.delete())
                LOG.error("Failed to delete " + subDir.getAbsolutePath());
        }
        if (studyIUIDDir.list().length == 0) {
            LOG.debug("Delete dir " + studyIUIDDir.getAbsolutePath());
            if (!studyIUIDDir.delete())
                LOG.error("Failed to delete " + studyIUIDDir.getAbsolutePath());
        }
    }

    private boolean deleteLogFiles(File[] logFiles) {
        boolean deleteStudyDir = true;
        for (File file : logFiles) {
            LOG.debug("Delete log file " + file.getAbsolutePath());
            if (!file.delete()) {
                LOG.error("Failed to delete " + file.getAbsolutePath());
                deleteStudyDir = false;
            }
        }
        return deleteStudyDir;
    }

    private void writeFailedLogMessage(File studyIUIDDir, String retry, AuditDirectory auditDir, String separator) {
        File retryDir = new File(studyIUIDDir + separator + retry);
        File[] logFiles = retryDir.listFiles(fileFilter());
        if (logFiles != null && logFiles.length > 1) {
            Log log = new Log();
            log.files = logFiles.length;
            for (File file : logFiles)
                readProperties(file, log);
            String path = retryDir.getPath();
            String studyIUID = path.substring(path.lastIndexOf(separator) + 1);
            path = path.substring(0, path.lastIndexOf(separator));
            String callingAET = path.substring(path.lastIndexOf(separator) + 1);
            path = path.substring(0, path.lastIndexOf(separator));
            String calledAET = path.substring(path.lastIndexOf(separator) + 1);
            Calendar timeStamp = new GregorianCalendar();
            timeStamp.setTimeInMillis(log.t2);
            AuditMessage msg = createAuditMessage(log, studyIUID, calledAET, log.hostname, callingAET, timeStamp,
                    EventID.DICOMInstancesTransferred, EventActionCode.Read, EventOutcomeIndicator.SeriousFailure);
            try {
                LOG.debug("AuditMessage: " + AuditMessages.toXML(msg));
                logger.write(timeStamp, msg);
            } catch (Exception e) {
                LOG.error("Failed to write audit log message: ", e);
            }
            boolean deleteStudyDir = deleteLogFiles(logFiles);
            if (deleteStudyDir)
                deleteStudyDir(studyIUIDDir, separator);
        }
    }

    private AuditMessage createAuditMessage(Log log, String studyIUID, String destinationAET, String destinationHostname,
            String sourceAET, Calendar timeStamp, EventID eventID, String eventActionCode, String eventOutcomeIndicator) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                eventID, 
                eventActionCode, 
                timeStamp, 
                eventOutcomeIndicator, 
                null));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                destinationHostname, 
                AuditMessages.alternativeUserIDForAETitle(destinationAET), 
                null, 
                false, 
                null, 
                null, 
                null, 
                AuditMessages.RoleIDCode.Application));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                log.proxyHostname, 
                AuditMessages.alternativeUserIDForAETitle(ae.getAETitle()),
                null, 
                false, 
                null, 
                null, 
                null, 
                AuditMessages.RoleIDCode.Application));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                log.hostname, 
                AuditMessages.alternativeUserIDForAETitle(sourceAET), 
                null, 
                true, 
                null, 
                null, 
                null, 
                AuditMessages.RoleIDCode.Application));
        ParticipantObjectDescription pod = new ParticipantObjectDescription();
        for (String sopClassUID : log.sopclassuid) {
            SOPClass sc = new SOPClass();
            sc.setUID(sopClassUID);
            sc.setNumberOfInstances(log.files - 1);
            pod.getSOPClass().add(sc);
        }
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
                log.patientID,
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

    private void writeSentServerLogMessage(Log log, float mb, float time, String studyIUID, String callingAET,
            String calledAET) {
        LOG.info("Sent {} {} (={}MB) of study {} with SOPClassUIDs {} from {} to {} in {}s (={}MB/s)",
                new Object[] {  log.files - 1, 
                ((log.files - 1) > 1) ? "objects" : "object", 
                mb, 
                studyIUID,
                Arrays.toString(log.sopclassuid.toArray()), 
                callingAET, 
                calledAET, 
                time,
                (log.totalSize / 1048576F) / time });
    }

    private void readProperties(File file, Log log) {
        Properties prop = new Properties();
        FileInputStream inStream = null;
        try {
            inStream = new FileInputStream(file);
            prop.load(inStream);
            if (!file.getPath().endsWith("start.log")) {
                log.totalSize = log.totalSize + Long.parseLong(prop.getProperty("size"));
                log.sopclassuid.add(prop.getProperty("SOPClassUID"));
            } else {
                log.hostname = prop.getProperty("hostname");
                log.proxyHostname = prop.getProperty("proxyHostname");
                log.patientID = prop.getProperty("patientID");
            }
            long time = Long.parseLong(prop.getProperty("time"));
            log.t1 = (log.t1 == 0 || log.t1 > time) ? time : log.t1;
            log.t2 = (log.t2 == 0 || log.t2 < time) ? time : log.t2;
        } catch (IOException e) {
            LOG.error("Error reading properties from {}: {}", new Object[]{file.getPath(), e.getMessage()});
            LOG.debug(e.getMessage(), e);
        } finally {
            SafeClose.close(inStream);
        }
    }

    private FileFilter fileFilter() {
        return new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                if (pathname.getPath().endsWith(".log"))
                    return true;
                return false;
            }
        };
    }

    private class Log {
        private String patientID;
        private String hostname;
        private String proxyHostname;
        private long totalSize;
        private int files;
        private long t1, t2;
        private Set<String> sopclassuid = new HashSet<String>();
    }
}
