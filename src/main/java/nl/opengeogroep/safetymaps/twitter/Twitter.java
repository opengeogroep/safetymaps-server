package nl.opengeogroep.safetymaps.twitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import static org.apache.http.HttpStatus.SC_OK;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
public class Twitter {
    private static final Log log = LogFactory.getLog("twitter");

    public static final String API = "https://api.twitter.com/";

    public static final String getOAuth2BearerToken(String consumerKey, String consumerSecret) throws Exception {
        CloseableHttpClient client = getClient();

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));

        HttpUriRequest post = RequestBuilder.post()
                .setUri(API + "oauth2/token")
                .addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .addHeader("Authorization", "Basic " + Base64.encodeBase64String((consumerKey + ":" + consumerSecret).getBytes()) )
                .setEntity(new UrlEncodedFormEntity(params))
                .build();

        log.trace("> " + post.getRequestLine());

        try(CloseableHttpClient httpClient = client) {
            final Mutable<Exception> exception = new MutableObject<Exception>();
            String token = httpClient.execute(post, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.trace("< " + hr.getStatusLine());
                    if(hr.getStatusLine().getStatusCode() != SC_OK) {
                        exception.setValue(new IOException("HTTP error getting token: " + hr.getStatusLine()));
                        return null;
                    }
                    String entity = null;
                    try {
                        entity = IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                    } catch(IOException e) {
                        exception.setValue(e);
                    }
                    return entity;
                }
            });
            if(exception.getValue() != null) {
                throw exception.getValue();
            }
            JSONObject jo = new JSONObject(token);
            token = jo.getString("access_token");
            log.info("Got twitter OAuth2 token: " + token);
            return token;
        } catch(Exception e) {
            String msg ="Twitter exception on " + post.getRequestLine() + ": " + e.getClass() +  ": " + e.getMessage();
            log.error(msg, e);
            throw new Exception(msg, e);
        }
    }

    public static final CloseableHttpClient getClient() {
        return HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(15 * 1000)
                    .setSocketTimeout(30 * 1000)
                    .build()
            )
            .build();
    }
}
