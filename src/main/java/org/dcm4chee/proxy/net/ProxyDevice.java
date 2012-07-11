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

package org.dcm4chee.proxy.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.conf.api.DicomConfiguration;
import org.dcm4che.io.TemplatesCache;
import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Device;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class ProxyDevice extends Device {

    private static final long serialVersionUID = -7370790158878613899L;

    private Integer schedulerInterval;
    private DicomConfiguration dicomConf;
    private final TemplatesCache templateCache = new TemplatesCache();
    private int forwardThreads;
    private ThreadPoolExecutor fileForwardingExecutor;

    public ThreadPoolExecutor getFileForwardingExecutor() {
        return fileForwardingExecutor;
    }

    public void setFileForwardingExecutor(ThreadPoolExecutor executor) {
        this.fileForwardingExecutor = executor;
    }

    public int getForwardThreads() {
        return forwardThreads;
    }

    public void setForwardThreads(int forwardThreads) {
        if (forwardThreads == 0)
            throw new IllegalArgumentException("ForwardThreads cannot be 0");

        fileForwardingExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(forwardThreads);
        this.forwardThreads = forwardThreads;
    }
    
    public Templates getTemplates(String uri) throws TransformerConfigurationException {
        return templateCache.get(uri);
    }

    public ProxyDevice(String name) {
        super(name);
        setForwardThreads(256);
    }

    public void setSchedulerInterval(Integer schedulerInterval) {
        this.schedulerInterval = schedulerInterval;
    }

    public Integer getSchedulerInterval() {
        return schedulerInterval;
    }

    public DicomConfiguration getDicomConf() {
        return dicomConf;
    }

    public void setDicomConf(DicomConfiguration dicomConf) {
        this.dicomConf = dicomConf;
    }

    public ApplicationEntity findApplicationEntity(String aet) throws ConfigurationException {
        return dicomConf.findApplicationEntity(aet);
    }

}
