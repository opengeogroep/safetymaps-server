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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.sourceforge.stripes.action.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/admin/layers/{layer}")
public class LayerActionBean implements ActionBean {

    private static final Log log = LogFactory.getLog("admin.layers");

    private static final String JSP = "/WEB-INF/jsp/admin/layers.jsp";

    private ActionBeanContext context;

    private List<Map<String,String>> layers = new ArrayList();

    // <editor-fold defaultstate="collapsed" desc="getters and setters">
    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public List<Map<String, String>> getLayers() {
        return layers;
    }

    public void setLayers(List<Map<String, String>> layers) {
        this.layers = layers;
    }
    // </editor-fold>

    @DefaultHandler
    public Resolution list() throws Exception {
        return new ForwardResolution(JSP);
    }

    @Before
    private void loadLayers() {

    }

}

