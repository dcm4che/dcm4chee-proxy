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

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IOD;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.ValidationResult;
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXWriter;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.util.UIDUtils;
import org.jboss.resteasy.util.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 */

public class MPPSToSRValidationTest {
    static final Logger LOG = LoggerFactory
            .getLogger(MPPSToSRValidationTest.class);

    @Test
    public void testMPPSToSRTransform() {
        try {
            Assert.assertNotNull(transformMpps2DoseSr());
        } catch (TransformerFactoryConfigurationError | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSRValidateInvalid() {
        try {
            Assert.assertFalse(validateDoseSR(
                    transformMpps2DoseSr(),
                    new String(
                            "1.2.392.200036.9125.14.1884891164079.64699826797.2668396")
                            .concat("1")));
        } catch (TransformerFactoryConfigurationError | Exception e) {
            e.printStackTrace();
        }
    }

    private Attributes transformMpps2DoseSr()
            throws TransformerFactoryConfigurationError, IOException {
        Attributes doseSrData = new Attributes();
        try {
            Templates templates = TemplatesCache.getDefault().get(
                    "src/test/resources/mpps2xraydosesr.xsl");
            SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactory
                    .newInstance();
            TransformerHandler th = factory.newTransformerHandler(templates);
            Transformer tr = th.getTransformer();
            String irradiationEventUID = new String(
                    "1.2.392.200036.9125.14.1884891164079.64699826797.2668396")
                    .concat("1");
            tr.setParameter("IrradiationEventUID", irradiationEventUID);
            String hex = Hex.encodeHex("DCM4CHEE".getBytes());
            BigInteger bi = new BigInteger(hex, 16);
            tr.setParameter("DeviceObserverUID", bi);
            tr.setParameter("PerfomedProcedureStepSOPInstanceUID",
                    "1.2.392.200036.9125.14.1884891164079.64699826797.2668396");
            th.setResult(new SAXResult(new ContentHandlerAdapter(doseSrData)));
            SAXWriter w = new SAXWriter(th);
            w.setIncludeKeyword(false);
            DicomInputStream dis = new DicomInputStream(new File(
                    "src/test/resources/testMPPS.dcm"));
            Attributes data = dis.readDataset(-1, -1);
            dis.close();
            w.write(data);
            doseSrData.setString(Tag.SOPInstanceUID, VR.UI,
                    "1.2.392.200036.9125.14.1884891164079.64699826797.2668396");
            doseSrData.setString(Tag.SeriesInstanceUID, VR.UI,
                    UIDUtils.createUID());
            return doseSrData;
        } catch (Exception e) {
            LOG.error("Error converting MPPS to Dose SR: {}", e.getMessage());
            if (!doseSrData.isEmpty())
                LOG.info("failed SR DATA:\n" + doseSrData.toString());
            return null;
        }
    }

    private boolean validateDoseSR(Attributes doseSrData,
            String irradiationEventUID) throws Exception {

        String iodTemplateURI = "src/test/resources/xr-dosesr-iod.xml";
        IOD doseSrIOD = IOD.load(iodTemplateURI);
        ValidationResult result = doseSrData.validate(doseSrIOD);
        System.out.println(result.asText(doseSrData));
        if (result.isValid())
            return true;
        else {
            return false;
        }
    }
}
