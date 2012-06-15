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

import org.dcm4che.data.Attributes;
import org.dcm4che.data.Tag;
import org.dcm4che.data.UID;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseRSPHandler;
import org.dcm4che.net.Status;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.service.DicomService;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.mc.net.ProxyApplicationEntity;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 */
public class MppsSCPImpl extends DicomService {
    
    public MppsSCPImpl(){
        super(UID.ModalityPerformedProcedureStepSOPClass);
    }
    
    @Override
    public void onDimseRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd,
            Attributes data) throws IOException {
        switch (dimse) {
        case N_CREATE_RQ:
            onNCreateRQ(asAccepted, pc, dimse, cmd, data);
            break;
        case N_SET_RQ:
            onNSetRQ(asAccepted, pc, dimse, cmd, data);
            break;
        default:
            super.onDimseRQ(asAccepted, pc, dimse, cmd, data);
        }
    }
    
    private void onNCreateRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd,
            Attributes data) throws IOException {
        Association asInvoked = (Association) asAccepted.getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        try {
            forwardNCreateRQ(asAccepted, asInvoked, pc, dimse, cmd, data);
        } catch (InterruptedException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }

    private void forwardNCreateRQ(final Association asAccepted, Association asInvoked,
            final PresentationContext pc, Dimse dimse, Attributes rq, Attributes data)
            throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.AffectedSOPClassUID);
        String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.warn(asAccepted + ": error forwarding N-CREATE-RQ", e);
                }
            }
        };
        asInvoked.ncreate(cuid, iuid, data, tsuid, rspHandler);
    }
    
    private void onNSetRQ(Association asAccepted, PresentationContext pc, Dimse dimse, Attributes cmd,
            Attributes data) throws IOException {
        Association asInvoked = (Association) asAccepted
                .getProperty(ProxyApplicationEntity.FORWARD_ASSOCIATION);
        try {
            forwardNSetRQ(asAccepted, asInvoked, pc, dimse, cmd, data);
        } catch (InterruptedException e) {
            throw new DicomServiceException(Status.UnableToProcess, e);
        }
    }
    
    private void forwardNSetRQ(final Association asAccepted, Association asInvoked,
            final PresentationContext pc, Dimse dimse, Attributes rq, Attributes data)
            throws IOException, InterruptedException {
        String tsuid = pc.getTransferSyntax();
        String cuid = rq.getString(Tag.RequestedSOPClassUID);
        String iuid = rq.getString(Tag.RequestedSOPInstanceUID);
        int msgId = rq.getInt(Tag.MessageID, 0);
        DimseRSPHandler rspHandler = new DimseRSPHandler(msgId) {

            @Override
            public void onDimseRSP(Association asInvoked, Attributes cmd, Attributes data) {
                super.onDimseRSP(asInvoked, cmd, data);
                try {
                    asAccepted.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.warn(asAccepted + ": error forwarding N-SET-RQ", e);
                }
            }
        };
        asInvoked.nset(cuid, iuid, data, tsuid, rspHandler);
    }
}
