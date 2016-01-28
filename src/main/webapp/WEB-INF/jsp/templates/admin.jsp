<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page errorPage="/WEB-INF/jsp/common/errorPage.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<stripes:layout-definition>

<!DOCTYPE html>

<c:set scope="request" var="menuitem" value="${menuitem}"/>

<html>
    <head>
        <title>${pageTitle}${empty pageTitle ? '' : ' - '}SafetyMaps beheer</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

        <%--script type="text/javascript" src="${contextPath}/js/jquery-1.5.2.min.js"></script--%>

        <link href="${contextPath}/styles/admin.jsp" rel="stylesheet">
        <link rel="shortcut icon" href="${contextPath}/images/favicon.ico">

        <stripes:layout-component name="head"/>
    </head>
    
    <body>
        <div id="header">
            <div class="logo"></div>
            <div class="title">SafetyMaps beheer</div>
            <div class="loggedInAs">
                Ingelogd als: <span class="username"><c:out value="${pageContext.request.userPrincipal.name}"/></span>                
            </div>
        </div>
        <jsp:include page="/WEB-INF/jsp/admin/menu.jsp"/>
        <div id="content">
            <p>
            <stripes:layout-component name="content"/>
        </div>   
    </body>
</html>

</stripes:layout-definition>