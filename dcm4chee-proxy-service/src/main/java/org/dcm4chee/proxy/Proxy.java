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
 * Portions created by the Initial Developer are Copyright (C) 2012
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

package org.dcm4chee.proxy;

import static org.dcm4che.audit.AuditMessages.createEventIdentification;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;

import org.dcm4che.audit.AuditMessage;
import org.dcm4che.audit.AuditMessages;
import org.dcm4che.audit.AuditMessages.EventActionCode;
import org.dcm4che.audit.AuditMessages.EventID;
import org.dcm4che.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che.audit.AuditMessages.EventTypeCode;
import org.dcm4che.audit.AuditMessages.RoleIDCode;
import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.ConfigurationNotFoundException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.DeviceService;
import org.dcm4che.net.audit.AuditLogger;
import org.dcm4che.net.service.DicomServiceRegistry;
import org.dcm4chee.proxy.audit.AuditLog;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.dcm4chee.proxy.dimse.CEcho;
import org.dcm4chee.proxy.dimse.CFind;
import org.dcm4chee.proxy.dimse.CGet;
import org.dcm4chee.proxy.dimse.CMove;
import org.dcm4chee.proxy.dimse.CStore;
import org.dcm4chee.proxy.dimse.Mpps;
import org.dcm4chee.proxy.dimse.StgCmt;
import org.dcm4chee.proxy.forward.Scheduler;
import org.dcm4chee.proxy.pix.PIXConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class Proxy extends DeviceService implements ProxyMBean {

    private static final Logger LOG = LoggerFactory.getLogger(Proxy.class);

    private static Proxy instance;

    static Proxy getInstance() {
        return instance;
    }

    public static final String KS_TYPE = "org.dcm4chee.proxy.net.keyStoreType";
    public static final String KS_URL = "org.dcm4chee.proxy.net.keyStoreURL";
    public static final String KS_PASSWORD = "org.dcm4chee.proxy.net.storePassword";
    public static final String KEY_PASSWORD = "org.dcm4chee.proxy.net.keyPassword";

    private final DicomConfiguration dicomConfiguration;
    private PIXConsumer pixConsumer;
    private static Scheduler scheduler;
    private final HL7ApplicationCache hl7AppCache;
    private final ApplicationEntityCache aeCache;
    private final CEcho cecho;
    private final CStore cstore;
    private final StgCmt stgcmt;
    private final CFind cfind;
    private final CGet cget;
    private final CMove cmove;
    private final Mpps mpps;

    public Proxy(DicomConfiguration dicomConfiguration, HL7Configuration hl7configuration, String deviceName)
            throws ConfigurationException {
        try {
            init(dicomConfiguration.findDevice(deviceName));
        } catch (ConfigurationNotFoundException e) {
            LOG.error("Could not find configuration for proxy device {}", deviceName);
            throw new ConfigurationNotFoundException(e);
        } catch (ConfigurationException e) {
            LOG.error("Error loading configuration for proxy device {}", deviceName);
            throw new ConfigurationException(e);
        }
        this.dicomConfiguration = dicomConfiguration;
        this.aeCache = new ApplicationEntityCache(dicomConfiguration);
        this.hl7AppCache = new HL7ApplicationCache(hl7configuration);
        this.pixConsumer = new PIXConsumer(hl7AppCache);
        this.cecho = new CEcho();
        this.cstore = new CStore(aeCache, "*");
        this.stgcmt = new StgCmt(aeCache);
        this.cfind = new CFind(aeCache, pixConsumer, "1.2.840.10008.5.1.4.1.2.1.1", "1.2.840.10008.5.1.4.1.2.2.1",
                "1.2.840.10008.5.1.4.1.2.3.1", "1.2.840.10008.5.1.4.31");
        this.cget = new CGet(aeCache, pixConsumer, "1.2.840.10008.5.1.4.1.2.1.3", "1.2.840.10008.5.1.4.1.2.2.3",
                "1.2.840.10008.5.1.4.1.2.3.3");
        this.cmove = new CMove(aeCache, pixConsumer, "1.2.840.10008.5.1.4.1.2.1.2", "1.2.840.10008.5.1.4.1.2.2.2",
                "1.2.840.10008.5.1.4.1.2.3.2");
        this.mpps = new Mpps();
        device.setDimseRQHandler(serviceRegistry());
        device.setAssociationHandler(new ProxyAssociationHandler(aeCache));
        setConfigurationStaleTimeout();
        Proxy.instance = this;
    }

    public PIXConsumer getPixConsumer() {
        return pixConsumer;
    }

    public void setPixConsumer(PIXConsumer pixConsumer) {
        this.pixConsumer = pixConsumer;
    }

    @Override
    public void start() throws Exception {
        if (isRunning())
            return;

        scheduler = new Scheduler(aeCache, device, new AuditLog(device.getDeviceExtension(AuditLogger.class)));
        resetSpoolFiles("start-up");
        super.start();
        scheduler.start();
        log(AuditMessages.EventTypeCode.ApplicationStart);
    }

    @Override
    public void stop() {
        if (!isRunning())
            return;

        scheduler.stop();
        super.stop();
        try {
            resetSpoolFiles("shut-down");
        } catch (IOException e) {
            LOG.error("Error reseting spool file: {}", e.getMessage());
            if (LOG.isDebugEnabled())
                e.printStackTrace();
        }
        log(EventTypeCode.ApplicationStop);
    }

    synchronized public void restart() throws Exception {
        stop();
        reload();
        start();
    }

    private void log(EventTypeCode eventType) {
        AuditLogger logger = device.getDeviceExtension(AuditLogger.class);
        if (logger != null && logger.isInstalled()) {
            Calendar timeStamp = logger.timeStamp();
            try {
                logger.write(timeStamp, createApplicationActivityMessage(logger, timeStamp, eventType));
            } catch (Exception e) {
                LOG.error("Failed to write audit log message: " + e.getMessage());
                if(LOG.isDebugEnabled())
                    e.printStackTrace();
            }
        }
    }

    
    private AuditMessage createApplicationActivityMessage(AuditLogger logger, Calendar timeStamp,
            EventTypeCode eventType) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(createEventIdentification(EventID.ApplicationActivity, 
                EventActionCode.Execute,
                timeStamp, 
                EventOutcomeIndicator.Success, 
                null, 
                eventType));
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        msg.getActiveParticipant().add(logger.createActiveParticipant(true, RoleIDCode.Application));
        return msg;
    }

    protected DicomServiceRegistry serviceRegistry() {
        DicomServiceRegistry dcmService = new DicomServiceRegistry();
        dcmService.addDicomService(cecho);
        dcmService.addDicomService(cstore);
        dcmService.addDicomService(stgcmt);
        dcmService.addDicomService(cfind);
        dcmService.addDicomService(cget);
        dcmService.addDicomService(cmove);
        dcmService.addDicomService(mpps);
        return dcmService;
    }

    @Override
    public void reload() throws Exception {
        scheduler.stop();
        device.reconfigure(dicomConfiguration.findDevice(device.getDeviceName()));
        if (isRunning())
            device.rebindConnections();
        setConfigurationStaleTimeout();
        scheduler.start();
    }

    private void setConfigurationStaleTimeout() {
        int staleTimeout = device.getDeviceExtension(ProxyDeviceExtension.class).getConfigurationStaleTimeout();
        aeCache.setStaleTimeout(staleTimeout);
        hl7AppCache.setStaleTimeout(staleTimeout);
    }

    private void resetSpoolFiles(String action) throws IOException {
        Collection<ApplicationEntity> proxyAEs = instance.getDevice().getApplicationEntities();
        for (ApplicationEntity ae : proxyAEs) {
            ProxyAEExtension proxyAEE = ae.getAEExtension(ProxyAEExtension.class);
            if (proxyAEE != null) {
                LOG.info("Reset spool files for {} on {}", ae.getAETitle(), action);
                renameSndFiles(proxyAEE.getCStoreDirectoryPath(), action);
                deletePartFiles(proxyAEE.getCStoreDirectoryPath(), action);
                renameSndFiles(proxyAEE.getNactionDirectoryPath(), action);
                deletePartFiles(proxyAEE.getNactionDirectoryPath(), action);
                renameSndFiles(proxyAEE.getNCreateDirectoryPath(), action);
                deletePartFiles(proxyAEE.getNCreateDirectoryPath(), action);
                renameSndFiles(proxyAEE.getNSetDirectoryPath(), action);
                deletePartFiles(proxyAEE.getNSetDirectoryPath(), action);
            }
        }
    }

    private void renameSndFiles(File path, String action) {
        for (String calledAET : path.list(dirFilter())) {
            File dir = new File(path, calledAET);
            File[] sndFiles = dir.listFiles(sndFileFilter());
            for (File sndFile : sndFiles) {
                String sndFileName = sndFile.getPath();
                File dst = new File(sndFileName.substring(0, sndFileName.length() - 4));
                if (sndFile.renameTo(dst))
                    LOG.info("Rename {} to {} on {}", new Object[] { sndFile.getPath(), dst.getPath(), action });
                else
                    LOG.info("Failed to rename {} to {} on {}",
                            new Object[] { sndFile.getPath(), dst.getPath(), action });
            }
        }
    }

    private FilenameFilter sndFileFilter() {
        return new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".snd");
            }
        };
    }

    private void deletePartFiles(File path, String action) {
        for (String partFileName : path.list(partFileFilter())) {
            File partFile = new File(path, partFileName);
            if (partFile.delete())
                LOG.info("Delete {} on {}", partFile.getPath(), action);
            else
                LOG.info("Failed to delete {} on {}", partFile.getPath(), action);
        }
    }

    private FilenameFilter partFileFilter() {
        return new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".part");
            }
        };
    }

    private FilenameFilter dirFilter() {
        return new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        };
    }

}
