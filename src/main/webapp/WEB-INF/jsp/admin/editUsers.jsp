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
    <stripes:layout-component name="content">

        <h1>Gebruikersbeheer</h1>

        <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="layers-table">
            <thead>
                <tr>
                    <th>Gebruikersnaam</th>
                    <th></th>
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
                <c:if test="${u.login_source == 'LDAP' || u.username == 'admin'}">
                    <c:set var="editLink" value=""/>
                </c:if>
                <tr style="${editLink == '' ? '' : 'cursor: pointer'}" class="${actionBean.username == u.username ? 'info' : ''}" onclick="${editLink != '' ? 'window.location.href=\''.concat(editLink).concat('\'') : ''}">
                    <td><c:out value="${u.username}"/> </td>
                    <td>
                        <c:choose>
                            <c:when test="${u.secure_password == null}"></c:when>
                            <c:when test="${u.secure_password}">
                                <span style="color: green">Wachtwoord veilig opgeslagen</span>
                            </c:when>
                            <c:otherwise>
                                <span style="color: red" title="Het wachtwoord is met een onveilig hash-algoritme (SHA-1) opgeslagen. Wijzig het wachtwoord zodat deze veiliger wordt opgeslagen (PBKDF2WithHmacSHA512 met salt en 100.000 iteraties)">Wachtwoord update nodig!</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
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
                <c:if test="${!empty actionBean.username}">
                    <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
                    <stripes:submit name="deleteSessions" class="btn btn-warning" onclick="return confirm('Weet u het zeker? Alle actieve browsersessies voor deze gebruiker zullen worden beëindigd.')">Overal uitloggen</stripes:submit>
                </c:if>
                <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

                <c:choose>
                    <c:when test="${empty actionBean.username}">
                        <h2>Bewerken van nieuwe gebruiker</h2>
                    </c:when>
                    <c:otherwise>
                        <h2>Bewerken van gebruiker &quot;<c:out value="${actionBean.username}"/>&quot;</h2>
                    </c:otherwise>
                </c:choose>

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
                        <stripes-dynattr:password class="form-control" name="password" autocomplete="new-password"/>
                        <c:if test="${!empty actionBean.username}">
                            <p class="help-block">Laat leeg om niet te wijzigen.</p>
                            <p class="help-block">Let op! Alle sessies van de gebruiker worden be&euml;indigd bij het wijzigen van de het wachtwoord.</p>
                        </c:if>

                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Automatisch uitloggen na:</label>
                    <div class="col-sm-10">
                        <stripes:text name="expiry" size="3"/>
                        <stripes:select name="expiryTimeUnit">
                            <stripes:option value="years">jaren</stripes:option>
                            <stripes:option value="months">maanden</stripes:option>
                            <stripes:option value="weeks">weken</stripes:option>
                            <stripes:option value="days">dagen</stripes:option>
                        </stripes:select>
                        <p class="help-block">Automatisch uitloggen gebeurt alleen na inactiviteit van de browsersessie.</p>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Incidentkoppeling:</label>
                    <div class="col-sm-10">
                        Voertuignummer(s): <stripes:text name="voertuignummer" size="26" maxlength="20"/> (max 3, spatie of kommagescheiden)
                        <p class="help-block">Voor incidentinformatie in de voertuigviewer voertuignummer(s) invullen of gebruiker lid maken van groep &quot;eigen_voertuignummer&quot;.
                            Voertuignummer is niet vereist als gebruiker de Incidentmonitor kan gebruiken of geen incidentinformatie voor zijn voertuig nodig heeft. De status wordt
                            weergegeven van het eerste voertuignummer of het voertuignummer dat gekoppeld is aan een incident.</p>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Groeplidmaatschap:</label>
                    <div class="col-sm-10">
                        <c:if test="${!empty actionBean.username}">
                            <p class="help-block text-warning">Let op: wijzigingen in groeplidmaatschap worden pas toegepast na uit- en inloggen. Als de uitlogknop is
                                verborgen kan alleen worden uitgelogd door de hele browsercache te legen of door een beheerder met de "Overal uitloggen" knop.</p>
                        </c:if>
                    <c:forEach var="role" items="${actionBean.allRoles}" varStatus="status">
                        <div class="custom-control custom-checkbox">
                            <stripes:checkbox name="roles" class="custom-control-input" value="${role}" id="role${status.index}"/>
                            <label class="custom-control-label" for="role${status.index}"><c:out value="${role}"/></label>
                        </div>
                    </c:forEach>
                    </div>
                </div>
            </c:if>
        </stripes:form>

    </stripes:layout-component>
</stripes:layout-render>