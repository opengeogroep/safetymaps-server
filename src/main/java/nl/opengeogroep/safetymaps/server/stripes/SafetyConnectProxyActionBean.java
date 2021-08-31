package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.action.StreamingResolution;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_KLADBLOK;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_EDITOR_GMS;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_EIGEN_VOERTUIGNUMMER;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.*;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/safetyconnect/{path}")
public class SafetyConnectProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(SafetyConnectProxyActionBean.class);

    private ActionBeanContext context;

    static final String ROLE = "safetyconnect_webservice";

    static final String INCIDENT_REQUEST = "incident";
    static final String EENHEIDLOCATIE_REQUEST = "eenheidlocatie";
    static final String KLADBLOKREGEL_REQUEST = "kladblokregel";
    static final String EENHEID_REQUEST = "eenheid";
    static final String EENHEIDSTATUS_REQUEST = "eenheidstatus";
    static final String[] UNMODIFIED_REQUESTS = { EENHEID_REQUEST, EENHEIDSTATUS_REQUEST };

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
            return unAuthorizedResolution();
        }

        if (requestIs(KLADBLOKREGEL_REQUEST) && !context.getRequest().isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return unAuthorizedResolution();
        }

        String authorization = Cfg.getSetting("safetyconnect_webservice_authorization");
        String url = Cfg.getSetting("safetyconnect_webservice_url");

        if(authorization == null || url == null) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor webservice geconfigureerd door beheerder");
        }

        String qs = context.getRequest().getQueryString();
        final HttpUriRequest req;
        
        if (requestIs(KLADBLOKREGEL_REQUEST)) {
            req = RequestBuilder.post()
                .setUri(url + "/" + path + (qs == null ? "" : "?" + qs))
                .addHeader("Authorization", authorization)
                .addHeader("Content-Length", "application/xml")
                .addHeader("Content-Length", "0")
                .build();
        } else {
            req = RequestBuilder.get()
                .setUri(url + "/" + path + (qs == null ? "" : "?" + qs))
                .addHeader("Authorization", authorization)
                .build();
        }

        try(CloseableHttpClient client = HttpClients.createDefault()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            final String responseContent = client.execute(req, new ResponseHandler<String>() {
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
            
            final String content;
            // Filter response from the webservice to remove any data that the user is not authorized for
            if (requestIs(INCIDENT_REQUEST)) {
                content = applyAuthorizationToIncidentContent(responseContent);
            } else if (requestIs(EENHEIDLOCATIE_REQUEST)) {
                content = applyFilterToEenheidLocatieContent(responseContent);
            } else if (requestIs(KLADBLOKREGEL_REQUEST)) {
                content = responseContent;
            } else if (keepRequestUnmodified()) {
                content = responseContent;
            } else {
                return unAuthorizedResolution();
            }

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
        } catch(IOException e) {
            log.error("Failed to write output:", e);
            return null;
        }
    }

    private Resolution unAuthorizedResolution() {
        return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot webservice");
    }

    private String defaultError(Exception e) {
        return "Error on " + path;
    }

    private boolean keepRequestUnmodified() {
        return Arrays.stream(UNMODIFIED_REQUESTS).anyMatch((path.toLowerCase())::startsWith);
    }

    private boolean requestIs(String pathPart) {
        return path.toLowerCase().startsWith(pathPart);
    }

    private String applyAuthorizationToIncidentContent(String contentFromResponse) throws Exception {
        HttpServletRequest request = context.getRequest();
        JSONArray content = new JSONArray(contentFromResponse);

        boolean kladblokAlwaysAuthorized = "true".equals(Cfg.getSetting("kladblok_always_authorized", "false"));
        boolean incidentMonitorKladblokAuthorized = kladblokAlwaysAuthorized || request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR_KLADBLOK);
        boolean eigenVoertuignummerAuthorized = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_EIGEN_VOERTUIGNUMMER);
        boolean incidentMonitorAuthorized = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR);

        if (incidentMonitorAuthorized && incidentMonitorKladblokAuthorized) {
            return content.toString();
        }

        try(Connection c = DB.getConnection()) {            
            JSONArray authorizedContent = new JSONArray();
            JSONObject details = getUserDetails(request, c);
            List<String> userVehicleList = Arrays.asList(details.optString("voertuignummer", "-").replaceAll("\\s", ",").split(","));

            for(int i=0; i<content.length(); i++) {
                JSONObject incident = (JSONObject)content.get(i);
                
                JSONArray attachedVehicles;
                if (incident.has("BetrokkenEenheden")) {
                    attachedVehicles = (JSONArray)incident.get("BetrokkenEenheden");
                } else {
                    attachedVehicles = new JSONArray();
                }

                boolean incidentForUserVehicle = false;
                for(int v=0; v<attachedVehicles.length(); v++) {
                    JSONObject vehicle = (JSONObject)attachedVehicles.get(v);
                    if (userVehicleList.contains(vehicle.get("Roepnaam"))) {
                        incidentForUserVehicle = true;
                    }
                }

                if(!incidentForUserVehicle && !eigenVoertuignummerAuthorized && !incidentMonitorKladblokAuthorized) {
                    incident.put("Kladblokregels", new JSONArray());
                }

                if(incidentForUserVehicle || eigenVoertuignummerAuthorized || incidentMonitorAuthorized) {
                    authorizedContent.put(incident);
                }
            }

            return authorizedContent.toString();
        } catch(Exception e) {
            return defaultError(e);
        }
    }

    // Applies filter to /eenheidLocatie to filter out locations for vehicles not attached to an incident
    private String applyFilterToEenheidLocatieContent(String contentFromResponse) throws Exception {
        JSONObject content = new JSONObject(contentFromResponse);
        JSONArray features = (JSONArray)content.get("features");
        JSONArray authorizedFeatures = new JSONArray();

        for(int i=0; i<features.length(); i++) {
            JSONObject feature = (JSONObject)features.get(i);
            JSONObject props = (JSONObject)feature.get("properties");
            Integer incidentnr = (Integer)props.get("incidentNummer");

            boolean authorizedForEenheidLocatie = true;

            if (incidentnr == null || incidentnr == 0) {
                authorizedForEenheidLocatie = false;
            }

            if(authorizedForEenheidLocatie) {
                authorizedFeatures.put(feature);
            }
        }

        content.put("features", authorizedFeatures);

        return content.toString();
    }
}
