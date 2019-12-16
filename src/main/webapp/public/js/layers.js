
// TODO set in JSP from config
var staticPrefix = "/cgi-bin/mapserv.exe?map=c:/ogg/mapserver/webdav/";
var onlinePrefix = "/cgi-bin/mapserv?map=/home/webdev/mapserver/data/webdav/";

// TODO configure function in config 
function convertStaticWmsUrl(url, toStatic, toOnline) {
    var path = url;
    if(staticPrefix && staticPrefix.length > 0 && url.indexOf(staticPrefix) === 0) {
        path = url.substring(staticPrefix.length);
    }
    if(onlinePrefix && onlinePrefix.length > 0 && url.indexOf(onlinePrefix) === 0) {
        path = url.substring(onlinePrefix.length).replace("l_", "");
    }
    if(toStatic) {
        return staticPrefix + path;
    } else if(toOnline) {
        var i = path.lastIndexOf("/");
        path = path.substring(0,i+1) + "l_" + path.substring(i+1);
        return onlinePrefix + path;
    }
    return path;
}

var defaultLayersOption = "Laag toevoegen...";

function findLayers() {
    $("#select-layer option").each(function() {
        if(this.value !== defaultLayersOption) {
            $(this).remove();
        }
    });
    var url = $("input[name='layer.url']").val();
    var haveLayers = false;
    $.each(mapFiles, function(i, m) {
        if(m.path === convertStaticWmsUrl(url)) {
            $.each(m.layers, function(j, l) {
                $("<option/>").text(l).appendTo("#select-layer");
            });
            haveLayers = true;
            return false;
        }
    });
    $("#select-layer").css("visibility", haveLayers ? "visible" : "hidden");
}

function layersInit() {
    var url = $("input[name='layer.url']").val();

    if(url.length > 0 && typeof convertStaticWmsUrl !== "undefined") {
        url = convertStaticWmsUrl(url);
    }

    var defaultOption = $("#select-mapfiles").val();

    $.each(mapFiles, function(i, m) {
        $("<option/>").text(m.path).attr("selected", m.path === url).appendTo("#select-mapfiles");
    });

    findLayers();

    $("#select-mapfiles").change(function() {
        if(this.value !== defaultOption) {
            $("input[name='layer.url']").val(staticPrefix + this.value);
            findLayers();
        }
    });

    $("#input-url").change(findLayers);

    $("#select-layer").change(function() {
        if(this.value !== defaultLayersOption) {
            var layersParam = $("input[name='layersParam']");
            layersParam.val(layersParam.val() === "" ? this.value : layersParam.val() + "," + this.value);
        }
        $(this).val(defaultLayersOption);
    });
}

