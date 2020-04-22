<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<%-- This page can be called when configuration is wrong or a SAMLRequest with
     IsPassive=true returned because the user was not authenticated. User shouldn't
     see an error in the second case.

     Right now just show login page without error.
--%>

<%--c:set scope="request" var="loginFailMessage">SAML2 fout, kan niet inloggen met een extern account!</c:set--%>

<jsp:include page="login.jsp"/>
