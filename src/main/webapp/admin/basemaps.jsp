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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Basiskaart update" menuitem="basemaps">
    <stripes:layout-component name="content">
        
        <h1>Basiskaart</h1>
        <p>
            Laatste datum update basiskaart: ...
        </p>
        <p>
        <ul>
            <li>Updatedatum</li>
            <li>Totale grootte, aantal bestanden, slack space</li>
            <li>Aantal verschillende tiles (en totale bestandsgrootte van die tiles) vergeleken met vorige versie</li>
        </ul>
        </p>
    </stripes:layout-component>
</stripes:layout-render>