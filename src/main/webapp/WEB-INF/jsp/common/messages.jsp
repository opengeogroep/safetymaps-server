<%@include file="/WEB-INF/jsp/taglibs.jsp" %>

<%@page import="net.sourceforge.stripes.action.*" %>

<div class="wideMessages">

    <stripes:errors/>
    <stripes:messages/>
<%--
    <c:set var="_locale" value="${actionBean.context.locale}"/>

    <c:forEach var="category" items="${actionBean.messageCategories}">
        <c:set var="msgs" value="${actionBean.messagesByCategory[category]}"/>
  
        <c:if test="${!empty msgs}">
            <div class="${category} box">
                <c:forEach var="msg" items="${msgs}">
                    <c:set var="message"><%= ((Message)pageContext.getAttribute("msg")).getMessage((java.util.Locale)pageContext.getAttribute("_locale")) %></c:set>
                    <div>
                        <c:choose>
                            <c:when test="${fn:startsWith(message, '[html]')}">
                                <c:out value="${fn:substringAfter(message, '[html]')}" escapeXml="false"/>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${message}"/>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:forEach>
            </div>
        </c:if>
    </c:forEach>
--%>
</div>