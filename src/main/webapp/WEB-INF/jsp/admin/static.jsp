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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Voertuigviewer" menuitem="static">
    <stripes:layout-component name="head">
        <meta http-equiv="Refresh" content="5">
    </stripes:layout-component>
    <stripes:layout-component name="content">
        
        <h1>Update voertuigviewer</h1>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>
        <ul>
            <li>Laatste update voertuigviewer: <fmt:formatDate type="both" dateStyle="full" timeStyle="full" value="${actionBean.lastStaticUpdate}"/></li>
            <li>Laatste wijziging configuratie: <fmt:formatDate type="both" dateStyle="full" timeStyle="full" value="${actionBean.lastConfigUpdate}"/></li>
            <li>Versie informatie: ...</li>            
        </p>
        <c:if test="${actionBean.lastConfigUpdate.after(actionBean.lastStaticUpdate)}">
            <p>
                De configuratie is gewijzigd sinds de laatste keer dat de voertuigviewer is geupdate. Druk op de knop om de wijzigingen
                toe te passen:
            </p>
        </c:if>
        <stripes:url var="url" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean" event="update"/>
        <input type="button" onclick="window.open('${url}')" value="Update voertuigviewer">
    </stripes:layout-component>
</stripes:layout-render>