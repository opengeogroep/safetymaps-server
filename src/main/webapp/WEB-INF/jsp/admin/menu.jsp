<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<ul class="nav navbar-nav">
    <li${menuitem == 'index' ? ' class="active"' : ''}><a href="${contextPath}/admin/index.jsp">Start</a></li>
    <li class="dropdown">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Voertuig <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'static' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean">Update</stripes:link></li>
            <li${menuitem == 'sync' ? ' class="active"' : ''}><a href="${contextPath}/admin/sync.jsp">Synchronisatie</a></li>
        </ul>
    </li>
    <li class="dropdown">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Configuratie <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'app' ? ' class="active"' : ''}><a href="${contextPath}/admin/app.jsp">Instellingen</a></li>
            <li${menuitem == 'customize' ? ' class="active"' : ''}><a href="${contextPath}/admin/customize.jsp">Maatwerk</a></li>
            <li${menuitem == 'symbols' ? ' class="active"' : ''}><a href="${contextPath}/admin/dbksymbols.jsp">DBK Symbolen</a></li>
            <li${menuitem == 'layertoggle' ? ' class="active"' : ''}><a href="${contextPath}/admin/layertoggle.jsp">Inzetbalk</a></li>
            <li${menuitem == 'layertoggle' ? ' class="active"' : ''}><a href="#">Tekenmodule</a></li>
        </ul>
    </li>
    <li${menuitem == 'basemaps' ? ' class="active"' : ''}><a href="${contextPath}/admin/basemapsupdate.jsp">Basiskaart update</a></li>
</ul>