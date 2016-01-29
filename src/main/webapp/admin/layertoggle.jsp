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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Inzetbalk" menuitem="layertoggle">
    <stripes:layout-component name="content">
        
        <h1>Inzetbalk</h1>
        <p>
            Op deze pagina zou de inzetbalk kunnen worden geconfigureerd:
            <ul>
                <li>Kleur</li>
                <li>Symbool</li>
                <li>Gekoppelde lagen</li>
                <li>Standaard aan/uit</li>
                <li>Gekoppelde DBK lagen/symbolen</li>
            </ul>
        </p>

        <h2>Huidige JavaScript config:</h2>
        <pre>
    availableToggles: {
        toggleBasis: {
            label: 'Basisgegevens',
            icon: 'fa-bus',
            layers: [ 'Toegang terrein' ], // XXX
            active: true,
            css: {
                'background-color': 'green',
                color: 'white'
            },
            brandweervoorzieningen: [
                'TbeRIJ',   // Bereidbaar
                'TbeBus',   // Bussluis
                'TbeHoogte',// Doorrijhoogte
                'Tbe05',    // Niet toegankelijk
                'Tbe06',    // Parkeerplaats
                'Tbe02',    // Poller
                'Tbe01',    // Sleutel of ring paal
                'Tr504',    // Indicator/flitslicht
                'Tb1.008',  // Opstelplaats 1e TS
                'Tb1.010',  // Opstelplaats RV
                'Tb1.004a', // BMC
                'Tb1.004',  // Brandweerpaneel
                'To04',     // Brandweerinfokast
                'Tb1.005'   // Nevenpaneel
            ],
            hulplijnen: [
                "Bbarrier",  // Slagboom
                "HEAT"       // Schadecirkel
            ],
            wms: ['Basis'],
            setTopLayerIndex: true
        },
        toggleGebouw: {
            label: 'Gebouwgegevens',
            icon: 'fa-industry',
            layers: [ ], // XXX Binnenmuur
            active: false,
            css: {
                'background-color': 'black',
                color: 'white'
            },
            brandweervoorzieningen: [
                'Tbk7.004', // Lift
                'Tn05',     // Nooduitgang
                'To1.001',  // Trap
                'To1.002',  // Trap rond
                'To1.003',  // Trappenhuis
                'Tb2.025',  // Afsluiter LPG
                'Tb2.021',  // Afsluiter gas
                'Tb2.043',  // Noodstop
                'To03',     // Noodstroom aggegraat
                'Falck11',  // Schacht/kanaal
                'Tb2.002',  // Noodschakelaar CV
                'Tb2.001',  // Noodschakelaar neon
                'Tb2.003',  // Schakelaar elektriciteit
                'Tb2.004',  // Schakelaar luchtbehandeling
                'To02',     // Slaapplaats
                'Falck36'   // Hellingbaan
            ],
            wms: ['Gebouw']
        },
        toggleBluswater: {
            label: 'Bluswatergegevens',
            img: 'images/brandkraan.png',
            style: 'height: 36px; margin-bottom: 5px',
            layers: [ ],
            active: false,
            css: {
                'background-color': '#2D2DFF',
                color: 'white'
            },
            brandweervoorzieningen: [
                'Tb2.024',  // Afsluiter omloopleiding
                'Tb2.026',  // Afsluiter schuimvormend middel
                'Tb2.023',  // Afsluiter sprinkler
                'Tb2.022',  // Afsluiter water
                'Tb02',     // Brandslanghaspel
                'Tb.1007a', // Droge blusleiding afnamepunt
                'Tb4.024',  // Gas blusinstallatie / Blussysteem kooldioxide
                'Tb1.011',  // Gas detectiepaneel
                'Tb4.005'   // Gesprinklerde ruimte
            ],
            hulplijnen: [
                "DBL Leiding"
            ],
            wms: ['Water']
        },
        toggleBrandweer: {
            label: 'Brandweergegevens',
            img: 'images/brwzw.png',
            style: 'width: 32px; margin-bottom: 6px',
            layers: [ 'Brandcompartiment', 'Gevaarlijke stoffen' ],
            active: false,
            css: {
                'background-color': 'red',
                color: 'white'
            },
            brandweervoorzieningen: [
                'Tbk5.001',  // Brandweerlift
                'Tb1.002',   // Overige ingangen / neveningang
                'Tb1.009',   // Opstelplaats overige blusvoertuigen
                'Tb2.005',   // Schakelaar rook-/warmteafvoer
            ],
            wms: ['Brandweer']
        }
    },
        </pre>
    </stripes:layout-component>
</stripes:layout-render>