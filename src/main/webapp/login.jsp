<%--
Copyright (C) 2012-2020 B3Partners B.V.

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
        <title>Inloggen</title>

        <link rel="stylesheet" href="${contextPath}/viewer/css/safetymaps.css?" type="text/css" media="screen">
        <link rel="stylesheet" href="${contextPath}/viewer/css/libs.min.css?" type="text/css" media="screen">
        <link rel="stylesheet" href="${contextPath}/viewer/css/customize.css?" type="text/css" media="screen">

        <script src="${contextPath}/viewer/js/libs/libs.min.js" type="text/javascript"></script>
        <script src="${contextPath}/viewer/js/safetymaps/config/options.js?" type="text/javascript"></script>
        <script src="${contextPath}/viewer/js/safetymaps/config/i18n.js?" type="text/javascript"></script>
    </head>
    <body>
        <div id="loginpanel" class="modal fade">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title" id="login_title"><span class="glyphicon glyphicon-lock"></span> Inloggen</h4>
              </div>
                <div id="loginpanel_b" class="modal-body">
                    <form method="post" action="j_security_check">
                        <div id="login_msg">
                            <c:catch>
                                <%
                                    String ssoManualHtml = nl.opengeogroep.safetymaps.server.db.Cfg.getSetting("sso_manual_html");
                                    if(ssoManualHtml != null) {
                                        ssoManualHtml = ssoManualHtml.replaceAll(java.util.regex.Pattern.quote("[contextPath]"), request.getContextPath());
                                        out.write(ssoManualHtml);
                                    }
                                %>
                            <script>
                                    var ssoPassiveUrl = "<%= nl.opengeogroep.safetymaps.server.db.Cfg.getSetting("sso_passive_url") %>";

                                    // XXX naar /api/sso
                                    if(ssoPassiveUrl) {
                                        ssoPassiveUrl = "${contextPath}" + ssoPassiveUrl;
                                        console.log("Passive URL: " + ssoPassiveUrl);
                                    }
                                </script>
                            </c:catch>

                            <c:if test="${!empty loginFailMessage}">
                                <p style="color: red; font-weight: bold"><c:out value="${loginFailMessage}"/></p>
                            </c:if>
                        </div>
                        <div class="form-group">
                            <label for="j_username"><span class="glyphicon glyphicon-user"></span> <span id="login_username">Gebruikersnaam1</span>:</label>
                            <input type="text" class="form-control" name="j_username" autocapitalize="none" autofocus="autofocus">
                        </div>
                        <div class="form-group">
                            <label for="j_password"><span class="glyphicon glyphicon-eye-open"></span> <span id="login_password">Wachtwoord1</span>:</label>
                            <input type="password" class="form-control" name="j_password">
                        </div>
                        <input type="submit" id="loginsubmit2" style="display: none" onclick="$('#btn_login_submit').click(); return false;"></input>
                        <button id="btn_login_submit" type="submit" class="btn btn-default btn-success btn-block"><span class="glyphicon glyphicon-log-in"></span> <span id="login_submit">Inloggen1</span></button>
                    </form>
                </div>
            </div>
          </div>
        </div>


        <script type="text/javascript">
$(document).ready(function() {
    i18n.init({
        resGetPath: "${contextPath}/viewer/locales/__lng__/__ns__.json",
        lng: dbkjsLang, fallbackLng: 'en', debug: false
    }, function () {
        init();
    });
});

function init() {
    $('#c_settings').attr("title",i18n.t("settings.title"));
    $('#settings_title').text(i18n.t("settings.title"));

    $("#login_title").text(i18n.t("login.title"));
    $("#login_username").text(i18n.t("login.username"));
    $("#login_password").text(i18n.t("login.password"));
    $("#login_submit").text(i18n.t("login.submit"));
    $("#login_retry").text(i18n.t("login.retry"));

    $("#btn_login_submit").on("click", function() {
        $("#btn_login_submit").attr("disabled", "disabled");
        $("#login_submit").text(i18n.t("login.processing"));
        document.forms[0].submit();
    });

    $("#loginpanel").on('shown.bs.modal', function() {
        $("input[name='j_username']").focus();
    })

    $("#loginpanel").modal({backdrop:'static',keyboard:false, show:true});

}
        </script>
    </body>
</html>
