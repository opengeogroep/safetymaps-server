<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<div id="menu">
    <ul>
        <li>
            <div><a ${menuitem == 'index' ? 'class="selected"' : ''} href="${contextPath}/admin/index.jsp">Start</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'app' ? 'class="selected"' : ''} href="${contextPath}/admin/app.jsp">Applicatieinstellingen</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'static' ? 'class="selected"' : ''} href="<stripes:url beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean"/>">Voertuigviewer</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'layers' ? 'class="selected"' : ''} href="<stripes:url beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean"/>">Lagenbeheer</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="selected"' : ''} href="#">Inzetbalk</a></div>
        </li>        
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="selected"' : ''} href="#">Symbolenbeheer</a></div>
        </li>     
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="selected"' : ''} href="#">Synchronisatiestatus</a></div>
        </li>             
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="selected"' : ''} href="#">Tekenmodule beheer</a></div>
        </li>             
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="selected"' : ''} href="#">Basiskaart update</a></div>
        </li>             
    </ul>
</div>