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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Lagen" menuitem="layers">
    <stripes:layout-component name="content">

        <h1>Beheer lagen</h1>

        <table class="table table-bordered table-striped table-fixed-header" id="layers-table">
            <thead>
                <tr>
                    <th>Naam</th>
                    <th>URL</th>
                    <th>Enabled</th>
                    <th>Baselayer</th>
                    <th>Layertype</th>
                    <th>Index</th>
                    <th class="table-actions">&nbsp;</th>
                </tr>
            </thead>
            <tbody>
            <c:forEach var="l" items="${actionBean.layers}">
                <tr>
                    <td><c:out value="${l.name}"/></td>
                    <td><c:out value="${l.url}"/></td>
                    <td>
                        <span class="glyphicon ${l.enabled ? 'glyphicon-ok-circle text-success' : 'glyphicon-remove-circle'}"></span>
                    </td>
                    <td>
                        <span class="glyphicon ${l.baselayer ? 'glyphicon-ok-circle text-success' : 'glyphicon-remove-circle'}"></span>
                    </td>
                    <td><c:out value="${l.layertype}"/></td>
                    <td><c:out value="${l.index}"/></td>
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
                    <label class="col-sm-2 control-label">Naam:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="layer.name"/></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">URL:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="layer.url" size="80" maxlength="255"/></div>
                </div>
                <div class="form-group">
                    <div class="col-sm-offset-2 col-sm-10">
                        <div class="checkbox">
                            <label><stripes:checkbox name="layer.enabled"/>Enabled</label>
                        </div>
                        <div class="checkbox">
                            <label><stripes:checkbox name="layer.baselayer"/>Basislaag</label>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">HTTP parameters:<br>(JSON)</label>
                    <div class="col-sm-10"><stripes:textarea cols="80" rows="6" name="layer.params" class="form-control" /></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">OpenLayers opties:<br>(JSON)</label>
                    <div class="col-sm-10"><stripes:textarea cols="80" rows="4"  name="layer.options" class="form-control" /></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Layertype:</label>
                    <div class="col-sm-10"><stripes:select name="layer.layertype"><stripes:option>WMS</stripes:option></stripes:select></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Index:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="layer.index" size="3" maxlength="3"/></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Legenda URL:</label>
                    <div class="col-sm-10"><stripes:text class="form-control" name="layer.legend" size="80" maxlength="255" /></div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Beschrijving:</label>
                    <div class="col-sm-10"><stripes:textarea cols="80" rows="4" class="form-control" name="layer.notes"/></div>
                </div>
            </c:if>
        </stripes:form>
    </stripes:layout-component>
</stripes:layout-render>