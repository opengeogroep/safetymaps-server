package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.StringReader;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/falckService/{path}")
public class FalckServiceProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(FalckServiceProxyActionBean.class);

    private ActionBeanContext context;

    private static final String ROLE = "falck_webservice";

    private String path;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Resolution proxy() throws Exception {
        if(!context.getRequest().isUserInRole(ROLE)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot webservice");
        }

        String authorization = Cfg.getSetting("falck_webservice_authorization");
        String url = Cfg.getSetting("falck_webservice_url");

        if(authorization == null || url == null) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor webservice geconfigureerd door beheerder");
        }

        final HttpUriRequest req = RequestBuilder.get()
                .setUri(url + "/" + path + "?" + context.getRequest().getQueryString())
                .addHeader("Authorization", authorization)
                .build();

        try(CloseableHttpClient client = HttpClients.createDefault()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            String content = client.execute(req, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                    contentType.setValue(hr.getEntity().getContentType().getValue());
                    try {
                        return IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                    } catch(IOException e) {
                        log.error("Exception reading HTTP content", e);
                        return "Exception " + e.getClass() + ": " + e.getMessage();
                    }
                }
            });

            return new StreamingResolution(contentType.getValue(), new StringReader(content));
        } catch(IOException e) {
            log.error("Failed to write output:", e);
            return null;
        }
    }
}
