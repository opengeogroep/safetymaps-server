<%--
Copyright (C) 2021 Safety C&T

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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Maptrip beheer" menuitem="maptrip">
  <stripes:layout-component name="content">

    <h1>Maptrip koppelingen</h1>

    <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="maptrip-table">
      <thead>
          <tr>
              <th>Voertuignummer</th>
              <th>Maptrip deviceid</th>
              <th class="table-actions">&nbsp;</th>
          </tr>
      </thead>
      <tbody>
        <c:forEach var="u" items="${actionBean.units}">
          <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MaptripActionBean" event="edit">
            <stripes:param name="rowid" value="${u.row_id}"/>
          </stripes:url>
          <tr style="cursor: pointer" class="${actionBean.rowid == u.row_id ? 'info' : ''}" onclick="${ 'window.location.href=\''.concat(editLink).concat('\'') }">
            <td><c:out value="${u.safetyconnect_unit}"/></td>
            <td><c:out value="${u.maptrip_device}"/></td>
            <td class="table-actions">
              <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MaptripActionBean" event="edit" title="Bewerken">
                  <stripes:param name="rowid" value="${u.row_id}"/>
                  <span class="glyphicon glyphicon-pencil"></span>
              </stripes:link>
              <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MaptripActionBean" event="delete" title="Verwijderen">
                  <stripes:param name="rowid" value="${u.row_id}"/>
                  <span class="glyphicon glyphicon-remove"></span>
              </stripes:link>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MaptripActionBean" class="form-horizontal">
      <c:set var="event" value="${actionBean.context.eventName}"/>
      <br>
      <c:if test="${event == 'list'}">
          <stripes:submit name="edit" class="btn btn-primary">Nieuwe koppeling</stripes:submit>
      </c:if>
      <c:if test="${event == 'edit' || event == 'save'}">
        <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
        <c:if test="${!empty actionBean.rowid}">
          <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
        </c:if>
        <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

        <c:if test="${!empty actionBean.rowid}">
            <stripes:hidden name="rowid" value="${actionBean.rowid}"/>
        </c:if>
        <div class="form-group">
          <label class="col-sm-2 control-label">Voertuignummer:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="voertuignummer" disabled="${!empty actionBean.rowid}"/>
            <c:if test="${!empty actionBean.rowid}">
                <stripes:hidden name="voertuignummer" value="${actionBean.rowid}" />
            </c:if>
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Maptrip deviceid:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="maptriplicentie" />
          </div>
        </div>
      </c:if>
    </stripes:form>

  </stripes:layout-component>
</stripes:layout-render>
