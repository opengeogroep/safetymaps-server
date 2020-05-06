<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<c:set scope="request" var="loginFailMessage">Fout bij het inloggen. Gebruikersnaam/wachtwoord incorrect, te vaak foute gegevens ingevuld of een technisch probleem!</c:set>
<jsp:include page="login.jsp"/>
