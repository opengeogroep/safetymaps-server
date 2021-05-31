package nl.opengeogroep.safetymaps.twitter;

import org.json.JSONObject;

import java.util.Date;
import java.util.Map;

public class TwitterIncidentSearchResult {
    private Date date;

    private Map<String,String[]> parameters;

    private JSONObject response;

    private JSONObject responseTerms;

    public TwitterIncidentSearchResult(Date time, Map<String, String[]> parameters, JSONObject response, JSONObject responseTerms) {
        this.date = time;
        this.parameters = parameters;
        this.response = response;
        this.responseTerms = responseTerms;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Map<String, String[]> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String[]> parameters) {
        this.parameters = parameters;
    }

    public JSONObject getResponse() {
        return response;
    }

    public void setResponse(JSONObject response) {
        this.response = response;
    }

    public JSONObject getResponseTerms() {
        return responseTerms;
    }

    public void setResponseTerms(JSONObject responseTerms) {
        this.responseTerms = responseTerms;
    }
}
