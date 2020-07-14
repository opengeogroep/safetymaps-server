<%--
Copyright (C) 2012 B3Partners B.V.

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

<% request.getSession().invalidate(); %>

<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="teststring" content="<h1>Uitgelogd</h1>">
        <title>Uitgelogd</title>
    </head>
    <body>
        <h1>Uitgelogd</h1>

        <b>U bent nu uitgelogd.</b>
        <p>
        <a href="<c:url value="/"/>">Startpagina</a>

        <script>
            var ssoLogoutUrl = null;
            <c:catch>
            <%
                String ssoLogoutUrl = nl.opengeogroep.safetymaps.server.db.Cfg.getSetting("sso_logout_url");
                if(ssoLogoutUrl != null) {
                    out.write("ssoLogoutUrl = '" + ssoLogoutUrl + "';");
                }
            %>
            </c:catch>

            var url = new URL(window.location.href);
            var r = url.searchParams.get("returnTo");
            if(r) {
                if(ssoLogoutUrl) {
                    var absoluteReturn = new URL(r, document.URL);
                    var ssoLogout = ssoLogoutUrl.replace('[returnUrl]', encodeURIComponent(absoluteReturn.toString()));
                    window.location.href = ssoLogout;
                } else {
                    window.location.href = r;
                }
            }
        </script>
    </body>
</html>
