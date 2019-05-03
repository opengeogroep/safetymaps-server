<%--
Copyright (C) 2019 B3Partners B.V.

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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Gebruikersbeheer" menuitem="users">
    <stripes:layout-component name="head">
        <%--script type="text/javascript" src="${contextPath}/public/js/editUsers.js"></script--%>
    </stripes:layout-component>
    <stripes:layout-component name="content">

        <h1>Gebruikersbeheer</h1>

        <%--script>
            var mapFiles = ${actionBean.mapFilesJson};

            $(document).ready(layersInit);
        </script--%>

        <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="layers-table">
            <thead>
                <tr>
                    <th>Gebruikersnaam</th>
                    <th>Afkomstig uit</th>
                    <th>Aantal voertuigviewer sessies</th>
                    <th>Laatst ingelogd op</th>
                    <th class="table-actions">&nbsp;</th>
                </tr>
            </thead>
            <tbody>
            <c:forEach var="u" items="${actionBean.allUsers}">
                <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditUsersActionBean" event="edit">
                    <stripes:param name="username" value="${u.username}"/>
                </stripes:url>
                <c:if test="${u.login_source == 'LDAP'}">
                    <c:set var="editLink" value=""/>
                </c:if>
                <tr style="${editLink == '' ? '' : 'cursor: pointer'}" class="${actionBean.username == u.username ? 'info' : ''}" onclick="${editLink != '' ? 'window.location.href=\''.concat(editLink).concat('\'') : ''}">
                    <td><c:out value="${u.username}"/></td>
                    <td><c:out value="${u.login_source}"/></td>
                    <td><c:out value="${u.session_count}"/></td>
                    <td><c:out value="${u.last_login}"/></td>
                    <td class="table-actions">
                        <c:if test="${!empty editLink}">
                            <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditUsersActionBean" event="edit" title="Bewerken">
                                <stripes:param name="username" value="${u.username}"/>
                                <span class="glyphicon glyphicon-pencil"></span>
                            </stripes:link>
                            <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditUsersActionBean" event="delete" title="Verwijderen">
                                <stripes:param name="username" value="${u.username}"/>
                                <span class="glyphicon glyphicon-remove"></span>
                            </stripes:link>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>

        <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditUsersActionBean" class="form-horizontal">
            <c:set var="event" value="${actionBean.context.eventName}"/>

            <br>
            <c:if test="${event == 'list'}">
                <stripes:submit name="edit" class="btn btn-primary">Nieuwe gebruiker</stripes:submit>
            </c:if>
            <c:if test="${event == 'edit' || event == 'save'}">

                <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
                <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
                <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

                <h2>Bewerken van <c:out value="${!empty actionBean.username ? 'gebruiker "'.concat(actionBean.username).concat('"')  : 'nieuwe gebruiker'}"/></h2>

                <div class="form-group">
                    <label class="col-sm-2 control-label">Gebruikersnaam:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="username" disabled="${!empty actionBean.username}"/>
                        <c:if test="${!empty actionBean.username}">
                            <stripes:hidden name="username" value="${actionBean.username}"/>
                        </c:if>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Wachtwoord:</label>
                    <div class="col-sm-10">
                        <stripes-dynattr:password class="form-control" name="password" autocomplete="off"/>
                        <c:if test="${!empty actionBean.username}"><p class="help-block">(laat leeg om niet te wijzigen)</p></c:if>

                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Groeplidmaatschap:</label>
                    <div class="col-sm-10">
                    <c:forEach var="role" items="${actionBean.allRoles}">
                        <label><stripes:checkbox name="roles" class="form-control" value="${role}"/> <c:out value="${role}"/></label>
                    </c:forEach>
                    </div>
                </div>
            </c:if>
        </stripes:form>

    </stripes:layout-component>
</stripes:layout-render>