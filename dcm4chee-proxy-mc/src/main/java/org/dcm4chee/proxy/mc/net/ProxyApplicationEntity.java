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

import java.io.IOException;

import org.dcm4che.net.ApplicationEntity;
import org.dcm4che.net.Association;
import org.dcm4che.net.IncompatibleConnectionException;
import org.dcm4che.net.pdu.AAbort;
import org.dcm4che.net.pdu.AAssociateAC;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.CommonExtendedNegotiation;
import org.dcm4che.net.pdu.ExtendedNegotiation;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4che.net.pdu.RoleSelection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ProxyApplicationEntity extends ApplicationEntity {

    public static final String FORWARD_ASSOCIATION = "forward";

    private String useCallingAETitle;
    private ApplicationEntity forward;

    public ProxyApplicationEntity(String aeTitle) {
        super(aeTitle);
    }

    public void setUseCallingAETitle(String useCallingAETitle) {
        this.useCallingAETitle = useCallingAETitle;
    }

    public String getUseCallingAETitle() {
        return useCallingAETitle;
    }

    public final void setForwardApplicationEntity(ApplicationEntity forward) {
        this.forward = forward;
    }

    public final ApplicationEntity getForwardApplicationEntity() {
        return forward;
    }

    @Override
    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac)
            throws IOException {
        if (forward == null)
            return super.negotiate(as, rq, ac);

        try {
            if (useCallingAETitle != null)
                rq.setCallingAET(useCallingAETitle);
            if (!getAETitle().equals("*"))
                rq.setCalledAET(getAETitle());
            Association as2 = connect(forward, rq);
            as.setProperty(FORWARD_ASSOCIATION, as2);
            AAssociateAC ac2 = as2.getAAssociateAC();
            for (PresentationContext pc : ac2.getPresentationContexts())
                ac.addPresentationContext(pc);
            for (RoleSelection rs : ac2.getRoleSelections())
                ac.addRoleSelection(rs);
            for (ExtendedNegotiation extNeg : ac2.getExtendedNegotiations())
                ac.addExtendedNegotiation(extNeg);
            for (CommonExtendedNegotiation extNeg : ac2.getCommonExtendedNegotiations())
                ac.addCommonExtendedNegotiation(extNeg);
            return ac;
        } catch (InterruptedException e) {
            LOG.warn("Unexpected exception:", e);
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        } catch (IncompatibleConnectionException e) {
            LOG.warn(e.getMessage());
            throw new AAbort(AAbort.UL_SERIVE_PROVIDER, 0);
        }
    }

    @Override
    protected void onClose(Association as) {
        super.onClose(as);
        Association as2 = (Association) as.getProperty(FORWARD_ASSOCIATION);
        if (as2 != null)
            try {
                as2.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + as2, e);
            }
    }

    
}
