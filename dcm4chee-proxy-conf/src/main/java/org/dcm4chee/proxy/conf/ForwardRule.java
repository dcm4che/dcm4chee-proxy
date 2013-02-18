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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che.net.Dimse;

/**
 * @author Michael Backhaus <michael.backaus@agfa.com>
 * 
 */
public class ForwardRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Dimse> dimse = new ArrayList<Dimse>();
    private List<String> sopClass = new ArrayList<String>();
    private String callingAET;
    private List<String> destinationURI = new ArrayList<String>();
    private String useCallingAET;
    private Schedule receiveSchedule = new Schedule();
    private boolean exclusiveUseDefinedTC;
    private String commonName;
    private String mpps2DoseSrTemplateURI;
    private boolean runPIXQuery;
    private String description;

    public List<Dimse> getDimse() {
        return dimse;
    }

    public void setDimse(List<Dimse> dimse) {
        this.dimse = dimse;
    }

    public List<String> getSopClass() {
        return sopClass;
    }

    public void setSopClass(List<String> sopClass) {
        this.sopClass = sopClass;
    }

    public String getCallingAET() {
        return callingAET;
    }

    public void setCallingAET(String callingAET) {
        this.callingAET = callingAET;
    }

    public List<String> getDestinationURI() {
        return destinationURI;
    }

    public String getDestinationTemplate() {
        if (containsTemplateURI())
            return destinationURI.get(0);
        return null;
    }

    public List<String> getDestinationAETitles() {
        List<String> aets = new ArrayList<String>();
        for (String aet : destinationURI)
            if (aet.startsWith("aet:"))
                aets.add(aet.substring(4));
        return aets;
    }

    public boolean containsTemplateURI() {
        return !destinationURI.get(0).startsWith("aet:");
    }

    public void setDestinationURIs(List<String> destinationURI) {
        this.destinationURI = destinationURI;
    }

    public String getUseCallingAET() {
        return useCallingAET;
    }

    public void setUseCallingAET(String useCallingAETitle) {
        this.useCallingAET = useCallingAETitle;
    }

    public Schedule getReceiveSchedule() {
        return receiveSchedule;
    }

    public void setReceiveSchedule(Schedule receiveTime) {
        this.receiveSchedule = receiveTime;
    }

    public boolean isExclusiveUseDefinedTC() {
        return exclusiveUseDefinedTC;
    }

    public void setExclusiveUseDefinedTC(boolean exclusiveUseDefinedTC) {
        this.exclusiveUseDefinedTC = exclusiveUseDefinedTC;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getMpps2DoseSrTemplateURI() {
        return mpps2DoseSrTemplateURI;
    }

    public void setMpps2DoseSrTemplateURI(String conversionUri) {
        this.mpps2DoseSrTemplateURI = conversionUri;
    }

    public boolean isRunPIXQuery() {
        return runPIXQuery;
    }

    public void setRunPIXQuery(boolean runPIXQuery) {
        this.runPIXQuery = runPIXQuery;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
