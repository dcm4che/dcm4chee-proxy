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

package org.dcm4chee.proxy.mc.net;

import java.io.File;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dcm4che.net.Device;
import org.dcm4che.net.service.InstanceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ForwardTaskListener implements MessageListener {

    protected static final Logger LOG = 
        LoggerFactory.getLogger(ForwardTaskListener.class);

    private static QueueConnectionFactory qconFactory;
    private static Queue queue;
    private final Device device;
    private QueueConnection qconn;
    private QueueSession qsession;
    private QueueReceiver qreceiver;

    public ForwardTaskListener(Device device) {
        this.device = device;
    }

    public void start() throws JMSException, NamingException {
        if (qconFactory == null) {
            InitialContext ctx = new InitialContext();
            try {
                qconFactory = (QueueConnectionFactory) ctx.lookup("ConnectionFactory");
                queue = (Queue) ctx.lookup("queue/ForwardTaskQueue");
            } finally {
                ctx.close();
            }
        }
        qconn = qconFactory.createQueueConnection();
        qsession = qconn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        qreceiver = qsession.createReceiver(queue);
        qreceiver.setMessageListener(this);
        qconn.start();
    }

    public void stop() throws JMSException {
        qreceiver.close();
        qsession.close();
        qconn.close();
    }


    public static void schedule(ForwardTask ft) {
        try {
            QueueConnection qcon = qconFactory.createQueueConnection();
            QueueSession qsession = qcon.createQueueSession(false, 0);
            QueueSender qsender = qsession.createSender(queue);
            ObjectMessage message = qsession.createObjectMessage(ft);
            message.setLongProperty("_JBM_SCHED_DELIVERY", ft.getForwardTime());
            qsender.send(message);
            qsender.close();
            qsession.close();
            qcon.close();
        } catch (JMSException e) {
            LOG.error("Failed to schedule forward task", e);
            deleteFiles(ft);
        }
    }

    private static void deleteFiles(ForwardTask ft) {
        for (InstanceLocator inst : ft.getInstances()) {
            File file = inst.getFile();
            if (file.delete()) {
                LOG.info("W-DELETE " + file);
            } else {
                LOG.warn("Failed to delete " + file);
            }
        }
    }

    @Override
    public void onMessage(Message message) {
        ForwardTask ft;
        try {
            ft = (ForwardTask) ((ObjectMessage) message).getObject();
        } catch (JMSException e) {
            LOG.error("Unexpected exception:", e);
            return;
        }
        process(ft);
        deleteFiles(ft);
    }

    private void process(ForwardTask ft) {
        ProxyApplicationEntity ae = (ProxyApplicationEntity)
                device.getApplicationEntity(ft.getProxyAET());
        // TODO Auto-generated method stub
        
    }

}
