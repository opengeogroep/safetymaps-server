<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<div id="menu">
    <ul class="menuClass">
        <li>
            <div><a ${menuitem == 'index' ? 'class="selected"' : ''} href="${contextPath}/admin/index.jsp">Start</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'app' ? 'class="menuselected"' : ''} href="#">Applicatieinstellingen</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'layers' ? 'class="menuselected"' : ''} href="<stripes:url beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean"/>">Lagenbeheer</a></div>
        </li>
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="menuselected"' : ''} href="#">Inzetbalk</a></div>
        </li>        
        <li>
            <div><a ${menuitem == 'vrhinzetbalk' ? 'class="menuselected"' : ''} href="#">Symbolenbeheer</a></div>
        </li>        
    </ul>
</div>