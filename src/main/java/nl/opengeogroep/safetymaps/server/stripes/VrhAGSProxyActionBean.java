package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/vrhAGS{path}")
public class VrhAGSProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(VrhAGSProxyActionBean.class);

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
            String url = Cfg.getSetting("vrh_ags_eenheden_url");

            path = path.substring("Eenheden".length());
            builder = buildProxyRequestBuilder(url);
        } else {
            String url = Cfg.getSetting("vrh_ags_incidents_url");
            builder = buildProxyRequestBuilder(url);
        }

        final HttpUriRequest req = builder.build();

        try(CloseableHttpClient client = createHttpClient()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            final String content = client.execute(req, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                    if(log.isTraceEnabled()) {
                        log.trace("response headers: " + Arrays.asList(hr.getAllHeaders()));
                    }
                    if(hr.getEntity() != null && hr.getEntity().getContentType() != null) {
                        contentType.setValue(hr.getEntity().getContentType().getValue());
                    }
                    try {
                        // XXX streaming werkt niet?
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

    private RequestBuilder buildProxyRequestBuilder(String url) throws Exception {
        String qs = context.getRequest().getQueryString();
        RequestBuilder builder = RequestBuilder.create(context.getRequest().getMethod())
                .setUri(url + (path == null ? "" : path) + (qs == null ? "" : "?" + qs));

        if("POST".equals(getContext().getRequest().getMethod())) {
            String contentType = getContext().getRequest().getContentType();
            if(contentType != null && contentType.contains("application/x-www-form-urlencoded")) {

                List <NameValuePair> nvps = new ArrayList<>();
                for(Map.Entry<String,String[]> param: context.getRequest().getParameterMap().entrySet()) {
                    nvps.add(new BasicNameValuePair(param.getKey(), context.getRequest().getParameter(param.getKey())));
                }

                builder.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
            } else {
                /*
                if(contentType.contains(";")) {
                    contentType = contentType.split(";")[0];
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copy(context.getRequest().getInputStream(), bos);
                byte[] body = bos.toByteArray();
                log.debug("Setting body content type " + contentType + " to: " + new String(body));
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                builder.setEntity(new InputStreamEntity(bis, body.length, ContentType.create(contentType)));
                //builder.setEntity(new InputStreamEntity(getContext().getRequest().getInputStream(), ContentType.create(contentType)));
                */

                throw new Exception("Post alleen x-www-form-urlencoded naar proxy!");
            }
        }
        return builder;
    }

    private CloseableHttpClient createHttpClient() throws Exception {
        return HttpClients.custom()
                .setSslcontext(new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                        @Override
                        public boolean isTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                            return true;
                        }
                }).build())
                .build();
    }
}
