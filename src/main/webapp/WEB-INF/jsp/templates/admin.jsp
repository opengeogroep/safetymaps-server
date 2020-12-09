<%@include file="/WEB-INF/jsp/taglibs.jsp" %>
<%@page errorPage="/WEB-INF/jsp/common/errorPage.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<stripes:useActionBean var="s" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SettingsActionBean" event="list"/> 

<stripes:layout-definition>
    <c:set scope="request" var="menuitem" value="${menuitem}"/>
    <!DOCTYPE html>
    <html>
        <head>
            <title>${pageTitle}${empty pageTitle ? '' : ' - '}<c:out value="${s.settings['title']}"/></title>
            <meta charset="utf-8">
            <meta http-equiv="X-UA-Compatible" content="IE=edge">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="shortcut icon" href="${contextPath}/images/favicon.ico">        
            <link rel="stylesheet" type="text/css" href="${contextPath}/public/vendor/bootstrap-3.3.6-dist/css/bootstrap.min.css" />
            <link rel="stylesheet" type="text/css" href="${contextPath}/public/css/main.css" />
            <script type="text/javascript" src="${contextPath}/public/vendor/jquery-2.2.0/jquery.min.js"></script>
            <script type="text/javascript" src="${contextPath}/public/vendor/bootstrap-3.3.6-dist/js/bootstrap.min.js"></script>
            <script type="text/javascript" src="${contextPath}/public/vendor/moment-with-locales.js"></script>
            <script type="text/javascript" src="${contextPath}/public/vendor/jquery.tableSorter.min.js"></script>
            <script>
                moment.locale("nl");
            </script>
            <script type="text/javascript" src="${contextPath}/public/vendor/mustache.min.js"></script>
            <script>
                $.mustache = function (template, view, partials) {
                  return Mustache.render(template, view, partials);
                };

                $.fn.mustache = function (view, partials) {
                  return $(this).map(function (i, elm) {
                    var template = $.trim($(elm).html());
                    var output = $.mustache(template, view, partials);
                    return $(output).get();
                  });
                };
                   
            </script>
            <script type="text/javascript" src="${contextPath}/public/js/main.js"></script>
            <stripes:layout-component name="head"/>
        </head>
        <body>
            <nav class="navbar navbar-default navbar-static-top">
                <div class="container">
                    <div class="navbar-header">
                        <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
                            <span class="sr-only">Toggle navigation</span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                            <span class="icon-bar"></span>
                        </button>
                        <a class="navbar-brand" href="${contextPath}/admin/index.jsp"><c:out value="${s.settings['title']}"/></a>
                    </div>
                    <div id="navbar" class="navbar-collapse collapse">
                        <jsp:include page="/WEB-INF/jsp/admin/menu.jsp"/>
                        <ul class="nav navbar-nav navbar-right">
                            <li><a href="${contextPath}/viewer.jsp" target="_blank">Open voertuigviewer</a></li>
                            <li><a href="${contextPath}/about.jsp">Versie</a></li>
                            <li><a href="<c:url value="/authinfo.jsp"/>">Ingelogd als: <span class="username"><c:out value="${pageContext.request.userPrincipal.name}"/></span></a></li>
                            <li><a href="<c:url value="/logout.jsp"/>">Uitloggen</a></li>
                        </ul>
                    </div>
                </div>
            </nav>
            <div class="main-container container">
                <stripes:layout-component name="content"/>
            </div>   
        </body>
    </html>

</stripes:layout-definition>