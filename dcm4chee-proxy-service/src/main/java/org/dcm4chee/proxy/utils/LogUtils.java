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

package org.dcm4chee.proxy.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.proxy.common.AuditDirectory;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
public class LogUtils {

    private static final Logger LOG = LoggerFactory.getLogger(LogUtils.class);

    public static void createStartLogFile(ProxyAEExtension proxyAEE, AuditDirectory auditDir, String callingAET,
            String calledAET, String proxyHostname, Properties fileInfo, Integer retry) throws IOException {
        String studyIUID = fileInfo.containsKey("study-iuid") 
                ? fileInfo.getProperty("study-iuid")
                : fileInfo.getProperty("sop-instance-uid");
        File file = new File(getLogDir(proxyAEE, auditDir, callingAET, calledAET, studyIUID, retry), "start.log");
        if (!file.exists()) {
            try {
                Properties prop = new Properties();
                prop.setProperty("time", String.valueOf(System.currentTimeMillis()));
                String patID = fileInfo.getProperty("patient-id");
                prop.setProperty("patient-id", (patID == null || patID.length() == 0) ? "<UNKOWN>" : patID);
                prop.setProperty("hostname", fileInfo.getProperty("hostname"));
                prop.setProperty("proxy-hostname", proxyHostname);
                prop.store(new FileOutputStream(file), null);
            } catch (IOException e) {
                LOG.debug("Failed to create log file: " + e.getMessage());
            }
        }
    }

    public static File getLogDir(ProxyAEExtension proxyAEE, AuditDirectory auditDir, String callingAET, String calledAET,
            String studyIUID, Integer retry) throws IOException {
        File path = null;
        String subDirs = proxyAEE.getSeparator() + calledAET + proxyAEE.getSeparator() + callingAET
                + proxyAEE.getSeparator() + studyIUID;
        switch (auditDir) {
        case TRANSFERRED:
            path = new File(proxyAEE.getTransferredAuditDirectoryPath().getPath() + subDirs);
            break;
        case FAILED:
            path = new File(proxyAEE.getFailedAuditDirectoryPath().getPath()  + subDirs + proxyAEE.getSeparator() + retry);
            break;
        case DELETED:
            path = new File(proxyAEE.getDeleteAuditDirectoryPath().getPath() + subDirs);
            break;
        default:
            LOG.error("Unrecognized Audit Directory: " + auditDir.getDirectoryName());
            break;
        }
        if (path == null)
            throw new DicomServiceException(Status.UnableToProcess);
        ProxyAEExtension.makeDirs(path);
        return path;
    }

    public static File writeLogFile(ProxyAEExtension proxyAEE, AuditDirectory auditDir, String callingAET, String calledAET,
            Properties fileInfo, long size, Integer retry) {
        Properties prop = new Properties();
        File file = null;
        try {
            String studyIUID = fileInfo.containsKey("study-iuid") 
                    ? fileInfo.getProperty("study-iuid")
                    : fileInfo.getProperty("sop-instance-uid");
            file = new File(getLogDir(proxyAEE, auditDir, callingAET, calledAET, studyIUID , retry), 
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
}
