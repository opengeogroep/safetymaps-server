/*
 * Copyright (C) 2016 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.validation.*;
import nl.opengeogroep.safetymaps.server.config.ConfiguredLayer;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/admin/action/layers/{layer}")
public class LayerActionBean implements ActionBean, ValidationErrorHandler {

    private static final Log log = LogFactory.getLog("admin.layers");

    private static final String JSP = "/WEB-INF/jsp/admin/layers.jsp";

    private static final String TABLE = "organisation.wms ";

    private ActionBeanContext context;

    private List<ConfiguredLayer> layers = new ArrayList();

    @ValidateNestedProperties({
        @Validate(field = "url", required = true, trim = true, maxlength = 255),
        @Validate(field = "enabled"),
//        @Validate(field = "baselayer"),
        @Validate(field = "index"),
        @Validate(field = "legend", trim = true, maxlength = 255)/*,
        @Validate(field = "notes")*/
    })
    private ConfiguredLayer layer = new ConfiguredLayer();

    @Validate
    private String tab;

    @Validate
    private String name;

    @Validate
    private boolean visible = true;

    @Validate
    private String params;

    @Validate
    private String layersParam;

    @Validate
    private boolean dpiConversionEnabled = true;

    @Validate
    private String layerToggleKey;

    private String mapFilesJson = "{}";

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<ConfiguredLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<ConfiguredLayer> layers) {
        this.layers = layers;
    }

    public ConfiguredLayer getLayer() {
        return layer;
    }

    public void setLayer(ConfiguredLayer layer) {
        this.layer = layer;
    }

    public String getTab() {
        return tab;
    }

    public void setTab(String tab) {
        this.tab = tab;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getMapFilesJson() {
        return mapFilesJson;
    }

    public void setMapFilesJson(String mapFilesJson) {
        this.mapFilesJson = mapFilesJson;
    }

    public String getLayersParam() {
        return layersParam;
    }

    public void setLayersParam(String layersParam) {
        this.layersParam = layersParam;
    }

    public boolean isDpiConversionEnabled() {
        return dpiConversionEnabled;
    }

    public void setDpiConversionEnabled(boolean dpiConversionEnabled) {
        this.dpiConversionEnabled = dpiConversionEnabled;
    }

    public String getLayerToggleKey() {
        return layerToggleKey;
    }

    public void setLayerToggleKey(String layerToggleKey) {
        this.layerToggleKey = layerToggleKey;
    }
    // </editor-fold>

    @Before
    private void loadLayers() throws NamingException, SQLException {
        layers = qr().query(
                "select * from " + TABLE + " where layertype = 'WMS' order by gid",
                new BeanListHandler<>(ConfiguredLayer.class));
    }

    @Override
    public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
        loadLayers();
        return list();
    }

    @DefaultHandler
    @DontValidate
    public Resolution list() throws Exception {
        return new ForwardResolution(JSP);
    }

    @Before(stages = LifecycleStage.BindingAndValidation)
    private void findMapfiles() {
        try {
            JSONArray a = new JSONArray();
            File search = Cfg.getPath("static_mapserver_searchdirs");

            if(search != null) {
                search = new File("/home/matthijsln/dev/dbk/vrh/webdav");
                for(File f: FileUtils.listFiles(search, new String[] {"map"}, true)) {
                    JSONObject m = new JSONObject();
                    m.put("path", f.getPath().substring(search.getPath().length()+1));
                    a.put(m);

                    // Naive mapfile parser. Perhaps replace by JavaScript client-side
                    // GetCap parsing
                    JSONArray l = new JSONArray();
                    m.put("layers", l);
                    LineIterator it = FileUtils.lineIterator(f, "US-ASCII");
                    try {
                        while(it.hasNext()) {
                            String line = it.nextLine().trim();
                            if(line.equals("LAYER")) {
                                String n = it.nextLine().trim();
                                n = n.substring(6, n.length()-1);
                                l.put(n);
                            }
                        }
                    } finally {
                        it.close();
                    }
                }
            }
            mapFilesJson = a.toString(4);
        } catch(Exception e) {
        }
    }

    @Before(stages = LifecycleStage.BindingAndValidation, on = {"edit","save"})
    public void loadLayer() throws NamingException, SQLException {

        String s = context.getRequest().getParameter("id");
        if(StringUtils.isNotBlank(s)) {
            Integer id = Integer.parseInt(s);

            layer = qr().query(
                    "select * from " + TABLE + " where gid = ?", new BeanHandler<>(ConfiguredLayer.class), id);

            String n = layer.getName();
            int i;
            if(n != null && (i = n.indexOf("\\")) != -1) {
                tab = n.substring(0, i);
                name = n.substring(i+1);
            } else {
                name = n;
            }

            try {
                JSONObject options = new JSONObject(layer.getOptions());
                visible = options.getBoolean("visibility");
            } catch(Exception e) {
                visible = false;
            }

            try {
                params = "";
                dpiConversionEnabled = false;
                JSONObject p = new JSONObject(layer.getParams());
                for(String key: (Set<String>)p.keySet()) {
                    if("layers".equalsIgnoreCase(key)) {
                        layersParam = p.getString(key);
                    } else if("map_resolution".equalsIgnoreCase(key)) {
                        dpiConversionEnabled = true;
                    } else {
                        if(params.length() > 0) {
                            params += ",";
                        }
                        params += key + "=" + p.getString(key);
                    }
                }
            } catch(Exception e) {
            }
        }
    }

    @DontValidate
    public Resolution edit() throws Exception {
        return new ForwardResolution(JSP);
    }

    public Resolution save() throws Exception {
        if(tab != null) {
            name = tab + '\\' + name;
            layer.setParent(null);
        } else {
            layer.setParent("#overlaypanel_b2");
        }
        JSONObject p = new JSONObject();
        if(params != null) {
            for(String s: params.split(",")) {
                String[] parts = s.split("=", 2);
                if(parts.length == 2) {
                    p.put(parts[0], parts[1]);
                }
            }
        }

        if(dpiConversionEnabled) {
            p.put("map_resolution", 58); // Convert ArcGIS 90 dpi to 72 dpi MapServer resolution: 72 / (90 / 72) = 57.6 = 58
        }
        p.put("layers", layersParam);

        // XXX
        layer.setNotes(layerToggleKey);

        Object[] qparams = new Object[] {
            name,
            layer.getUrl(),
            layer.isProxy(),
            layer.isEnabled(),
            layer.isBaselayer(),
            p.toString(),
            "{ \"visibility\": " + visible + "}",
            layer.isGetcapabilities(),
            layer.getParent(),
            layer.getPl(),
            layer.getLayertype(),
            layer.getIndex(),
            layer.getNotes(),
            layer.getLegend()
        };
        if(layer.getGid() == null) {
            log.debug("inserting new layer: " + Arrays.toString(qparams));
            Integer newId = qr().insert(
                    "insert into " + TABLE
                    + "(name,url,proxy,enabled,baselayer,params,options,getcapabilities,parent,pl,layertype,index,abstract,legend) "
                    + "values(?,?,?,?,?,?::json,?::json,?,?,?,?,?,?,?)",
                    new ScalarHandler<Integer>(),
                    qparams);
            layer.setGid(newId);
            log.debug("new layer id: " + newId);
        } else {
            log.debug("updating layer id " + layer.getGid() + ": " + Arrays.toString(qparams));
            qr().update(
                    "update " + TABLE
                    + "set name=?,url=?,proxy=?,enabled=?,baselayer=?,params=?::json,options=?::json,getcapabilities=?,parent=?,pl=?,layertype=?,index=?,abstract=?,legend=? "
                    + "where gid=" + layer.getGid(),
                    qparams);
        }
        Cfg.settingsUpdated();

        getContext().getMessages().add(new SimpleMessage("Laag opgeslagen."));
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution delete() throws Exception {
        String id = context.getRequest().getParameter("id");
        if(id != null) {
            qr().update("delete from " + TABLE + " where gid=?::integer", id);
            getContext().getMessages().add(new SimpleMessage("Laag verwijderd."));
        } else {
            getContext().getMessages().add(new SimpleMessage("Geen laag id om te verwijderen!"));
        }
        return new RedirectResolution(this.getClass()).flash(this);
    }

    @DontValidate
    public Resolution cancel() {
        return new RedirectResolution(this.getClass()).flash(this);
    }

}

