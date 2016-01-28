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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
        @Validate(field = "name", required = true, trim = true, maxlength = 255),
        @Validate(field = "url", required = true, trim = true, maxlength = 255),
        @Validate(field = "enabled"),
        @Validate(field = "baselayer"),
        @Validate(field = "params"),
        @Validate(field = "options"),
        @Validate(field = "layertype", required = true),
        @Validate(field = "index"),
        @Validate(field = "legend", trim = true, maxlength = 255),
        @Validate(field = "notes")
    })
    private ConfiguredLayer layer = new ConfiguredLayer();

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
    // </editor-fold>

    @Before
    private void loadLayers() throws NamingException, SQLException {
        layers = qr().query(
                "select * from " + TABLE + " order by gid",
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

    @Before(stages = LifecycleStage.BindingAndValidation, on = {"edit","save"})
    public void loadLayer() throws NamingException, SQLException {
        String s = context.getRequest().getParameter("id");
        if(StringUtils.isNotBlank(s)) {
            Integer id = Integer.parseInt(s);

            layer = qr().query(
                    "select * from " + TABLE + " where gid = ?", new BeanHandler<>(ConfiguredLayer.class), id);
        }
    }

    @DontValidate
    public Resolution edit() throws Exception {
        return new ForwardResolution(JSP);
    }

    public Resolution save() throws Exception {
        Object[] params = new Object[] {
            layer.getName(),
            layer.getUrl(),
            layer.isProxy(),
            layer.isEnabled(),
            layer.isBaselayer(),
            layer.getParams(),
            layer.getOptions(),
            layer.isGetcapabilities(),
            layer.getParent(),
            layer.getPl(),
            layer.getLayertype(),
            layer.getIndex(),
            layer.getNotes(),
            layer.getLegend()
        };
        if(layer.getGid() == null) {
            log.debug("inserting new layer: " + Arrays.toString(params));
            Integer newId = qr().insert(
                    "insert into " + TABLE
                    + "(name,url,proxy,enabled,baselayer,params,options,getcapabilities,parent,pl,layertype,index,abstract,legend) "
                    + "values(?,?,?,?,?,?::json,?::json,?,?,?,?,?,?,?)",
                    new ScalarHandler<Integer>(),
                    params);
            layer.setGid(newId);
            log.debug("new layer id: " + newId);
        } else {
            log.debug("updating layer id " + layer.getGid() + ": " + Arrays.toString(params));
            qr().update(
                    "update " + TABLE
                    + "set name=?,url=?,proxy=?,enabled=?,baselayer=?,params=?::json,options=?::json,getcapabilities=?,parent=?,pl=?,layertype=?,index=?,abstract=?,legend=? "
                    + "where gid=" + layer.getGid(),
                    params);
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

