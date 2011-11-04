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
import java.io.IOException;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che.data.Attributes;
import org.dcm4che.io.DicomInputStream;
import org.dcm4che.io.SAXTransformer;
import org.dcm4che.io.SAXWriter;
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
import org.dcm4che.util.SafeClose;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
public class ProxyApplicationEntity extends ApplicationEntity {

    public static final String FORWARD_ASSOCIATION = "forward.assoc";
    public static final String FORWARD_TASK = "forward.task";

    private static SAXTransformerFactory saxTransformerFactory =
            (SAXTransformerFactory) TransformerFactory.newInstance();

    private String useCallingAETitle;
    private ApplicationEntity destination;
    private Templates attributeCoercion;
    private Schedule schedule;
    private File spoolDirectory;
    private String attributeCoercionURI;

    public ProxyApplicationEntity(String aeTitle) {
        super(aeTitle);
    }

    public final void setSpoolDirectory(File spoolDirectory) {
        spoolDirectory.mkdirs();
        this.spoolDirectory = spoolDirectory;
    }

    public final File getSpoolDirectory() {
        return spoolDirectory;
    }

    public void setUseCallingAETitle(String useCallingAETitle) {
        this.useCallingAETitle = useCallingAETitle;
    }

    public String getUseCallingAETitle() {
        return useCallingAETitle;
    }

    public final void setForwardDestination(ApplicationEntity destination) {
        this.destination = destination;
    }

    public final ApplicationEntity getForwardDestination() {
        return destination;
    }

    public void setAttributeCoercionURI(String uri) throws TransformerConfigurationException {
        this.attributeCoercion = uri != null
                ? saxTransformerFactory.newTemplates(new StreamSource(uri))
                : null;
        this.attributeCoercionURI = uri;
    }

    public final String getAttributeCoercionURI() {
        return attributeCoercionURI;
    }

    public final Templates getAttributeCoercion() {
        return attributeCoercion;
    }

    public final boolean isCoerceAttributes() {
        return attributeCoercion != null;
    }

    public final void setForwardSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public final Schedule getForwardSchedule() {
        return schedule;
    }

    @Override
    protected AAssociateAC negotiate(Association as, AAssociateRQ rq, AAssociateAC ac)
            throws IOException {
        if (schedule != null) {
            long forwardTime = schedule.getForwardTime();
            if (forwardTime > 0) {
                as.setProperty(FORWARD_TASK, new ForwardTask(this, rq, forwardTime));
                return super.negotiate(as, rq, ac);
            }
        }
        return forwardAAssociateRQ(as, rq, ac);
    }

    private AAssociateAC forwardAAssociateRQ(Association as, AAssociateRQ rq,
            AAssociateAC ac) throws IOException {
        try {
            if (useCallingAETitle != null)
                rq.setCallingAET(useCallingAETitle);
            if (!destination.getAETitle().equals("*"))
                rq.setCalledAET(destination.getAETitle());
            Association as2 = connect(destination, rq);
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
        ForwardTask ft = (ForwardTask) as.getProperty(FORWARD_TASK);
        if (ft != null)
            ForwardTaskListener.schedule(ft);
        else if (as2 != null)
            try {
                as2.release();
            } catch (IOException e) {
                LOG.warn("Failed to release " + as2, e);
            }
    }

    public Attributes readAndCoerceDataset(File file) throws IOException {
        Attributes attrs;
        DicomInputStream in = new DicomInputStream(file);
        try {
            in.setIncludeBulkDataLocator(true);
            attrs = in.readDataset(-1, -1);
        } finally {
            SafeClose.close(in);
        }
        
        Attributes modify = new Attributes();
        try {
            SAXWriter w = SAXTransformer.getSAXWriter(attributeCoercion, modify);
            w.setIncludeKeyword(false);
            w.write(attrs);
        } catch (Exception e) {
            new IOException(e);
        }
        attrs.addAll(modify);
        return attrs;
    }

}
