package org.dcm4chee.proxy.test.stgcmt;

import java.io.File;
import java.io.IOException;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

public class findDicomObject {

    public findDicomObject() {
        // TODO Auto-generated constructor stub
    }

    public static void main(String[] args) throws IOException
    {
        File f = new File(args[0]);
        getID(f,args[1]);
    }

    private static void getID(File f, String string) throws IOException {
        if(f.isFile())
        {
            System.out.println("in file "+ f.getPath());
            DicomInputStream dis = new DicomInputStream(f);
            Attributes attrs = dis.readDataset(-1, -1);
            if(attrs.getString(Tag.SOPInstanceUID).compareTo(string)==0)
            {
                System.out.println("Found File: "+f.getPath());
            }
        }
        else if(f.isDirectory())
        {
            System.out.println("in dir "+ f.getPath());
            for(File file: f.listFiles())
            getID(file,string);
        }
    }

    
}
