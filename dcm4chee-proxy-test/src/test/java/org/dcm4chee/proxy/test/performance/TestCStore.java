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

package org.dcm4chee.proxy.test.performance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */

public class TestCStore {
    private static final Logger LOG = LoggerFactory.getLogger(TestCStore.class);
    private CStoreTask[] tasks = new CStoreTask[200];
    // Print writer for log file passed to all tasks
    static PrintWriter printer = null;

    @Before
    public void init() throws FileNotFoundException, InstanceNotFoundException,
            ConfigurationException {
        initializeTasks();
        initializeConfig();
    }

    private void initializeTasks() throws FileNotFoundException {
        printer = new PrintWriter(new File("perfTestResult.txt"));
        int threadCounter = 0;
        int port = 6000;
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = new CStoreTask(printer, "CStoreTask[" + threadCounter
                    + "]", port);
            threadCounter++;
            port++;
        }
    }

    private void initializeConfig() throws InstanceNotFoundException,
            ConfigurationException {
        removeConfig();
        // create the configuration
        PreferencesDicomConfiguration conf = new PreferencesDicomConfiguration();
        for (CStoreTask task : tasks) {
            Device dev = new Device(task.cStoreTaskName);
            dev.addConnection(new Connection("dicom", "0.0.0.0",
                    task.cStoreTaskPort));
            ApplicationEntity ae = new ApplicationEntity(
                    task.cStoreTaskName.toUpperCase());
            dev.setInstalled(true);
            ae.setInstalled(true);
            ae.setAssociationAcceptor(true);
            ae.setAssociationInitiator(true);
            dev.addApplicationEntity(ae);

            ae.addConnection(dev.listConnections().get(0));
            conf.persist(dev);
            conf.sync();
            conf.close();
        }

    }

    public void removeConfig() throws InstanceNotFoundException,
            ConfigurationException {
        PreferencesDicomConfiguration conf = new PreferencesDicomConfiguration();
        for (String devName : conf.listDeviceNames()) {
            if (devName.startsWith("CStoreTask")) {
                LOG.info("removing device " + devName);
                conf.removeDevice(devName);
            }

        }
        conf.close();
    }

    @After
    public void finalizeTest() throws SecurityException, IllegalStateException,
            RollbackException, HeuristicMixedException,
            HeuristicRollbackException, SystemException,
            InstanceNotFoundException, ConfigurationException {
        LOG.info("Finished test successfully");

        LOG.info("Removing test devices");
        removeConfig();

    }

    private void collectStatistics(long testStartTime, long testEndTime,
            CStoreTask[] tasks, int filelink) throws FileNotFoundException {

        String RESULT_HEADER1 = "+--------------------------------------------------------------------------------------------+";
        String RESULT_HEADER2 = "+                              C-Store            Performance Test                           +";
        String TEST_NAME_ROW = "+                              " + filelink
                + "                           +";
        String RESULT_HEADER3 = "+----+----------------------+------+------+------+----------+--------------------------------+";
        String RESULT_COLUMNS1 = "| #  |     Task      |        Time |                     | Sucessfull CStore                |";
        String RESULT_COLUMNS2 = "| #  |     Number    |             |                     |                                  |";
        String RESULT_FOOTER1 = "+----+-----------------------------+---------------------+------------+----------------------+";
        String RESULT_PRX = "|                             Proxy Throughput per second                                      |";
        String RESULT_FOOTER2 = "+--------------------------------------------------------+-----------------------------------+";
        PrintWriter resultWriter = new PrintWriter(new File("" + filelink));
        resultWriter.println(RESULT_HEADER1);
        resultWriter.println(RESULT_HEADER2);
        resultWriter.println(TEST_NAME_ROW);
        resultWriter.println(RESULT_HEADER3);
        resultWriter.println(RESULT_COLUMNS1);
        resultWriter.println(RESULT_COLUMNS2);
        resultWriter.println(RESULT_FOOTER1 + "\n");
        for (int i = 0; i < tasks.length; i++) {
            resultWriter.print(" " + "   | " + tasks[i].cStoreTaskName + " |");
            resultWriter.print("  " + tasks[i].result.getTime() + "       |");
            resultWriter.print("             " + "        |");
            resultWriter.print("         "
                    + (tasks[i].result.getFailures() == 0 ? "true" : "false")
                    + "          |");
            resultWriter.println();
        }
        resultWriter.println(RESULT_HEADER1);
        resultWriter.println(RESULT_PRX);
        resultWriter.println(RESULT_FOOTER2);
        long totalTime = (testEndTime - testStartTime) / 1000;
        double throughPut = 200 / totalTime;
        resultWriter.println("|                             " + throughPut
                + "                                      |");
        resultWriter.println(RESULT_FOOTER2);
        resultWriter.close();
    }

    @Test
    public void testDrillTheProxy() throws ConfigurationException, IOException {
        int uniqueID = 0;
        long t1 = System.currentTimeMillis();
        long tend = System.currentTimeMillis() + 3600000;
        FileWriter resultWriter = new FileWriter(new File("TestReport"
                + "Test CStore 1 destination" + ".html"));
        while (System.currentTimeMillis() < tend) {

            uniqueID++;
            if (uniqueID == 1)
                resultWriter.write("<html><body><div><a href=" + uniqueID
                        + ">Cycle " + new Date(System.currentTimeMillis())
                        + "</a>");
            else
                resultWriter.write("<br><a href=" + uniqueID + ">Cycle "
                        + new Date(System.currentTimeMillis()) + "</a>");

            Integer threadCounter = 0;
            BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(
                    200);

            CustomThreadPoolExecutor executor = new CustomThreadPoolExecutor(5,
                    10, 99999, TimeUnit.MILLISECONDS, blockingQueue);

            // Let start all core threads initially
            executor.prestartAllCoreThreads();

            while (true) {

                // Adding threads one by one
                executor.execute(tasks[threadCounter]);
                threadCounter++;
                if (threadCounter == 200)
                    break;

            }

            while (executor.getActiveCount() != 0
                    && executor.getCompletedTaskCount() != 200)
                ;

            printer.println("#########################################################################################################################################################################################################");
            printer.close();
            long t2 = System.currentTimeMillis();
            collectStatistics(t1, t2, tasks, uniqueID);
            Assert.assertTrue(true);
            if(!executor.isShutdown())
                executor.shutdownNow();
        }
        resultWriter.write("<br><a href=" + uniqueID + ">Cycle "
                + new Date(System.currentTimeMillis())
                + "</a></div></body></html>");
        resultWriter.close();
    }

    class CustomThreadPoolExecutor extends ThreadPoolExecutor {
        Date date1;
        Date date2;
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

        public CustomThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                long keepAliveTime, TimeUnit unit,
                BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);

        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                ((CStoreTask) r).writer.println("Error Task - "
                        + ((CStoreTask) r).cStoreTaskName);
            }
        }

    }

}
