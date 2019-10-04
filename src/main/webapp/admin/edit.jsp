<%--
Copyright (C) 2016 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>


<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Tekenen" menuitem="edit">
    <stripes:layout-component name="content">
        
        <h1>Tekening</h1>

        Met onderstaande knop kan de huidige tekening in GeoJSON formaat worden opgeslagen:
        <p><p>
        <button class="btn btn-primary" onclick="download()">Tekening opslaan</button>

        <script>

            function download() {

                $.ajax("${contextPath}/viewer/api/edit", {
                    method: "GET"
                })
                .done(function(result) {
                    var element = document.createElement('a');
                    element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(result));
                    element.setAttribute('download', "lcms-tekening.json");

                    element.style.display = 'none';
                    document.body.appendChild(element);

                    element.click();

                    document.body.removeChild(element);
                });
            }

        </script>
      <p><p>
        Laden van eerder opgeslagen tekening (GeoJSON formaat, nog geen validatie):
      <p><p>
        <input type="file" id="file-input" class="btn btn-primary"></input>

        <script>
            function readFile(e) {
                var file = e.target.files[0];
                if (!file) {
                    return;
                }
                var reader = new FileReader();
                reader.onload = function(e) {
                    var contents = e.target.result;
                    console.log("Loaded file",contents);

                    $.ajax("${contextPath}/viewer/api/edit", {

                        method: "POST",
                        data: {
                            save: "true",
                            features: contents
                        }
                    })
                    .done(function(result) {
                        $("#msg").text("Tekening is geladen!");
                    });
                };
                reader.readAsText(file);
            }
            document.getElementById('file-input')
              .addEventListener('change', readFile, false);
        </script>

        <p><p id="msg">
    </stripes:layout-component>
</stripes:layout-render>