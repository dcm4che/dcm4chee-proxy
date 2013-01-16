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

package org.dcm4chee.proxy.conf;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;

import org.dcm4che.conf.api.ApplicationEntityCache;
import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.hl7.HL7ApplicationCache;
import org.dcm4che.conf.api.hl7.HL7Configuration;
import org.dcm4che.io.TemplatesCache;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.hl7.HL7Device;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyDevice extends HL7Device {

    private static final long serialVersionUID = -7370790158878613899L;
    
    public static final int DEFAULT_FORWARD_THREADS = 1;
    public static final int DEFAULT_SCHEDULER_INTERVAL = 60;

    private Integer schedulerInterval;
    private HL7Configuration dicomConf;
    private transient TemplatesCache templateCache;
    private int forwardThreads;
    private transient ThreadPoolExecutor fileForwardingExecutor;
    private int configurationStaleTimeout;
    private transient ApplicationEntityCache aeCache;
    private transient HL7ApplicationCache hl7AppCache;

    public ThreadPoolExecutor getFileForwardingExecutor() {
        if (fileForwardingExecutor == null)
            fileForwardingExecutor = (ThreadPoolExecutor) Executors
                    .newFixedThreadPool(forwardThreads);
        return fileForwardingExecutor;
    }

    public int getForwardThreads() {
        return forwardThreads;
    }

    public void setForwardThreads(int forwardThreads) {
        if (forwardThreads == 0)
            throw new IllegalArgumentException("ForwardThreads cannot be 0");
        this.forwardThreads = forwardThreads;
    }
    
    public Templates getTemplates(String uri) throws TransformerConfigurationException {
        if (templateCache == null)
            templateCache = new TemplatesCache();
        return templateCache.get(uri);
    }

    public ProxyDevice(String name) {
        super(name);
        setForwardThreads(1);
    }

    public void setSchedulerInterval(Integer schedulerInterval) {
        this.schedulerInterval = schedulerInterval;
    }

    public Integer getSchedulerInterval() {
        return schedulerInterval;
    }

    public HL7Configuration getDicomConf() {
        return dicomConf;
    }

    public void setDicomConf(HL7Configuration dicomConf) {
        this.dicomConf = dicomConf;
    }

    public int getConfigurationStaleTimeout() {
        return configurationStaleTimeout;
    }

    public void setConfigurationStaleTimeout(int configurationStaleTimeout) {
        this.configurationStaleTimeout = configurationStaleTimeout;
    }

    public ApplicationEntity findApplicationEntity(String aet) throws ConfigurationException {
        return dicomConf.findApplicationEntity(aet);
    }

    public ApplicationEntityCache getAeCache() {
        return aeCache;
    }

    public void setAeCache(ApplicationEntityCache aeCache) {
        this.aeCache = aeCache;
        aeCache.setStaleTimeout(configurationStaleTimeout);
    }

    public HL7ApplicationCache getHl7AppCache() {
        return hl7AppCache;
    }

    public void setHl7AppCache(HL7ApplicationCache hl7AppCache) {
        this.hl7AppCache = hl7AppCache;
    }
}
