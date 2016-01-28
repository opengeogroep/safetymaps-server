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
        
        <table>
            <tr>
                <th>ID</th>
                <th>Naam</th>
                <th>URL</th>
                <th>Enabled</th>
                <th>Baselayer</th>
                <th>Layertype</th>
                <th>Index</th>
            </tr>
            <c:forEach var="l" items="${actionBean.layers}">
                <tr>
                    <td>
                        <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean" event="edit">
                            <stripes:param name="id" value="${l.gid}"/>
                            ${l.gid}
                        </stripes:link>
                    </td>
                    <td><c:out value="${l.name}"/></td>
                    <td><c:out value="${l.url}"/></td>
                    <td><c:out value="${l.enabled ? '✓' : ''}"/></td>
                    <td><c:out value="${l.baselayer ? '✓' : ''}"/></td>
                    <td><c:out value="${l.layertype}"/></td>
                    <td><c:out value="${l.index}"/></td>
                </tr>
            </c:forEach>
        </table>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>
        <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean">
            <c:set var="event" value="${actionBean.context.eventName}"/>
            <c:if test="${event == 'list'}">
                <stripes:submit name="edit">Nieuw</stripes:submit>
            </c:if>
            <c:if test="${event == 'edit' || event == 'save'}">
                <c:set var="l" value="${actionBean.layer}"/>                
                <stripes:hidden name="id" value="${l.gid}"/>
                
                <%--p>${l}</p--%>
                <stripes:submit name="save">Opslaan</stripes:submit>
                <stripes:submit name="delete" onclick="return confirm('Zeker weten?');">Verwijderen</stripes:submit>
                <stripes:submit name="cancel">Annuleren</stripes:submit>

                <c:set var="name" value="${!empty l.name ? 'laag '.concat(l.name) : '<Onbenoemde laag>'}"/>
                <h2>Bewerken van <c:out value="${!empty l.gid ? name : 'nieuwe laag'}"/></h2>

                <table>
                    <tr>
                        <td>Naam:</td><td><stripes:text name="layer.name"/></td>
                    </tr>
                    <tr>
                        <td>URL:</td><td><stripes:text name="layer.url" size="80" maxlength="255"/></td>
                    </tr>                    
                    <tr>
                        <td/><td><label><stripes:checkbox name="layer.enabled"/>Enabled</label></td>
                    </tr>                    
                    <tr>
                        <td/><td><label><stripes:checkbox name="layer.baselayer"/>Basislaag</label></td>
                    </tr>                    
                    <tr>
                        <td valign="top">HTTP parameters:<br>(JSON) </td><td><stripes:textarea cols="80" rows="6" name="layer.params"/></td>
                    </tr>                    
                    <tr>
                        <td valign="top">OpenLayers opties:<br>(JSON) </td><td><stripes:textarea cols="80" rows="4"  name="layer.options"/></td>
                    </tr>                    
                    <tr>
                        <td>Layertype:</td>
                        <td>
                            <stripes:select name="layer.layertype">
                                <stripes:option>WMS</stripes:option>
                            </stripes:select>
                        </td>
                    </tr>                    
                    <tr>
                        <td>Index:</td><td><stripes:text name="layer.index" size="3" maxlength="3"/></td>
                    </tr>                    
                    <tr>
                        <td>Legenda URL:</td><td><stripes:text name="layer.legend" size="80" maxlength="255"/></td>
                    </tr>                    
                    <tr>
                        <td valign="top">Beschrijving: </td><td><stripes:textarea cols="80" rows="4"  name="layer.notes"/></td>
                    </tr>                        
                </table>
                
            </c:if>
        </stripes:form>
    </stripes:layout-component>
</stripes:layout-render>