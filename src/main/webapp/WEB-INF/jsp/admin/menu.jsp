<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<stripes:useActionBean var="s" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SettingsActionBean" event="list"/>

<c:if test="${s.settings['hide_onboard'] != 'true'}">
    <stripes:useActionBean var="staticBean" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean" event="info"/>
    <c:if test="${staticBean.updateRequired}">Configuratie aangepast: <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean">update</stripes:link> van voertuigviewer nodig</c:if>
</c:if>

<ul class="nav navbar-nav">
    <li${menuitem == 'index' ? ' class="active"' : ''}><a href="${contextPath}/admin/index.jsp">Start</a></li>

    <stripes:useActionBean var="s" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SettingsActionBean" event="list"/>

    <c:if test="${s.settings['hide_onboard'] != 'true'}">
        <li class="dropdown">
            <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Voertuig <span class="caret"></span></a>
            <ul class="dropdown-menu">
                <li${menuitem == 'static' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.StaticViewerActionBean">Update</stripes:link></li>
                <li${menuitem == 'sync' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SyncStatusActionBean">Synchronisatie</stripes:link></li>
            </ul>
        </li>
    </c:if>
    <li class="dropdown">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Configuratie <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'app' ? ' class="active"' : ''}><a href="${contextPath}/admin/app.jsp">Instellingen</a></li>
            <li${menuitem == 'modules' ? ' class="active"' : ''}><a href="${contextPath}/admin/modules.jsp">Modules</a></li>
            <c:if test="${s.settings['linkify_enabled'] == 'true'}"><li${menuitem == 'linkify' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LinkifyActionBean">Steekwoorden</stripes:link></li></c:if>
            <li${menuitem == 'layers' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LayerActionBean">Lagen</stripes:link></li>
            <%--li${menuitem == 'customize' ? ' class="active"' : ''}><a href="${contextPath}/admin/customize.jsp">Maatwerk</a></li>
            <li${menuitem == 'dbksymbols' ? ' class="active"' : ''}><a href="${contextPath}/admin/dbksymbols.jsp">DBK Symbolen</a></li>
            <li${menuitem == 'layertoggle' ? ' class="active"' : ''}><a href="${contextPath}/admin/layertoggle.jsp">Inzetbalk</a></li>
            <li${menuitem == 'edit' ? ' class="active"' : ''}><a href="#">Tekenmodule</a></li--%>
            <li${menuitem == 'maptrip' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MaptripActionBean">Maptrip</stripes:link></li>
        </ul>
    </li>
    <li class="dropdown">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Beveiliging <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'users' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditUsersActionBean">Gebruikers</stripes:link></li>
            <li${menuitem == 'groups' ? ' class="active"' : ''}><stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditGroupsActionBean">Groepen</stripes:link></li>
            <li${menuitem == 'authorization' ? ' class="active"' : ''}><a href="${contextPath}/admin/authorization.jsp">Autorisatie overzicht</a></li>
        </ul>
    </li>
    <li${menuitem == 'fotofunctie' ? ' class="active"' : ''}><a href="${contextPath}/admin/fotomanager.jsp">Beheer fotofunctie</a></li>
    <li${menuitem == 'edit' ? ' class="active"' : ''}><a href="${contextPath}/admin/edit.jsp">Tekening opslaan/laden</a></li>
    <%--li class="dropdown">
        <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">Datasets <span class="caret"></span></a>
        <ul class="dropdown-menu">
            <li${menuitem == 'basemaps' ? ' class="active"' : ''}><a href="${contextPath}/admin/basemaps.jsp">Basiskaart</a></li>
            <li${menuitem == 'locationsearch' ? ' class="active"' : ''}><a href="${contextPath}/admin/locationsearch.jsp">Locatiezoeker</a></li>
        </ul>
    </li--%>    
</ul>