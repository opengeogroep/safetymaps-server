package nl.opengeogroep.safetymaps.server.config;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 *
 * @author Matthijs Laan
 */
public class ConfiguredLayer {
    private Integer gid;
    private boolean proxy, enabled, baselayer, getcapabilities, issmvngwms;
    private int index;
    private String name, url, params, options, parent, pl, layertype = "WMS", notes, legend;

    public Integer getGid() {
        return gid;
    }

    public void setGid(Integer gid) {
        this.gid = gid;
    }

    public boolean isProxy() {
        return proxy;
    }

    public void setProxy(boolean proxy) {
        this.proxy = proxy;
    }

    public boolean isIssmvngwms() {
        return issmvngwms;
    }

    public void setIssmvngwms(boolean issmvngwms) {
        this.issmvngwms = issmvngwms;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isBaselayer() {
        return baselayer;
    }

    public void setBaselayer(boolean baselayer) {
        this.baselayer = baselayer;
    }

    public boolean isGetcapabilities() {
        return getcapabilities;
    }

    public void setGetcapabilities(boolean getcapabilities) {
        this.getcapabilities = getcapabilities;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public String getPl() {
        return pl;
    }

    public void setPl(String pl) {
        this.pl = pl;
    }

    public String getLayertype() {
        return layertype;
    }

    public void setLayertype(String layertype) {
        this.layertype = layertype;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getLegend() {
        return legend;
    }

    public void setLegend(String legend) {
        this.legend = legend;
    }

    public void setAbstract(String s) {
        this.notes = s;
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
