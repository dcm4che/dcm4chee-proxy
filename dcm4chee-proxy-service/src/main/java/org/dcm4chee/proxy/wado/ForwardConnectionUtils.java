package org.dcm4chee.proxy.wado;

import org.dcm4che.net.Association;
import org.dcm4che.net.pdu.AAssociateRQ;
import org.dcm4che.net.pdu.PresentationContext;
import org.dcm4chee.proxy.Proxy;
import org.dcm4chee.proxy.conf.ForwardRule;
import org.dcm4chee.proxy.conf.ProxyAEExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardConnectionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardConnectionUtils.class);

    public static Association openForwardAssociation(ProxyAEExtension proxyAEE, ForwardRule rule, String callingAET,
            String calledAET, String cuid, String tsuid, String clazz) {
        AAssociateRQ rq = new AAssociateRQ();
        rq.addPresentationContext(new PresentationContext(1, cuid, tsuid));
        rq.setCallingAET(callingAET);
        rq.setCalledAET(calledAET);
        Association asInvoked = null;
        try {
            asInvoked = proxyAEE.getApplicationEntity().connect(Proxy.getInstance().findApplicationEntity(calledAET),
                    rq);
        } catch (Exception e) {
            LOG.error("{}: Error opening forward connection: {}", clazz, e);
            if (LOG.isDebugEnabled())
                e.printStackTrace();
        }
        return asInvoked;
    }

}
