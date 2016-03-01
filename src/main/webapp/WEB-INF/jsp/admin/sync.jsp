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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Synchronisatie" menuitem="sync">
    <stripes:layout-component name="head">
        <script type="text/javascript" src="${contextPath}/public/js/sync.js"></script>
        
        <style>
            #info table tbody td { 
                cursor: pointer;
                white-space: nowrap;
            } 
            #info table tbody tr:hover {
                background-color: #b9def0;
            }
           /* #info table th {
                text-align: center;
            }*/
        </style>
    </stripes:layout-component>
    <stripes:layout-component name="content">

        <script>
            $(document).ready(syncInit);
        </script>
        <h1>Synchronisatiestatus voertuigen</h1>
        
        <div id="popup" class="modal fade" tabindex="-1">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                        <h4 class="modal-title">Details van </h4>                        
                    </div>
                    <div class="modal-body">
                        <div class="row">
                            <div class="col-md-2">OS:</div>
                            <div class="col-md-10" id="os"></div>
                        </div>
                        <div class="row">
                            <div class="col-md-2">Synchronisatietool:</div>
                            <div class="col-md-10" id="sync"></div>
                        </div>
                        <div class="row">
                            <div class="col-md-2">Java:</div>
                            <div class="col-md-10" id="java"></div>
                        </div>
                        <div id="state" style="padding-top: 4px; font-style: italic"></div>
                        <table id="filesets_t" class="table">
                            <thead>
                                <tr><th>Naam</th><th>Status</th><th>Planning</th><th>Prio</th><th>Laatst gestart</th><th>Laatste resultaat</th><th>Laatst succesvol</th></tr>
                            </thead>
                            <tbody id="filesets_tb">
                                
                            </tbody>
                        </table>
                    </div>
                    <div class="modal-footer"></div>
                        <button type="button" class="btn btn-default" data-dismiss="modal">Sluiten</button>                
                    </div>
                </div>
            </div>
        </div>
        <div id="msg"></div>
        
        <div id="info" >
            <div id="offline">
                <h2>Offline langer dan 1 dag <span id="offline_h"/></h2>
                <table class="table">
                    <thead>
                        <tr><th>Naam</th><th colspan="2">Laatst gezien</th></tr>
                    </thead>
                    <tbody id="offline_tb">

                    </tbody>
                </table>
            </div>

            <div id="active">
                <h2>Actief <span id="active_h"/></h2>

                <table class="table">
                    <thead>
                        <tr><th>Naam</th><th colspan="2">Laatste update</th><th>Taak</th><th colspan="2">Voortgang</th></tr>
                    </thead>
                    <tbody id="active_tb">

                    </tbody>
                </table>
            </div>

            <div id="waiting">
                <h2>Wachtend <span id="waiting_h"/></h2>

                <table class="table">
                    <thead>
                        <tr><th>Naam</th><th colspan="2">Laatste update</th><th>Modus</th></tr>
                    </thead>
                    <tbody id="waiting_tb">

                    </tbody>
                </table>
            </div>
        </div>
    </stripes:layout-component>
</stripes:layout-render>