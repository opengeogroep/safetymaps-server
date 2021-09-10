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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Autorisatie overzicht" menuitem="authorization">
    <stripes:layout-component name="content">
        
        <h1>Autorisatie overzicht</h1>

        <stripes:useActionBean var="actionBean" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.AuthorizationActionBean" event="list"/> 
        
        <table class="table table-bordered table-striped table-fixed-header table-fixed-header--full">
            <thead>
                <tr>
                    <th>Gebruiker</th>
                    <th>Rol</th>
                    <th>Modules</th>
                    <th>Lagen</th>
                </tr>
            </thead>
            <tbody>
            <c:set var="lastUsername" scope="session" value="" /> 
            <c:forEach var="s" items="${actionBean.authorizations}" varStatus="status">
                <tr>
                    <td><span style="${status.index > 0 && lastUsername == s.username ? 'display: none;' : ''}"><c:out value="${s.username}"/></span></td>
                    <td><c:out value="${s.role}"/></td>
                    <td><c:out value="${s.modules}"/></td>
                    <td><c:out value="${s.wms}"/></td>
                </tr>
                <c:set var="lastUsername" value="${s.username}" />
            </c:forEach>
            </tbody>
        </table>
        
    </stripes:layout-component>
</stripes:layout-render>        