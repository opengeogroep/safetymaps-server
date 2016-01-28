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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="" menuitem="index">
    <stripes:layout-component name="content">
        
        <h1>Beheer SafetyMaps</h1>
        <p>
            Kies in het menu een optie om instellingen aan te passen.
        </p>
        <p>
            De aanpassingen van de configuratie worden direct zichtbaar in een
            online SafetyMaps kantoorviewer. Voor de voertuigviewer moeten de 
            aanpassingen mogelijk handmatig worden toegepast op de 
            <stripes:link href="#">Voertuigviewer</stripes:link> pagina.
        </p>
    </stripes:layout-component>
</stripes:layout-render>