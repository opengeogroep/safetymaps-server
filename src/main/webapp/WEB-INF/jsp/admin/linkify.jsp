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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Steekwoorden" menuitem="linkify">

    <stripes:layout-component name="content">

        <h1>Steekwoorden</h1>

        <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover">
            <thead>
                <tr>
                    <th>Woord</th>
                    <th>Zoekterm</th>
                    <th class="table-actions">&nbsp;</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="w" items="${actionBean.wordList}">
                    <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LinkifyActionBean" event="edit">
                        <stripes:param name="word" value="${w}"/>
                    </stripes:url>
                    <tr style="cursor: pointer" class="${actionBean.word == w ? 'info' : ''}" onclick="window.location.href='${editLink}'">
                        <td><c:out value="${w}"/></td>
                        <td>
                            <c:out value="${actionBean.termByWord[w]}"/>
                        </td>
                        <td class="table-actions">
                            <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LinkifyActionBean" event="edit" title="Bewerken">
                                <stripes:param name="word" value="${w}"/>
                                <span class="glyphicon glyphicon-pencil"></span>
                            </stripes:link>
                            <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LinkifyActionBean" event="delete" title="Verwijderen">
                                <stripes:param name="word" value="${w}"/>
                                <span class="glyphicon glyphicon-remove"></span>
                            </stripes:link>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>
        <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LinkifyActionBean" class="form-horizontal">
            <c:set var="event" value="${actionBean.context.eventName}"/>
            <c:if test="${event == 'list'}">
                <stripes:submit name="edit" class="btn btn-primary">Woord toevoegen</stripes:submit>
            </c:if>
            <c:if test="${event == 'edit' || event == 'save'}">
                <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
                <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
                <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

                <h2>Steekwoord</h2>

                <div class="form-group">
                    <label class="col-sm-2 control-label">Woord:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="word"/>
                        <p class="help-block">Het woord dat aanklikbaar gemaakt moet worden. Alleen a t/m z gebruiken.</p>
                    </div>
                </div>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Zoekterm:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="term"/>
                        <p class="help-block">Optioneel: de te gebruiken zoekterm indien anders dan het woord.</p>
                    </div>
                </div>
            </c:if>
        </stripes:form>
    </stripes:layout-component>
</stripes:layout-render>