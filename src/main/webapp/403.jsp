<%--
Copyright (C) 2019 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Geen toegang</title>
    </head>
    <body>
        <h1>U heeft geen toegang tot de gevraagde pagina</h1>

        <p>
            Klik
            <a id="link" href="${contextPath}/logout.jsp">hier</a> om met een ander account in te loggen.
        </p>

        <script>
            var link = document.getElementById("link");
            var href = link.getAttribute("href");
            href = href + "?returnTo=" + encodeURIComponent(window.location.href);
            link.setAttribute("href", href);
            console.log(href);
        </script>
    </body>
</html>
