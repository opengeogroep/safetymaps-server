<%-- 
    Document   : fotomanager
    Created on : Jun 14, 2018, 1:19:54 PM
    Author     : martijn
--%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Beheer fotofunctie" menuitem="fotofunctie">
    <stripes:layout-component name="content">
        
        <h1>Foto's</h1>

        <stripes:useActionBean var="actionBean" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.FotoActionBean" event="list"/> 
        <c:if test="${not empty actionBean.fotos}">
        <table class="table table-bordered table-striped table-fixed-header">
            <thead>
                <tr>
                    <th>Bestandsnaam</th>
                    <th>Datum</th>
                    <th>Incident</th>
                    <th>Opmerking</th>
                    <th>Verwijderen</th>
                </tr>
            </thead>
            <tbody>

                <c:forEach var="p" items="${actionBean.fotos}">
                    <stripes:url var="downloadLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.FotoActionBean" event="downloadFoto">
                        <stripes:param name="filename" value="${p.filename}"/>
                    </stripes:url>
                    <tr>
                        <td style="cursor: pointer" onclick="window.location.href='${downloadLink}'"><c:out value="${p.filename}"/></td>
                        <td><c:out value="${p.date}"/></td>
                        <td>
                            <c:out value="${p.incident_nummer}"/>
                        </td>
                        <td><c:out value="${p.omschrijving}"/></td>
                        <td class="table-actions">
                            <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.FotoActionBean" event="delete" title="Verwijderen">
                                <stripes:param name="filename" value="${p.filename}"/>
                                <span class="glyphicon glyphicon-remove"></span>
                            </stripes:link>
                        </td>
                    </tr>
                </c:forEach>
            
            </tbody>
        </table>
        </c:if>
        <c:if test="${empty actionBean.fotos}">
            <h1>Geen foto's om weer te geven. </h1>
        </c:if>
    </stripes:layout-component>
</stripes:layout-render>  
