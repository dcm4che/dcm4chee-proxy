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
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.management.InstanceNotFoundException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che3.conf.prefs.PreferencesUtils;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.tool.dcmqrscp.DcmQRSCP;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.test.stgcmt.StgCmtTestTool;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */
@RunWith(Arquillian.class)
public class TestStgcmt {
    private static final Logger LOG = LoggerFactory.getLogger(TestStgcmt.class);
    private StgCmtTask[] tasks = new StgCmtTask[200];
    // Print writer for log file passed to all tasks
    static PrintWriter printer = null;

    Thread t1 = new Thread();
    Thread t2 = new Thread();

    public void init() throws FileNotFoundException, InstanceNotFoundException,
            ConfigurationException {
        initializeTasks();
        initializeConfig();
    }

    @Deployment(testable = true)
    public static WebArchive createDeployment() throws IOException {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");

        war.addClasses(TestStgcmt.class, StgCmtTestTool.class, StgCmtTask.class);
        File testPom = new File("testpom.xml");

        File[] archs = Maven.resolver().offline().loadPomFromFile(testPom)
                .importRuntimeAndTestDependencies().resolve()
                .withTransitivity().as(File.class);
        war.addAsLibraries(archs);
        war.addAsWebInfResource("jboss-web.xml");
        war.addAsWebInfResource("web.xml");
        war.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        war.as(ZipExporter.class).exportTo(new File("test-export.war"), true);
        return war;
    }

    private void initializeTasks() throws FileNotFoundException {
        printer = new PrintWriter(new File("perfTestResult.txt"));
        int threadCounter = 0;
        int port = 6000;
        for (int i = 0; i < tasks.length; i++) {
            tasks[i] = new StgCmtTask(printer, "StgCmtTask[" + threadCounter
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
        for (StgCmtTask task : tasks) {
            Device dev = new Device(task.stgCmtTaskName);
            dev.addConnection(new Connection("dicom", "127.0.0.1",
                    task.stgCmtTaskPort));
            ApplicationEntity ae = new ApplicationEntity(
                    task.stgCmtTaskName.toUpperCase());
            dev.setInstalled(true);
            ae.setInstalled(true);
            ae.setAssociationAcceptor(true);
            ae.setAssociationInitiator(true);
            dev.addApplicationEntity(ae);

            ae.addConnection(dev.listConnections().get(0));
            conf.persist(dev);

            List<ForwardRule> rules = new ArrayList<ForwardRule>();
            List<String> uris = new ArrayList<String>();
            uris.add("aet:DCMQRSCP");
            uris.add("aet:DCM4CHEE");
            ForwardRule rule = new ForwardRule();
            rule.setCommonName("test");
            rule.setRunPIXQuery(false);
            rule.setDestinationURIs(uris);
            rule.setUseCallingAET("DCM4CHEE-PROXY");
            rules.add(rule);
            Preferences prefs = conf
                    .getRootPrefs()
                    .node("dicomConfigurationRoot/dicomDevicesRoot/dcm4chee-proxy/dcmNetworkAE/DCM4CHEE-PROXY");
            Preferences rulesNode = prefs.node("dcmForwardRule");
            for (ForwardRule r : rules) {
                PreferencesUtils.storeNotEmpty(
                        rulesNode.node(r.getCommonName()),
                        "labeledURI",
                        r.getDestinationURI().toArray(
                                new String[r.getDestinationURI().size()]));
                PreferencesUtils.storeNotNull(
                        rulesNode.node(r.getCommonName()),
                        "dcmUseCallingAETitle", r.getUseCallingAET());
                PreferencesUtils.storeNotNull(
                        rulesNode.node(r.getCommonName()), "cn",
                        r.getCommonName());
                PreferencesUtils.storeNotDef(rulesNode.node(r.getCommonName()),
                        "dcmPIXQuery", r.isRunPIXQuery(), Boolean.FALSE);
            }
            conf.sync();
            conf.close();
        }

    }

    public void removeConfig() throws InstanceNotFoundException,
            ConfigurationException {
        PreferencesDicomConfiguration conf = new PreferencesDicomConfiguration();
        for (String devName : conf.listDeviceNames()) {
            if (devName.startsWith("StgCmtTask")) {
                LOG.info("removing device " + devName);
                conf.removeDevice(devName);
            }

        }
        conf.close();
    }

    @Before
    @RunAsClient
    public void setup() {

        try {
            LOG.info("Starting DCMQRSCP ...");
            t1 = new Thread(new Runnable() {

                @Override
                public void run() {
                    DcmQRSCP.main(new String[] { "-b",
                            "DCMQRSCP@localhost:11113", "--dicomdir", "./test" });
                }
            });
            t1.start();
            LOG.info("Starting DCM4CHEE ...");
            t2 = new Thread(new Runnable() {

                @Override
                public void run() {
                    DcmQRSCP.main(new String[] { "-b",
                            "DCM4CHEE@localhost:11112", "--dicomdir", "./test" });
                }
            });
            t2.start();
            init();
        } catch (Exception e) {
            System.exit(1);
        }
    }

    @SuppressWarnings("deprecation")
    @After
    @RunAsClient
    public void finalizeTest() throws SecurityException, IllegalStateException,
            RollbackException, HeuristicMixedException,
            HeuristicRollbackException, SystemException,
            InstanceNotFoundException, ConfigurationException {
        t1.stop();
        t2.stop();
        LOG.info("Finished test successfully");

        LOG.info("Removing test devices");
        removeConfig();

    }

    private void collectStatistics(long testStartTime, long testEndTime,
            StgCmtTask[] tasks, String testName) throws FileNotFoundException {

        String RESULT_HEADER1 = "+--------------------------------------------------------------------------------------------+";
        String RESULT_HEADER2 = "+                              Storage Commitment Performance Test                           +";
        String TEST_NAME_ROW = "+                              " + testName
                + "                           +";
        String RESULT_HEADER3 = "+----+----------------------+------+------+------+----------+--------------------------------+";
        String RESULT_COLUMNS1 = "| #  |     Task      | Assoc. Time |Time Taken to Receive| Sucessfull Storage                |";
        String RESULT_COLUMNS2 = "| #  |     Number    |             |       N-Event       |     commitment                    |";
        String RESULT_FOOTER1 = "+----+-----------------------------+---------------------+------------+----------------------+";
        String RESULT_PRX = "|     Total Time Taken To finish 200 StgCmt in seconds   |    Proxy Throughput per second    |";
        String RESULT_FOOTER2 = "+--------------------------------------------------------+-----------------------------------+";
        PrintWriter resultWriter = new PrintWriter(new File("TestReport"
                + testName + ".txt"));
        resultWriter.println(RESULT_HEADER1);
        resultWriter.println(RESULT_HEADER2);
        resultWriter.println(TEST_NAME_ROW);
        resultWriter.println(RESULT_HEADER3);
        resultWriter.println(RESULT_COLUMNS1);
        resultWriter.println(RESULT_COLUMNS2);
        resultWriter.println(RESULT_FOOTER1 + "\n");
        for (int i = 0; i < tasks.length; i++) {
            if (tasks[i].getTotalTime() > 0) {

                resultWriter.print(" " + i + "   | " + tasks[i].stgCmtTaskName
                        + " |");
                resultWriter.print("  " + tasks[i].assocTime + "       |");
                resultWriter.print("       " + tasks[i].nEventTime
                        + "        |");
                resultWriter.print("         true          |");
            }
            if (Integer.valueOf(tasks[i].getTotalTime()) == null
                    || tasks[i].getTotalTime() <= 0) {
                resultWriter.print(" " + i + "   | " + tasks[i].stgCmtTaskName
                        + " |");
                resultWriter.print("  " + tasks[i].assocTime + "       |");
                resultWriter.print("       " + tasks[i].nEventTime
                        + "        |");
                resultWriter.print("         false          |");
            }
            resultWriter.println();
        }
        resultWriter.println(RESULT_HEADER1);
        resultWriter.println(RESULT_PRX);
        resultWriter.println(RESULT_FOOTER2);
        long totalTime = (testEndTime - testStartTime) / 1000;
        double throughPut = 200 / totalTime;
        resultWriter.println("                        " + totalTime
                + "                   |           " + throughPut
                + "           ");
        resultWriter.println(RESULT_FOOTER2);
        resultWriter.close();
    }

    @Test
    @RunAsClient
    public void testDrillTheProxy() throws FileNotFoundException,
            ConfigurationException {
        long t1 = System.currentTimeMillis();
        Integer threadCounter = 0;
        BlockingQueue<Runnable> blockingQueue = new ArrayBlockingQueue<Runnable>(
                200);

        CustomThreadPoolExecutor executor = new CustomThreadPoolExecutor(50,
                70, 99999, TimeUnit.MILLISECONDS, blockingQueue);

        // Let start all core threads initially
        executor.prestartAllCoreThreads();

        printer.println("#Proxy Performance Test "
                + new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
                        .format(new Date())
                + "-StgCmtTaskCount=200 - Destinations=2 - Proxy Scheduler thread config: ForwardThreads:10 , PollInterval:5 sec #");
        printer.println("#########################################################################################################################################################################################################");
        while (true) {

            // Adding threads one by one
            executor.execute(tasks[threadCounter]);
            // printer.println("Adding StgCmtTask - "
            // + tasks[threadCounter].stgCmtTaskName + " to the pool");
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
        collectStatistics(t1, t2, tasks,
                "Test Storage commitment 2 destinations");
        Assert.assertTrue(true);
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
            // date1 = Calendar.getInstance().getTime();
            // ((StgCmtTask) r).writer.println("Started Task - "
            // + ((StgCmtTask) r).stgCmtTaskName + " at "
            // + df.format(date1));

        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            if (t != null) {
                ((StgCmtTask) r).writer.println("Error Task - "
                        + ((StgCmtTask) r).stgCmtTaskName);
            }
            // date2 = Calendar.getInstance().getTime();
            // Calendar calDiff = Calendar.getInstance();
            // calDiff.setTimeInMillis(date2.getTime() - date1.getTime());
            // ((StgCmtTask) r).writer.println("Finished Task - "
            // + ((StgCmtTask) r).stgCmtTaskName + " at "
            // + df.format(date2) +
            // "- Time taken to finish task  in seconds -  "
            // + calDiff.get(Calendar.MILLISECOND));
        }

    }

}
