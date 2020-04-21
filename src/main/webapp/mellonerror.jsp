<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<c:set scope="request" var="loginFailMessage">SAML2 fout, kan niet inloggen met een extern account!</c:set>
<jsp:include page="login.jsp"/>
