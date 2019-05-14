package nl.opengeogroep.safetymaps.routing.service;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import nl.opengeogroep.safetymaps.routing.RoutingException;
import nl.opengeogroep.safetymaps.routing.RoutingRequest;
import nl.opengeogroep.safetymaps.routing.RoutingResult;
import nl.opengeogroep.safetymaps.routing.RoutingService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 *
 * @author matthijsln
 */
public class GraphHopper implements RoutingService {
    private static final Log log = LogFactory.getLog(GraphHopper.class);

    private String URL = "http://localhost:11111/route";

    private String profile = "car";

    public GraphHopper() {
    
    }

    public String getURL() {
        return URL;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    public void setProfile(String profile) {
        if(profile != null) {
            this.profile = profile;
        }
    }
    
    @Override
    public RoutingResult getRoute(RoutingRequest request) throws RoutingException {
        RoutingResult result;
        try {
            CoordinateReferenceSystem crs = CRS.decode("epsg:28992");
            CoordinateReferenceSystem serviceCrs = CRS.decode("epsg:4326");
            MathTransform transform = CRS.findMathTransform(crs, serviceCrs, true);
            
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory( null );
            Coordinate coord = new Coordinate(request.getFromX(), request.getFromY());
            Point from = geometryFactory.createPoint(coord);
            coord = new Coordinate(request.getToX(), request.getToY());
            Point to = geometryFactory.createPoint(coord);

            Point fromTransformed = (Point)JTS.transform(from, transform);
            Point toTransformed = (Point)JTS.transform(to, transform);
                    
            log.info("Reprojected destination point to service CRS: " + toTransformed);
            
            // TODO: maybe use https://github.com/graphhopper/directions-api-clients/tree/master/java

            try(CloseableHttpClient client = getClient()) {
                HttpUriRequest get = RequestBuilder.get()
                        .setUri(URL)
                        .addHeader("Accept", "text/json; charset=utf-8, application/json")
                        .addParameter("point", fromTransformed.getX() + "," + fromTransformed.getY())
                        .addParameter("point", toTransformed.getX() + "," + toTransformed.getY())
                        .addParameter("vehicle", profile)
                        .addParameter("weighting", "fastest")
                        .addParameter("locale", "nl")
                        .addParameter("type", "json")
                        .addParameter("elevation", "false")
                        .addParameter("points_encoded", "false")
                        .build();

                log.info("GET > " + get.getRequestLine());
                String response = client.execute(get, new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse hr) {
                        log.trace("< " + hr.getStatusLine());
                        String entity = null;
                        try {
                            entity = IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                        } catch(IOException e) {
                        }
                        if(hr.getStatusLine().getStatusCode() != SC_OK) {
                            log.error("HTTP error: " + hr.getStatusLine() + ", " + entity);
                        }
                        log.trace("< entity: " + entity);
                        return entity;
                    }
                });
                if(response != null) {
                    JSONObject res = new JSONObject(response);
                    log.info("JSON response: " + res.toString(4));
                    
                    if(res.has("message")) {
                        result = new RoutingResult(false, "GraphHopper routing engine error: " + res.getString("message"), null);
                    } else {
                        result = new RoutingResult(true, res);
                        result.setDistance(calculateDistance(res));
                    }
                    result.setRoute(res);
                } else {
                    result = new RoutingResult(false, "Error calculating route: null response", null);
                }
            }

        } catch(Exception e) {
            log.error("Error calculating route", e);
            result = new RoutingResult(false, "Error calculating route: " + e.getMessage(), e);
        }    
        return result;
    }
    
    private static double calculateDistance(JSONObject route) {
        
        double distance = 0;
        JSONArray paths = route.getJSONArray("paths");
        for(int i = 0; i < paths.length(); i++) {
            distance += paths.getJSONObject(i).getDouble("distance");
        }
        return distance;
    }
    
    private static CloseableHttpClient getClient() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        return HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(5 * 1000)
                    .setSocketTimeout(10 * 1000)
                    .build()
            )
            .build();
    }
    
}
