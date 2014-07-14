
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
package org.dcm4chee.proxy.test.stgcmt;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.tool.stgcmtscu.StgCmtSCU;
import org.dcm4chee.proxy.test.performance.StgCmtTask;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * @author Umberto Cappellini <umberto.cappellini@agfa.com>
 */
public class StgCmtTestTool extends StgCmtSCU{

    private String testDescription;
    private String fileName;
    private long totalSize;
    private int filesSent;
    private int warnings;    
    private int failures;
    private PrintWriter writer;
    private Device device;
    private Connection conn;
    private ApplicationEntity ae;
    private StgCmtTask parent;
    /**
     * @param stgCmtTask 
     * @param conn 
     * @param device 
     * @param writer 
     * @param testName
     * @param testDescription
     * @param fileName
     * @throws IOException 
     */
    public StgCmtTestTool(StgCmtTask stgCmtTask, Device device, Connection conn, PrintWriter writer, String testDescription, String fileName) throws IOException {
        super(device.getApplicationEntity(device.getDeviceName().toUpperCase()));
        this.testDescription = testDescription;
        this.fileName = fileName;
        this.writer = writer;
        this.device= device;
        this.conn = conn;
        this.parent = stgCmtTask;
        this.ae=device.getApplicationEntity(device.getDeviceName().toUpperCase());
    } 
    
    public void stgcmt(String host, int localport, int port, String aeTitle) throws IOException, InterruptedException,
            IncompatibleConnectionException, GeneralSecurityException, ConfigurationException {

        long t1, t2;
        t1 = System.currentTimeMillis();

        File file = new File(fileName);

        assertTrue(
                "file or directory does not exists: " + file.getAbsolutePath(),
                file.exists());
        


        // configure
        conn.setMaxOpsInvoked(0);
        conn.setMaxOpsPerformed(0);
        
        this.getAAssociateRQ().setCalledAET(aeTitle);
        this.getRemoteConnection().setHostname(host);
        this.getRemoteConnection().setPort(port);
        this.setTransferSyntaxes(new String[]{UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian, UID.ExplicitVRBigEndianRetired});
        this.setAttributes(new Attributes());
        this.setStorageDirectory(new File("."));
        
        Attributes ds = null;

            DicomInputStream dis = new DicomInputStream(file);
            try{
                ds = dis.readDataset(-1, -1);
                this.addInstance(ds);
            }
            finally{
                dis.close();
            }

        

        // create executor
        ExecutorService executorService =
                Executors.newCachedThreadPool();
        ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor();
        device.setExecutor(executorService);
        device.setScheduledExecutor(scheduledExecutorService);
        device.bindConnections();

        // open, send and wait for response
        try {
            long asTime1 = System.currentTimeMillis();
            this.open();
            long asTime2 = System.currentTimeMillis();
            writer.println("Task-"+this.testDescription+"-AssociateTime-"+(asTime2-asTime1));
            this.parent.setAssocTime((int) (asTime2-asTime1));
            sendRequests();
         } finally {
             long neventTime1 = System.currentTimeMillis();
            this.close();
            long neventTime2 = System.currentTimeMillis();
            writer.println("Task-"+this.testDescription+"-NeventTime-"+(neventTime2-neventTime1));
            this.parent.setnEventTime((int) (neventTime2-neventTime1));
            if (conn.isListening()) {
                device.waitForNoOpenConnections();
                device.unbindConnections();
            }
            executorService.shutdown();
            scheduledExecutorService.shutdown();
            t2 = System.currentTimeMillis();
        }
        writer.println("Task-"+this.testDescription+"-StgCmtTime-"+(t2-t1));
        this.parent.setTotalTime((int) (t2-t1));
    }

    @Override
    public void sendRequests() throws IOException, InterruptedException {
        super.sendRequests();
    }

}
