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
 * Java(TM), hosted at https://github.com/dcm4che.
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

package org.dcm4chee.proxy.utils;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.conf.api.ConfigurationException;
import org.dcm4che.data.Attributes;
import org.dcm4che.io.SAXWriter;
import org.dcm4che.net.Association;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.service.DicomServiceException;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.dcm4chee.proxy.conf.ProxyDeviceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
public class ForwardRuleUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardRuleUtils.class);

    public static List<String> getDestinationAETsFromForwardRule(Association as, ForwardRule rule, Attributes data)
            throws ConfigurationException, DicomServiceException {
        ProxyAEExtension proxyAEE = as.getApplicationEntity().getAEExtension(ProxyAEExtension.class);
        List<String> destinationAETs = new ArrayList<String>();
        if (rule.containsTemplateURI()) {
            destinationAETs.addAll(getDestinationAETsFromTemplate(proxyAEE, rule.getDestinationTemplate(), data));
        } else
            destinationAETs.addAll(rule.getDestinationAETitles());
        LOG.info("{}: sending data to {} based on ForwardRule \"{}\"",
                new Object[] { as, destinationAETs, rule.getCommonName() });
        return destinationAETs;
    }

    public static List<String> getDestinationAETsFromTemplate(ProxyAEExtension proxyAEE, String uri, Attributes data)
            throws ConfigurationException {
        final List<String> result = new ArrayList<String>();
        try {
            ProxyDeviceExtension proxyDevExt = proxyAEE.getApplicationEntity().getDevice().getDeviceExtension(
                    ProxyDeviceExtension.class);
            Templates templates = proxyDevExt.getTemplates(uri);
            SAXTransformerFactory transFac = (SAXTransformerFactory) TransformerFactory.newInstance();
            TransformerHandler handler = transFac.newTransformerHandler(templates);
            handler.setResult(new SAXResult(new DefaultHandler() {

                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes)
                        throws SAXException {
                    if (qName.equals("Destination")) {
                        result.add(attributes.getValue("aet"));
                    }
                }

            }));
            SAXWriter saxWriter = new SAXWriter(handler);
            saxWriter.write(data);
        } catch (Exception e) {
            LOG.error("Error parsing template {}: {}", uri, e);
            throw new ConfigurationException(e.getMessage());
        }
        if (result.isEmpty()) {
            LOG.error("Parsing template {} returned no result", uri);
            throw new ConfigurationException();
        }
        return result;
    }

    public static List<ForwardRule> filterForwardRulesByCallingAET(ProxyAEExtension proxyAEE, String callingAET) {
        List<ForwardRule> filterList = new ArrayList<ForwardRule>();
        for (ForwardRule rule : proxyAEE.getForwardRules()) {
            List<String> callingAETs = rule.getCallingAETs();
            if ((callingAETs.isEmpty() || callingAETs.contains(callingAET))
                    && rule.getReceiveSchedule().isNow(new GregorianCalendar())) {
                LOG.debug(
                        "Filter by Calling AET: use forward rule \"{}\" based on i) Calling AET = {} and ii) receive schedule days = {}, hours = {}",
                        new Object[] { rule.getCommonName(), 
                                rule.getCallingAETs().isEmpty() ? "<EMPTY>" : rule.getCallingAETs(), 
                                rule.getReceiveSchedule().getDays(),
                                rule.getReceiveSchedule().getHours() });
                filterList.add(rule);
            }
        }
        List<ForwardRule> returnList = new ArrayList<ForwardRule>(filterList);
        for (Iterator<ForwardRule> iterator = filterList.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            for (ForwardRule fwr : filterList) {
                if (rule.getCommonName().equals(fwr.getCommonName()))
                    continue;
                if (rule.getCallingAETs().isEmpty()
                        && !fwr.getCallingAETs().isEmpty()
                        && fwr.getCallingAETs().contains(callingAET)) {
                    LOG.debug(
                            "Filter by Calling AET: remove forward rule \"{}\" with Calling AET = <EMPTY> due to rule \"{}\" with matching Calling AET = {}",
                            new Object[] { rule.getCommonName(), fwr.getCommonName(), fwr.getCallingAETs() });
                    returnList.remove(rule);
                }
            }
        }
        return returnList;
    }

    public static List<ForwardRule> filterForwardRulesOnDimseRQ(List<ForwardRule> fwdRules, String cuid, Dimse dimse) {
        List<ForwardRule> filterList = new ArrayList<ForwardRule>();
        for (ForwardRule rule : fwdRules) {
            if (rule.getDimse().isEmpty() && rule.getSopClasses().isEmpty()
                    || rule.getSopClasses().contains(cuid) && rule.getDimse().isEmpty()
                    || rule.getDimse().contains(dimse) 
                        && (rule.getSopClasses().isEmpty() || rule.getSopClasses().contains(cuid))) {
                LOG.debug(
                        "Filter on DIMSE RQ: add forward rule \"{}\" based on DIMSE = \"{}\" and SOPClasses = \"{}\"",
                        new Object[] { 
                                rule.getCommonName(), 
                                rule.getDimse().isEmpty() ? "<EMPTY>" : rule.getDimse(),
                                rule.getSopClasses().isEmpty() ? "<EMPTY>" : rule.getSopClasses()});
                filterList.add(rule);
            }
        }
        List<ForwardRule> returnList = new ArrayList<ForwardRule>(filterList);
        int i = 0;
        for (Iterator<ForwardRule> iterator = filterList.iterator(); iterator.hasNext();) {
            ForwardRule rule = iterator.next();
            i++;
            for (int j = i; j < filterList.size(); j++) {
                if (returnList.get(j) == null)
                    break;

                ForwardRule fwr = filterList.get(j);
                if (rule.getDimse().isEmpty() && !fwr.getDimse().isEmpty()) {
                    LOG.debug(
                            "Filter on DIMSE RQ: remove forward rule \"{}\" with DIMSE = <EMPTY> due to rule \"{}\" with DIMSE = \"{}\"",
                            new Object[] { 
                                    rule.getCommonName(), 
                                    fwr.getCommonName(),
                                    fwr.getDimse()});
                    returnList.remove(rule);
                    break;
                }
                if (rule.getSopClasses().isEmpty() && !fwr.getSopClasses().isEmpty()) {
                    LOG.debug(
                            "Filter on DIMSE RQ: remove forward rule \"{}\" with SOP Class = <EMPTY> due to rule \"{}\" with SOP Class = \"{}\"",
                            new Object[] { 
                                    rule.getCommonName(), 
                                    fwr.getCommonName(),
                                    fwr.getSopClasses()});
                    returnList.remove(rule);
                }
            }
        }
        return returnList;
    }

}
