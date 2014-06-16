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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * 
 */
public class InfoFileUtils {

    private static final Logger LOG = LoggerFactory.getLogger(InfoFileUtils.class);

    public static Properties getFileInfoProperties(ProxyAEExtension proxyAEE, File file) throws IOException {
        String[] fileList = file.getParentFile().list();
        String infoFileName =null;
        if(fileList.length >0)
        {
            for(String f:  fileList)
            if(f.endsWith(".info"))
            {
                infoFileName = f;
                break;
                }
        }
        else
        {
            throw new FileNotFoundException("Unable to find information file");
        }
//        String infoFileName = file.getName().substring(0, file.getName().lastIndexOf('.')) + ".info";
        return getPropertiesFromInfoFile(proxyAEE, file.getParent(), infoFileName);
    }

    public static Properties getPropertiesFromInfoFile(ProxyAEExtension proxyAEE, String path, String infoFileName)
            throws FileNotFoundException, IOException {
        Properties prop = new Properties();
        FileInputStream inStream = null;
        try {
            File infoFile = new File(path, infoFileName);
            LOG.debug("{}: Loading info file {}", proxyAEE.getApplicationEntity().getAETitle(), infoFile);
            inStream = new FileInputStream(infoFile);
            prop.load(inStream);
        } finally {
            inStream.close();
        }
        return prop;
    }

    public static FileFilter infoFileFilter() {
        return new FileFilter() {
            
            @Override
            public boolean accept(File pathname) {
                if (pathname.getPath().endsWith(".info"))
                    return true;
                return false;
            }
        };
    }

}
