<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<ul class="nav navbar-nav">
    <li${menuitem == 'index' ? ' class="active"' : ''}><a href="${contextPath}/admin/index.jsp">Start</a></li>
    <li${menuitem == 'static' ? ' class="active"' : ''}><a href="<stripes:url beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean"/>">Voertuigviewer</a></li>
    <li${menuitem == 'vrhinzetbalk' ? ' class="active"' : ''}><a href="#">Inzetbalk</a></li>
    <li${menuitem == 'vrhinzetbalk' ? ' class="active"' : ''}><a href="#">Synchronisatiestatus</a></li>
    <li${menuitem == 'vrhinzetbalk' ? ' class="active"' : ''}><a href="#">Basiskaart update</a></li>
    <li class="dropdown">
    <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Beheer <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'app' ? ' class="active"' : ''}><a href="${contextPath}/admin/app.jsp">Applicatieinstellingen</a></li>
            <li${menuitem == 'layers' ? ' class="active"' : ''}><a href="<stripes:url beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean"/>">Lagenbeheer</a></li>
            <li${menuitem == 'vrhinzetbalk' ? ' class="active"' : ''}><a href="#">Symbolenbeheer</a></li>
            <li${menuitem == 'vrhinzetbalk' ? ' class="active"' : ''}><a href="#">Tekenmodule beheer</a></li>
        </ul>
    </li>
</ul>