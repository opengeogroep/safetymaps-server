<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<%-- This page can be called when SAML2 configuration is wrong or a SAMLRequest with
     IsPassive=true returned because the user was not authenticated. User shouldn't
     see an error in the second case.

     Right now just forward to viewer, don't show any error. Can't show login page
     directly, would cause HTTP 400 "Invalid direct reference to form login page"
     from Tomcat.
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html><html>
    <head>
        <meta http-equiv="Refresh" content="0;url=${contextPath}/viewer/">
    </head>
    <body>
        Redirecting...
    </body>
</html>
