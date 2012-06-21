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
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.Status;
import org.dcm4che.net.TransferCapability.Role;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.mc.net.ForwardCFindRQ;
import org.dcm4chee.proxy.mc.net.ForwardRule;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 */
public class CFindSCPImpl extends DicomService {

    public CFindSCPImpl(String... sopClasses) {
        super(sopClasses);
    }

    @Override
    public void onDimseRQ(final Association asAccepted, final PresentationContext pc, Dimse dimse, Attributes rq,
            Attributes keys) throws IOException {
        if (dimse != Dimse.C_FIND_RQ)
            throw new DicomServiceException(Status.UnrecognizedOperation);

        ProxyApplicationEntity pae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
        pae.coerceDataset(asAccepted.getRemoteAET(), Tag.AffectedSOPClassUID, Role.SCU, Dimse.C_FIND_RQ, keys);
        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        if (asInvoked == null) {
            Association[] fwdAssocs = openAssociations(asAccepted, rq);
            if (fwdAssocs.length == 0)
                    throw new DicomServiceException(Status.UnableToProcess);
            
            try {
                new ForwardCFindRQ(asAccepted, pc, rq, keys, fwdAssocs).execute();
            } catch (InterruptedException e) {
                LOG.debug("Unexpected exception: " + e.getMessage());
            } finally {
                close(fwdAssocs);
            }
        } else
            try {
                new ForwardCFindRQ(asAccepted, pc, rq, keys, asInvoked).execute();
            } catch (InterruptedException e) {
                LOG.debug("Unexpected exception: " + e.getMessage());
            }
    }

    private void close(Association... fwdAssocs) {
        for (Association as : fwdAssocs) {
            if (as != null && as.isReadyForDataTransfer()) {
                try {
                    as.waitForOutstandingRSP();
                    as.release();
                } catch (Exception e) {
                    LOG.debug("Unexpected exception: " + e.getMessage());
                }
            }
        }
    }

    private Association[] openAssociations(Association asAccepted, Attributes rq) {
        ProxyApplicationEntity pae = (ProxyApplicationEntity) asAccepted.getApplicationEntity();
        List<ForwardRule> forwardRules = pae.filterForwardRulesOnDimseRQ(asAccepted, rq, Dimse.C_STORE_RQ);
        HashMap<String, String> aets = pae.getAETsFromForwardRules(asAccepted, forwardRules);
        List<Association> fwdAssocs = new ArrayList<Association>(aets.size());
        for (Entry<String, String> entry : aets.entrySet()) {
            String callingAET = entry.getValue();
            String calledAET = entry.getKey();
            AAssociateRQ aarq = asAccepted.getAAssociateRQ();
            aarq.setCallingAET(callingAET);
            aarq.setCalledAET(calledAET);
            Association asInvoked = null;
            try {
                asInvoked = pae.connect(pae.getDestinationAE(calledAET), aarq);
                fwdAssocs.add(asInvoked);
            } catch (IncompatibleConnectionException e) {
                LOG.error("Unable to connect to {} ({})", new Object[] {calledAET, e.getMessage()} );
            } catch (GeneralSecurityException e) {
                LOG.error("Failed to create SSL context ({})", new Object[]{e.getMessage()});
            } catch (ConfigurationException e) {
                LOG.error("Unable to load configuration for destination AET ({})", new Object[]{e.getMessage()});
            } catch (ConnectException e) {
                LOG.error("Unable to connect to {} ({})", new Object[] {calledAET, e.getMessage()} );
            } catch (Exception e) {
                LOG.debug("Unexpected exception: " + e.getMessage());
            }
        }
        return fwdAssocs.toArray(new Association[fwdAssocs.size()]);
    }
}
