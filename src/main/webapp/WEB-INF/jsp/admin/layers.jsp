<%--
Copyright (C) 2016 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<stripes:useActionBean var="s" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SettingsActionBean" event="list"/>

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Lagen" menuitem="layers">
    <stripes:layout-component name="head">
        <script type="text/javascript" src="${contextPath}/public/js/layers.js"></script>
    </stripes:layout-component>    
    <stripes:layout-component name="content">

        <h1>Beheer WMS lagen</h1>
        
        <script>
            var mapFiles = ${actionBean.mapFilesJson};
            var staticPrefix = '${s.settings['mapserver_prefix_static']}';
            var onlinePrefix = '${s.settings['mapserver_prefix']}';
            
            $(document).ready(layersInit);
        </script>

        <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="layers-table">
            <thead>
                <tr>
                    <th>Naam</th>
                    <c:if test="${actionBean.vrhObjectsEnabled}">
                        <th>Inzetbalk knop</th>
                    </c:if>
                    <th>Beschikbaar</th>
                    <th>Index</th>
                    <th>SMVNG</th>
                    <th class="table-actions">&nbsp;</th>
                </tr>
            </thead>
            <tbody>
            <c:forEach var="l" items="${actionBean.layers}">
                <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean" event="edit">
                    <stripes:param name="id" value="${l.gid}"/>
                </stripes:url>
                <tr style="cursor: pointer" class="${actionBean.layer.gid == l.gid ? 'info' : ''}" onclick="window.location.href='${editLink}'">
                    <td><c:out value="${l.name}"/></td>
                    <c:if test="${actionBean.vrhObjectsEnabled}">
                        <td><c:out value="${l.notes}"/></td>
                    </c:if>
                    <td>
                        <span class="glyphicon ${l.enabled ? 'glyphicon-ok-circle text-success' : 'glyphicon-remove-circle'}"></span>
                    </td>
                    <td><c:out value="${l.index}"/></td>
                    <td><span class="glyphicon ${l.issmvngwms ? 'glyphicon-ok-circle text-success' : 'glyphicon-remove-circle'}"></span></td>
                    <td class="table-actions">
                        <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean" event="edit" title="Bewerken">
                            <stripes:param name="id" value="${l.gid}"/>
                            <span class="glyphicon glyphicon-pencil"></span>
                        </stripes:link>
                        <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean" event="delete" title="Verwijderen">
                            <stripes:param name="id" value="${l.gid}"/>
                            <span class="glyphicon glyphicon-remove"></span>
                        </stripes:link>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>
        <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean" class="form-horizontal">
            <c:set var="event" value="${actionBean.context.eventName}"/>
            <c:if test="${event == 'list'}">
                <stripes:submit name="edit" class="btn btn-primary">Nieuw</stripes:submit>
            </c:if>
            <c:if test="${event == 'edit' || event == 'save'}">
                <c:set var="l" value="${actionBean.layer}"/>
                <stripes:hidden name="id" value="${l.gid}"/>

                <%--p>${l}</p--%>
                <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
                <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
                <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

                <c:set var="name" value="${!empty l.name ? 'laag '.concat(l.name) : '<Onbenoemde laag>'}"/>
                <h2>Bewerken van <c:out value="${!empty l.gid ? name : 'nieuwe laag'}"/></h2>

                <div class="form-group">
                    <label class="col-sm-2 control-label">Tabblad:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="tab"/>
                        <p class="help-block">Het tabblad in het kaartlagen scherm waar de laag kan worden in/uitgeschakeld.</p>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Naam:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="name"/></div>
                </div>
                <c:if test="${actionBean.vrhObjectsEnabled}">
                    <div class="form-group">
                        <label class="col-sm-2 control-label">Inzetbalk knop: </label>
                        <div class="col-sm-10">
                            <stripes:select name="layerToggleKey" class="form-control">
                                <stripes:option value="">Geen</stripes:option>
                                <stripes:option value="Basis">Basis (groen)</stripes:option>
                                <stripes:option value="Brandweer">Brandweer (rood)</stripes:option>
                                <stripes:option value="Water">Water (blauw)</stripes:option>
                                <stripes:option value="Gebouw">Gebouw (zwart)</stripes:option>
                            </stripes:select>
                            <p class="help-block">Indien de laag niet gekoppeld is aan een inzetbalk knop kan de laag worden in- en uitgeschakeld via het kaartlagen scherm. Als de laag gekoppeld is aan een knop kan de laag alleen worden geschakeld via de knop.</p>
                        </div>
                    </div>
                </c:if>
                <div class="form-group">
                    <label class="col-sm-2 control-label">URL:</label>
                    <div class="col-sm-10">
                        <stripes:text id="input-url" class="form-control" name="layer.url" size="80" maxlength="255"/><br/>
                        <c:if test="${actionBean.vrhBridgeLayersEnabled}">
                            <select id="select-mapfiles" class="form-control">
                                <option>Kies beschikbare service...</option>
                            </select>
                            <p class="help-block">Kies hier een door Bridge als mapfile ge&euml;xporteerde MXD.</p>
                        </c:if>
                    </div>
                </div>
                <c:if test="${!actionBean.vrhBridgeLayersEnabled}">
                    <div class="form-group">
                    <label class="col-sm-2 control-label">Modus:</label>
                    <div class="col-sm-10">
                        <div class="form-group">
                            <div class="col-sm-12"><label><stripes:radio name="layer.getcapabilities" value="true"/>Doe WMS GetCapabilities request en voeg alle beschikbare kaartlagen toe aan tabblad</label></div>
                            <div class="col-sm-12"><label><stripes:radio name="layer.getcapabilities" value="false"/>Direct laag met GetMap ophalen met ingevulde LAYERS parameter (hieronder invullen bij "Lagen")</label></div>
                        </div>
                    </div>
                </c:if>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Lagen:</label>
                    <div class="col-sm-10">
                        <stripes:text name="layersParam" class="form-control" /><br/>
                        <c:if test="${actionBean.vrhBridgeLayersEnabled}">
                            <select id="select-layer" class="form-control">
                                <option>Laag toevoegen...</option>
                            </select>
                        </c:if>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Instellingen:</label>                    
                    <div class="col-sm-10">
                        <div class="checkbox">
                            <label><stripes:checkbox name="layer.enabled"/>Beschikbaar</label>
                        </div>
                        <%--div class="checkbox">
                            <label><stripes:checkbox name="layer.baselayer"/>Basislaag</label>
                        </div--%>
                        <div class="checkbox">
                            <label><stripes:checkbox name="visible"/>Standaard ingeschakeld</label>
                        </div>                        
                        <div class="checkbox">
                            <label><stripes:checkbox name="hidefeatureinfo"/>Geen feature info weergeven</label>
                        </div>
                        <c:if test="${actionBean.vrhBridgeLayersEnabled}">
                            <div class="checkbox">
                                <label><stripes:checkbox name="dpiConversionEnabled"/>ArcGIS naar MapServer DPI conversie</label>
                            </div>
                        </c:if>
                        <div class="checkbox">
                            <label><stripes:checkbox name="singleTile"/>Niet getegeld ophalen (indien kaart snel rendert, sneller en minder dataverbruik)</label>
                        </div>
                        <div class="checkbox">
                            <label><stripes:checkbox name="hiDPI"/>Op hoge resolutie schermen renderen met hoge pixeldichtheid</label>
                        </div>
                        <div class="form-group">
                            <div class="col-sm-2" style="padding-top: 5px">Pixel radius voor feature info:</div>
                            <div class="col-sm-10"><stripes-dynattr:text name="featureInfoRadius" class="form-control input-sm" style="width: 80px" type="number"/></div>
                        </div>
                        <div class="form-group">
                            <div class="col-sm-2" style="padding-top: 5px">Niet weergeven boven resolutie</div>
                            <div class="col-sm-10"><stripes-dynattr:text name="maxResolution" class="form-control input-sm" style="width: 80px" type="number" step="0.0001" min="0"/></div>
                            <div class="col-sm-12">
                                (Wanneer een kaart niet zichtbaar hoeft te zijn, vermindert dit het dataverbruik. <a href="<c:out value="${contextPath}/viewer/?res=true"/>" target="_blank">Open voertuigviewer met resolutie weergave</a>)
                            </div>
                        </div>
                    </div>
                </div>
                <%--div class="form-group">
                    <label class="col-sm-2 control-label">Extra HTTP parameters:</label>
                    <div class="col-sm-10"><stripes:text name="params" class="form-control" /></div>
                </div--%>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Index:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="layer.index" size="3" maxlength="3"/>
                        <p class="help-block">De index bepaalt of een laag bovenop of onderop een andere laag wordt getoond (een hogere index tov andere laag betekent bovenop)</p>
                    </div>
                </div>
                <%--div class="form-group">
                    <label class="col-sm-2 control-label">Legenda URL:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="layer.legend" size="80" maxlength="255" /></div>
                </div-->
                <%--div class="form-group">
                    <label class="col-sm-2 control-label">Beschrijving:</label>
                    <div class="col-sm-10"><stripes:textarea cols="80" rows="4" class="form-control" name="layer.notes"/></div>
                </div--%>
            </c:if>
        </stripes:form>
    </stripes:layout-component>
</stripes:layout-render>