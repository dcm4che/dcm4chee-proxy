package org.dcm4chee.proxy.service;

import java.lang.management.ManagementFactory;

import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.dcm4che.conf.api.DicomConfiguration;

@SuppressWarnings("serial")
public class ProxyServlet extends HttpServlet {
    
    private ObjectInstance mbean;
    
    private DicomConfiguration dicomConfig;
    
    private Proxy proxy;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dicomConfig = (DicomConfiguration) Class.forName(
                    config.getInitParameter("dicomConfigurationClass"), 
                    false,
                    Thread.currentThread().getContextClassLoader()).newInstance();
            proxy = new Proxy(dicomConfig, config.getInitParameter("deviceName"));
            proxy.start();
            mbean = ManagementFactory.getPlatformMBeanServer().registerMBean(proxy,
                    new ObjectName(config.getInitParameter("jmxName")));
        } catch (Exception e) {
            destroy();
            throw new ServletException(e);
        }
    }
    
    @Override
    public void destroy() {
        if (mbean != null)
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(mbean.getObjectName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        if (proxy != null)
            proxy.stop();
        if (dicomConfig != null)
            dicomConfig.close();
    }
}
