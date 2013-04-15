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

package org.dcm4chee.proxy.common;

import org.dcm4chee.proxy.conf.ForwardRule;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 */
public class CMoveInfoObject {

    private String moveOriginatorAET;
    private String moveDestinationAET;
    private String calledAET;
    private String callingAET;
    private int sourceMsgId;
    private ForwardRule rule;

    public CMoveInfoObject(String moveOriginatorAET, String moveDestinationAET, String calledAET, String callingAET,
            int sourceMsgId, ForwardRule rule) {
        this.moveOriginatorAET = moveOriginatorAET;
        this.moveDestinationAET = moveDestinationAET;
        this.calledAET = calledAET;
        this.callingAET = callingAET;
        this.sourceMsgId = sourceMsgId;
        this.rule = rule;
    }

    public String getMoveOriginatorAET() {
        return moveOriginatorAET;
    }

    public void setMoveOriginatorAET(String moveOriginatorAET) {
        this.moveOriginatorAET = moveOriginatorAET;
    }

    public String getMoveDestinationAET() {
        return moveDestinationAET;
    }

    public void setMoveDestinationAET(String moveDestinationAET) {
        this.moveDestinationAET = moveDestinationAET;
    }

    public String getCalledAET() {
        return calledAET;
    }

    public void setCalledAET(String calledAET) {
        this.calledAET = calledAET;
    }

    public String getCallingAET() {
        return callingAET;
    }

    public void setCallingAET(String callingAET) {
        this.callingAET = callingAET;
    }

    public int getSourceMsgId() {
        return sourceMsgId;
    }

    public void setSourceMsgId(int sourceMsgId) {
        this.sourceMsgId = sourceMsgId;
    }

    public ForwardRule getRule() {
        return rule;
    }

    public void setRule(ForwardRule rule) {
        this.rule = rule;
    }

}
