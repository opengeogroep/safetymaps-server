package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
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
@UrlBinding("/viewer/api/safetyconnect/{path}")
public class SafetyConnectProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(SafetyConnectProxyActionBean.class);

    private ActionBeanContext context;

    static final String ROLE = "safetyconnect_webservice";

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
        // Allow online access to webservice from onboard safetymaps-viewer using
        // mobile data connection. On the device the browser must be logged in
        // using a persistent login session to the safetymaps-server integrated version
        context.getResponse().addHeader("Access-Control-Allow-Origin", "http://localhost");
        context.getResponse().addHeader("Access-Control-Allow-Credentials", "true");

        if(!context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot webservice");
        }

        String authorization = Cfg.getSetting("safetyconnect_webservice_authorization");
        String url = Cfg.getSetting("safetyconnect_webservice_url");

        if(authorization == null || url == null) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor webservice geconfigureerd door beheerder");
        }

        // TODO: serverside checks incidentmonitor, incidentmonitor_kladblok,
        // eigen_voertuignummer roles and use voertuignummer from user details

        String qs = context.getRequest().getQueryString();
        final HttpUriRequest req = RequestBuilder.get()
                .setUri(url + "/" + path + (qs == null ? "" : "?" + qs))
                .addHeader("Authorization", authorization)
                .build();

        try(CloseableHttpClient client = HttpClients.createDefault()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            final String content = client.execute(req, new ResponseHandler<String>() {
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

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String encoding = "UTF-8";
                    response.setCharacterEncoding(encoding);
                    response.setContentType(contentType.getValue());

                    OutputStream out;
                    String acceptEncoding = request.getHeader("Accept-Encoding");
                    if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        response.setHeader("Content-Encoding", "gzip");
                        out = new GZIPOutputStream(response.getOutputStream(), true);
                    } else {
                        out = response.getOutputStream();
                    }
                    IOUtils.copy(new StringReader(content), out, encoding);
                    out.flush();
                    out.close();
                }
            };

            //return new StreamingResolution(contentType.getValue(), new StringReader(content));
        } catch(IOException e) {
            log.error("Failed to write output:", e);
            return null;
        }
    }
}
