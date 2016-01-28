<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page isErrorPage="true" %>

<h1><fmt:message key="errorPage.title"/></h1>
 <p>
 <fmt:message key="errorPage.contents"/>
                    
<pre><c:out value="<%=exception.getMessage() %>"/></pre>

