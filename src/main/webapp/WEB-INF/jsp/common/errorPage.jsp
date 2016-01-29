<%@page import="java.io.StringWriter"%>
<%@page import="java.io.PrintWriter"%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page isErrorPage="true" %>

<h1><fmt:message key="errorPage.title"/></h1>
 <p>
 <fmt:message key="errorPage.contents"/>
                    
<pre>
<% 
    StringWriter sw = new StringWriter();
    exception.printStackTrace(new PrintWriter(sw));
    pageContext.setAttribute("msg", sw);
%>
<c:out value="${msg}"/>
</pre>

