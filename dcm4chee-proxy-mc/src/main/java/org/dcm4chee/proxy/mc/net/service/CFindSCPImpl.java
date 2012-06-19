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

package org.dcm4chee.proxy.mc.net.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.Association;
import org.dcm4che.net.CancelRQHandler;
import org.dcm4che.net.Commands;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.BasicCFindSCP;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.mc.net.ForwardRule;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class CFindSCPImpl extends BasicCFindSCP {

    public CFindSCPImpl(String... sopClasses) {
        super(sopClasses);
    }

    @Override
    public void onDimseRQ(final Association asAccepted, final PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes keys) throws IOException {
        if (dimse != Dimse.C_FIND_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null)
            processForwardRules(asAccepted, pc, dimse, rq, keys);
        else
            try {
                forwardCFindRQ(asAccepted, asInvoked, pc, rq, keys);
            } catch (InterruptedException e) {
                throw new DicomServiceException(Status.UnableToProcess, e);
            }
    }

    private void processForwardRules(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes keys) throws IOException {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
        List<ForwardRule> forwardRules = pae.filterForwardRulesOnDimseRQ(asAccepted, rq, dimse);
        HashMap<String, String> aets = pae.getAETsFromForwardRules(asAccepted, forwardRules);
        asAccepted.setProperty(ProxyApplicationEntity.STATUS, Status.UnableToProcess);
        for (Entry<String, String> entry : aets.entrySet()) {
            String callingAET = entry.getValue();
            String calledAET = entry.getKey();
            startForwardCFindRQ(callingAET, calledAET, pae, asAccepted, pc, rq, keys);
        }
        int status = Integer.parseInt(asAccepted.getProperty(ProxyApplicationEntity.STATUS).toString());
        asAccepted.writeDimseRSP(pc, Commands.mkCFindRSP(rq, status));
    }

    private void startForwardCFindRQ(String callingAET, String calledAET, ProxyApplicationEntity pae,
            Association asAccepted, PresentationContext pc, Attributes rq, Attributes data) {
        AAssociateRQ aarq = asAccepted.getAAssociateRQ();
        aarq.setCallingAET(callingAET);
        aarq.setCalledAET(calledAET);
        Association asInvoked = null;
        try {
            asInvoked = pae.connect(pae.getDestinationAE(calledAET), aarq);
            forwardCFindRQ(asAccepted, asInvoked, pc, rq, data);
        } catch (IncompatibleConnectionException e) {
            LOG.debug("Unable to connect to " + calledAET, e);
        } catch (GeneralSecurityException e) {
            LOG.warn("Failed to create SSL context", e);
        } catch (ConfigurationException e) {
            LOG.warn("Unable to load configuration for destination AET", e);
        } catch (Exception e) {
            LOG.warn("Unexpected exception", e);
        } finally {
            if (asInvoked != null && asInvoked.isReadyForDataTransfer()) {
                try {
                    asInvoked.waitForOutstandingRSP();
                    asInvoked.release();
                } catch (Exception e) {
                    LOG.warn("Unexpected exception", e);
                }
            }
        }
    }

    private void forwardCFindRQ(final Association asAccepted, final Association asInvoked,
            final PresentationContext pc, Attributes rq, Attributes data)
            throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        int priority = rq.getInt(Tag.Priority, 0);
        int msgId = rq.getInt(Tag.MessageID, 0);
        final DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                int status = cmd.getInt(Tag.Status, -1);
                switch (status) {
                case Status.Pending:
                    writeDimseRSQP(pc, cmd, data);
                    break;

                case Status.Success:
                    asAccepted.setProperty(ProxyApplicationEntity.STATUS, status);
                    break;

                default:
                    break;
                }
            }

            private void writeDimseRSQP(PresentationContext pc, Attributes cmd, Attributes data) {
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.warn("Failed to forward C-FIND-RSP", e);
                }
            }
        };
        asAccepted.addCancelRQHandler(msgId, new CancelRQHandler() {

            @Override
            public void onCancelRQ(Association association) {
                try {
                    rspHandler.cancel(asInvoked);
                } catch (IOException e) {
                    LOG.warn(asAccepted + ": unexpected exception", e);
                }
            }
        });
        asInvoked.cfind(cuid, priority, data, tsuid, rspHandler);
    }

}
