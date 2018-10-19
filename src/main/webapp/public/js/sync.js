
function syncInit() {
    $("#waiting_tb").on("click", showPopup);
    $("#active_tb").on("click", showPopup);
    $("#offline_tb").on("click", showPopup);

    update();

}

function showPopup(e) {
    var clientId = $(e.target).parent().attr("data-id");

    $("#popup .modal-title").html($.mustache("Details van <b>{{.}}</b>", clientId));

    var client = null;
    $.each(window.clients, function(i, c) {
        if(c.client_id === clientId) {
            client = c;
            return false;
        }
    });

    if(client === null) {
        $("#os").text("-");
        $("#sync").text("-");
        $("#java").text("-");
    } else {
        var startup = client.start_report;
        $("#os").html($.mustache("{{os.name}}, versie {{os.version}} {{os.arch}}", startup));
        $("#sync").html($.mustache("versie {{version}}, gebouwd op {{git_build_time}}<br>opgestart op {{startup}} ({{sinds}})", {
            version: startup.client.version,
            git_build_time: startup.client.git_build_time,
            startup: moment(client.start_time).format("LLLL"),
            sinds: moment(client.start_time).fromNow()
        }));
        $("#java").html($.mustache("{{vm_name}}, {{vm_version}}, {{name}}", startup.runtime));
    }

    if(client.state === null) {
        $("#filesets_t").hide();
        $("#state").empty();
    } else {
        var state = client.state;
        $("#filesets_t").show();
        $("#filesets_tb").empty();
        $("#state").html("Status van taken zoals gerapporteerd op " + moment(client.state_time).format("LLLL"));

        $.each(state.filesets, function(i, fs) {
            if(fs.schedule === "once") {
                return;
            }
            var view = {
                name: fs.name,
                schedule: fs.schedule,
                prio: fs.priority
            };
            if(fs.next_scheduled && fs.next_scheduled !== "ASAP") {
                view.next = moment(fs.next_scheduled).format(fs.schedule === "hourly" ? "HH:mm:ss" : "DD-MM-YYYY HH:mm");
            }
            if(fs.state) {
                var s = fs.state;
                view.state = s.state;
                view.last_succeeded = s.last_succeeded ? moment(s.last_succeeded).format("DD-MM-YYYY HH:mm") : null;
                view.last_run = s.last_run ? moment(s.last_run).format("DD-MM-YYYY HH:mm") : null;
                view.last_finished = s.last_finished ? moment(s.last_finished).format("DD-MM-YYYY HH:mm") : null;
                view.last_result = s.last_finished_state;
            }
            $("#filesets_tb").append($.mustache("<tr><td>{{name}}</td><td>{{state}}</td><td>{{next}}</td>"
                    + "<td>{{prio}}</td><td>{{last_run}}</td><td>{{#last_finished}}{{last_result}} op {{last_finished}}{{/last_finished}}</td><td>{{last_succeeded}}</td></tr>", view));
        });
    }

    $("#popup").modal();
}

function update() {
    $.ajax({
        data: { json: "t" }
    })
    .done(function(data) {
        window.clients = data; // Useful for debugging
        display(data);
    })
    .fail(function(jqXHR, textStatus, errorThrown) {
        $("#msg").text("Fout bij ophalen status: " + textStatus + ", " + jqXHR.responseText);
        window.setTimeout(update, 10000);
    });
}

function display(clients) {
    var stats = {
        offline: 0,
        active: 0,
        waiting: 0
    };

    $("#offline_tb").empty();
    $("#active_tb").empty();
    $("#waiting_tb").empty();

    var activeRows = [];
    var waitingRows = [];

    $.each(clients, function(i, sr) {
        var cutoff = moment().subtract(24, 'hours');
        var time = moment(sr.state_time);

        if(typeof sr.state === 'undefined') {
            stats.offline++;
            $("#offline_tb").append($.mustache("<tr><td>{{client_id}}</td><td colspan=\"2\">Geen status bekend</td></tr>", sr));
            return;
        }

        var view = {
            client_id: sr.client_id,
            datetime: moment(sr.state_time).format("DD-MM-YYYY HH:mm:ss"),
            time: moment(sr.state_time).format("LTS"),
            fromNow: moment(sr.state_time).fromNow(),
            mode: sr.state.mode
        };

        // check if earliest next_scheduled is before now minus 5 min delay
        var now = new Date().getTime();
        var filesetScheduledInPast = null;
        $.each(sr.state.filesets, function(j, fs) {
            if(fs.next_scheduled && fs.next_scheduled !== "ASAP" && fs.next_scheduled < sr.state_time && (fs.next_scheduled < now - (5*60000))) {
                if(filesetScheduledInPast === null || fs.next_scheduled < filesetScheduledInPast.next_scheduled) {
                    filesetScheduledInPast = fs;
                }
            };
        });

        if(time.isBefore(cutoff) || (!sr.state.current_fileset && filesetScheduledInPast)) {
            stats.offline++;
            if(time.isBefore(cutoff)) {
                view.details = "Laatst gezien langer dan 24 uur geleden";
            } else {
                var timeScheduled = moment(filesetScheduledInPast.next_scheduled);
                view.details = "Taak " + filesetScheduledInPast.name + " was gepland op " + timeScheduled.format("LTS") + ", " + timeScheduled.fromNow();
            }
            $("#offline_tb").append($.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{datetime}}, {{fromNow}}</td><td>{{details}}</td></tr>", view));
        } else {
            if(sr.state.mode.indexOf("waiting") === 0) {
                stats.waiting++;
                var row = $.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{time}}</td><td>{{fromNow}}</td><td>{{mode}}</td></tr>", view);
                waitingRows.push({
                    view: view,
                    row: row
                });
            } else {
                stats.active++;

                view.fileset = sr.state.current_fileset;

                var fileset = null;
                $.each(sr.state.filesets, function(j, fs) {
                    if(fs.name === sr.state.current_fileset) {
                        fileset = fs;
                        return false;
                    }
                });
                if(fileset && fileset.state) {
                    var state = fileset.state;
                    view.action = (state.action || state.last_finished_details);// + ", sinds " + moment(state.action_since || state.last_finished).fromNow();
                    var progress = {
                        count: state.progress_count,
                        total: state.progress_count_total,
                        size: state.progress_size ? Number(state.progress_size / 1024 / 1024).toFixed(1) + " MB" : null
                    };
                    if(state.progress_count_total && state.progress_count) {
                        progress.percent = Number(state.progress_count / state.progress_count_total * 100.0).toFixed(2);
                    }
                    view.progress = $.mustache("{{count}} {{#total}}van {{.}}{{#percent}} ({{.}}%){{/percent}}{{/total}}{{#size}}, {{.}}{{/size}}", progress);

                }
                var row = $.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{time}}</td><td></td><td>{{fileset}}</td><td>{{action}}</td><td>{{progress}}</td></tr>", view);
                activeRows.push({
                    since: fileset && fileset.mode_since ? fileset.mode_since : sr.state_time,
                    view: view,
                    row: row
                });
            }
        }
    });

    activeRows.sort(function(lhs, rhs) {
        return lhs.view.client_id.localeCompare(rhs.view.client_id);
    });
    $.each(activeRows, function(i, r) {
        $("#active_tb").append(r.row);
    });

    waitingRows.sort(function(lhs, rhs) {
        return lhs.view.client_id.localeCompare(rhs.view.client_id);
    });
    $.each(waitingRows, function(i, r) {
        $("#waiting_tb").append(r.row);
    });

    $("#offline").toggle(stats.offline !== 0);
    $("#offline_h").text("(" + stats.offline +")");

    $("#active").toggle(stats.active !== 0);
    $("#active_h").text("(" + stats.active +")");

    $("#waiting").toggle(stats.waiting !== 0);
    $("#waiting_h").text("(" + stats.waiting +")");

    if(stats.offline === 0 && stats.active === 0 && stats.waiting === 0) {
        $("#msg").text("Geen synchronisatierapportages beschikbaar");
    } else {
        $("#msg").text("Status op " + moment().format("LLLL"));
    }

    window.setTimeout(update, stats.active > 0 ? 1000 : 5000);
}
