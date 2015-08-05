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

package org.dcm4chee.proxy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
public class ProxyCleanUpScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyCleanUpScheduler.class);
    private final Device device;
    private ScheduledFuture<?> timer;
    private ScheduledExecutorService scheduledExecutor;

    public ProxyCleanUpScheduler(Device device) {
        this.device = device;
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        long period = device.getDeviceExtension(ProxyDeviceExtension.class).getCleanerInterval();
        timer = scheduledExecutor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                for (ApplicationEntity ae : device.getApplicationEntities()) {
                    ProxyAEExtension prxAExt = ae.getAEExtension(ProxyAEExtension.class);
                    if (prxAExt != null) {
                        try {
                            File cstoreDir = prxAExt.getCStoreDirectoryPath();
                            ProxyDeviceExtension prxDevExt = device.getDeviceExtension(ProxyDeviceExtension.class);
                            for(File file : cstoreDir.listFiles()) {
                                if(!file.isDirectory() && file.getName().endsWith(".part"))
                                    deleteIfPossible(prxDevExt, file);
                            }
                        } catch (IOException e) {
                            LOG.error("Part File CleanUP :Failed to "
                                    + "get files in spool directory"
                                    + "- reason {}", e);
                        }
                    }
                }
            }

            private void deleteIfPossible(ProxyDeviceExtension prxDevExt, File file) {
                Date timeToDelete = new Date(file.lastModified()
                        + (prxDevExt.getMaxTimeToKeepPartFilesInSeconds() * 1000));
                Date timeNow = new Date(System.currentTimeMillis());
                if(timeNow.after(timeToDelete))
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e) {
                        LOG.error("Part File CleanUP :Failed to "
                                + "delete part file on path {} -"
                                + " reason {}", file.toPath(), e);
                    }
            }
        }, period, period, TimeUnit.SECONDS);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel(true);
            timer = null;
        }
    }
}
