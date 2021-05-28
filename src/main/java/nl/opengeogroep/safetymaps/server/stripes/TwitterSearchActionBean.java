package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.twitter.Twitter;
import nl.opengeogroep.safetymaps.twitter.TwitterIncidentSearchResult;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/twitter")
public class TwitterSearchActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog("twitter");

    private static final long DEFAULT_MAX_CACHE_AGE = 25;
    private static final String CFG_MAX_CACHE_AGE = "twitter_max_cache_age_seconds";

    private static String bearerToken = null;

    private ActionBeanContext context;

    private static final ConcurrentHashMap<Long, TwitterIncidentSearchResult> cache = new ConcurrentHashMap<>();

    @Validate(required=true)
    private long incidentId;

    @Validate(required=true)
    private long startTime;

    @Validate
    private long endTime;

    @Validate(required=true)
    private String location;

    @Validate
    private Double radius = 0.2;

    @Validate
    private String address;

    @Validate
    private String terms;

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public long getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(long incidentId) {
        this.incidentId = incidentId;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getRadius() {
        return radius;
    }

    public void setRadius(Double radius) {
        this.radius = radius;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }
    // </editor-fold>

    private synchronized String getBearerToken() throws Exception {
        if(bearerToken == null) {
            log.info("Requesting Twitter bearer token");
            String consumerKey = Cfg.getSetting("twitter_consumer_key");
            String consumerSecret = Cfg.getSetting("twitter_consumer_secret");
            if(consumerKey == null || consumerSecret == null) {
                throw new Exception("Add twitter_consumer_key and twitter_consumer_secret to settings table!");
            }
            bearerToken = Twitter.getOAuth2BearerToken(consumerKey, consumerSecret);
            log.info("Bearer token received");
        }
        return bearerToken;
    }

    private static void evictExpiredCache() throws Exception {
        final long maxCacheAge = Long.parseLong(Cfg.getSetting(CFG_MAX_CACHE_AGE, DEFAULT_MAX_CACHE_AGE + ""));
        final List<Long> keysToEvict = new ArrayList<>();
        for(Map.Entry<Long,TwitterIncidentSearchResult> entry: cache.entrySet()) {
            final long incidentId = entry.getKey();
            final TwitterIncidentSearchResult cachedResult = entry.getValue();
            final long cacheAgeSeconds = (System.currentTimeMillis() - cachedResult.getDate().getTime()) / 1000;
            if (cacheAgeSeconds > maxCacheAge) {
                log.debug(String.format("Evicting cached response for incident %d with age of %d seconds, older than max %d seconds", incidentId, cacheAgeSeconds, maxCacheAge));
                keysToEvict.add(incidentId);
            }
        }
        for(Long key: keysToEvict) {
            cache.remove(key);
        }
    }

    public Resolution search() throws Exception {

        context.getResponse().addHeader("Access-Control-Allow-Origin", "*");
        if(context.getRequest().getMethod().equals("HEAD")) {
            return new StreamingResolution("application/json", new StringReader(""));
        }

        JSONObject response = new JSONObject("{result:false}");
        response.put("result", false);
        response.put("cached", false);

        evictExpiredCache();
        TwitterIncidentSearchResult cachedResult = cache.get(incidentId);

        if (cachedResult != null) {
            log.debug(String.format("Incident %d: sending cached response", incidentId));

            response.put("cached", true);
            response.put("cachedAt", cachedResult.getDate().getTime());
            response.put("response", cachedResult.getResponse());
            response.put("responseTerms", cachedResult.getResponseTerms());
            response.put("result", true);
            return new StreamingResolution("application/json", new StringReader(response.toString(4)));
        }

        log.debug("Search request for incident " + incidentId + ", location=" + location + ", terms " + terms);

        try {
            try(CloseableHttpClient client = Twitter.getClient()) {
                HttpUriRequest get = RequestBuilder.get()
                        .setUri(Twitter.API + "1.1/search/tweets.json")
                        .addHeader("Authorization", "Bearer " + getBearerToken())
                        .addParameter("geocode", location + "," + radius + "km")
                        .addParameter("q", "since:" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(startTime*1000)))
                        .addParameter("lang", "nl")
                        .addParameter("count", "50")
                        .addParameter("include_entities", "false")
                        .build();

                log.trace("> " + get.getRequestLine());
                String result = client.execute(get, new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse hr) {
                        log.trace("< " + hr.getStatusLine());
                        String entity = null;
                        try {
                            entity = IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                        } catch(IOException e) {
                        }
                        if(hr.getStatusLine().getStatusCode() != SC_OK) {
                            log.error("HTTP error searching tweets for geocode " + location + ": " + hr.getStatusLine() + ", " + entity);
                        }
                        log.trace("< entity: " + entity);
                        return entity;
                    }
                });
                if(result != null) {
                    JSONObject res = new JSONObject(result);
                    response.put("response", res);
                    if(res.has("errors")) {
                        log.error("Response searching location has errors: " + res.getJSONArray("errors").toString());
                    }
                    response.put("result", true);
                }
            } catch(Exception e) {
                log.error("Error searching tweets for geocode " + location, e);
            }

            JSONArray tA = new JSONArray(terms);
            terms = address;
            for(int i = 0; i < tA.length() && i < 10; i++) {
                terms += (i == 0 ? " AND " : " OR ") + "\"" + tA.get(i) + "\"";
            }
            try(CloseableHttpClient client = Twitter.getClient()) {
                String q = "since:" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(startTime*1000)) + " " + terms;
                log.debug("Searching tweets for terms with q=" + terms);
                HttpUriRequest get = RequestBuilder.get()
                        .setUri(Twitter.API + "1.1/search/tweets.json")
                        .addHeader("Authorization", "Bearer " + getBearerToken())
                        .addParameter("q", q)
                        .addParameter("lang", "nl")
                        .addParameter("count", "20")
                        .addParameter("include_entities", "false")
                        .build();

                log.trace("> " + get.getRequestLine());
                String result = client.execute(get, new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse hr) {
                        log.trace("< " + hr.getStatusLine());
                        String entity = null;
                        try {
                            entity = IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                        } catch(IOException e) {
                        }
                        if(hr.getStatusLine().getStatusCode() != SC_OK) {
                            log.error("HTTP error searching tweets for terms " + terms + ": " + hr.getStatusLine() + ", " + entity);
                        }
                        log.trace("< entity: " + entity);
                        return entity;
                    }
                });
                if(result != null) {
                    JSONObject res = new JSONObject(result);
                    response.put("responseTerms", res);
                    if(res.has("errors")) {
                        log.error("Response for searching terms \"" + terms + "\" has errors: " + res.getJSONArray("errors").toString());
                    }

                    log.debug(String.format("Incident %d: caching result", incidentId));
                    cache.put(incidentId, new TwitterIncidentSearchResult(
                            new Date(),
                            this.context.getRequest().getParameterMap(),
                            response.getJSONObject("response"),
                            response.getJSONObject("responseTerms")
                    ));
                }
            } catch(Exception e) {
                log.error("Error searching tweets for terms " + terms, e);
            }

        } catch(Exception e) {
            log.error("Error searching tweets", e);
            response.put("error", e.getClass() + ": " + e.getMessage());
        }

        return new StreamingResolution("application/json", new StringReader(response.toString(4)));
    }
}
