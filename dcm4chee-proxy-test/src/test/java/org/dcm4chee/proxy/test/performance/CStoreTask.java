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
import java.io.PrintWriter;
import org.dcm4che3.conf.prefs.PreferencesDicomConfiguration;
import org.dcm4che3.tool.storescu.test.StoreResult;
import org.dcm4che3.tool.storescu.test.StoreTest;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */

    public class CStoreTask implements Runnable {
        PrintWriter writer;
        String cStoreTaskName;
        int cStoreTaskPort;
        StoreResult result;


        CStoreTask(PrintWriter writer, String cStoreTaskName, int port) {
            this.writer = writer;
            this.cStoreTaskName = cStoreTaskName;
            this.cStoreTaskPort = port;
        }

        @Override
        public void run() {
            // start storage commitment
            try {
                PreferencesDicomConfiguration conf = new PreferencesDicomConfiguration();
                StoreResult result = new StoreTest("0.0.0.0",22222,"XD0000000ABC",new File(Thread.currentThread()
                        .getContextClassLoader().getResource("test.dcm").getFile()).getParentFile()).store(this.cStoreTaskName,"test.dcm");
                this.result = result;
                conf.close();
            } catch (Exception e) {
                e.printStackTrace();
                
            }
            
        }

    }