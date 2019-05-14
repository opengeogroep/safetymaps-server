package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.StringReader;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/vrhAGS{path}")
public class VrhAGSProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(FalckServiceProxyActionBean.class);

    private ActionBeanContext context;

    private static final String ROLE = "vrh_ags_replica";

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
        if(!context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot VRH AGS!");
        }

        RequestBuilder builder;

        if("Token".equals(path)) {
            String authorization = Cfg.getSetting("vrh_ags_token_authorization");
            String url = Cfg.getSetting("vrh_ags_token_url");

            if(authorization == null || url == null) {
                return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor VRH ARGS geconfigureerd door beheerder");
            }

            String qs = context.getRequest().getQueryString();
            builder = RequestBuilder.get()
                    .setUri(url)
                    .addParameter("f", context.getRequest().getParameter("f"))
                    .addParameter("username", authorization.split(":")[0])
                    .addParameter("password", authorization.split(":")[1]);
        } else if(path != null && path.startsWith("Eenheden")) {
            // TODO
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Eenheden proxy nog niet beschikbaar");
        } else {
            String url = Cfg.getSetting("vrh_ags_incidents_url");

            String qs = context.getRequest().getQueryString();
            builder = RequestBuilder.get()
                    .setUri(url + (path == null ? "" : path) + (qs == null ? "" : "?" + qs));

        }
/*
        if("POST".equals(getContext().getRequest().getMethod())) {
            String contentType = getContext().getRequest().getContentType();
            if(contentType.contains(";")) {
                contentType = contentType.split(";")[0];
            }
            builder.setEntity(new InputStreamEntity(getContext().getRequest().getInputStream(), ContentType.create(contentType)));
        }
*/
        final HttpUriRequest req = builder.build();

        try(CloseableHttpClient client = HttpClients.createDefault()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            String content = client.execute(req, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                    contentType.setValue(hr.getEntity().getContentType().getValue());
                    try {
                        // XXX streaming werkt niet?
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