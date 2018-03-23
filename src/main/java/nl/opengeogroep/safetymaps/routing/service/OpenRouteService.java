/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.routing.service;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.io.IOException;
import nl.opengeogroep.safetymaps.routing.*;
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
import org.geotools.geometry.GeometryBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 *
 * @author matthijsln
 */
public class OpenRouteService implements RoutingService {
    private static final Log log = LogFactory.getLog(OpenRouteService.class);
    private final String apiKey;

    private String profile = "driving-car";
    private String preference = "shortest";
    
    public OpenRouteService(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setProfile(String profile) {
        if(profile != null) {
            this.profile = profile;
        }
    }

    public void setPreference(String preference) {
        if(preference != null) {
            this.preference = preference;
        }
    }
    
    public RoutingResult getRoute(RoutingRequest request) throws RoutingException {
        
        RoutingResult result;
        try {
            //CoordinateReferenceSystem crs = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG",null).createCoordinateReferenceSystem(request.getSrid() + "");        
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
            
            try(CloseableHttpClient client = getClient()) {
                HttpUriRequest get = RequestBuilder.get()
                        .setUri("https://api.openrouteservice.org/directions")
                        .addHeader("Accept", "text/json; charset=utf-8")
                        .addParameter("api_key", apiKey)
                        .addParameter("coordinates", fromTransformed.getX() + "," + fromTransformed.getY() + "|" + toTransformed.getX() + "," + toTransformed.getY())
                        .addParameter("profile", profile)
                        .addParameter("preference", preference)
                        .addParameter("format", "geojson")
                        .addParameter("language", "nl")
                        //.addParameter("instructions", "false")
                        .addParameter("elevation", "false")
                        .addParameter("extra_info", "")
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
                    
                    result = new RoutingResult(true, res);
                    result.setDistance(calculateDistance(res));
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
        JSONObject props = route.getJSONArray("features").getJSONObject(0).getJSONObject("properties");
        JSONArray segments = props.getJSONArray("segments");
        double distance = 0;
        for(int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            distance += segment.getDouble("distance");
        }
        return distance;
    }
    
    private static final CloseableHttpClient getClient() {
        return HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(5 * 1000)
                    .setSocketTimeout(10 * 1000)
                    .build()
            )
            .build();
    }
}
