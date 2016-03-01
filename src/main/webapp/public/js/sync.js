
function syncInit() {
    $("#waiting_tb").on("click", showPopup);
    $("#active_tb").on("click", showPopup);
    $("#offline_tb").on("click", showPopup);

    update();

}

function showPopup(e) {
    var clientId = $(e.target).parent().attr("data-id");

    $("#popup .modal-title").html($.mustache("Details van <b>{{.}}</b>", clientId));

    var startup = null;
    var state = null;
    $.each(window.data.startup, function(i, d) {
        if(d.client_id === clientId) {
            startup = d;
            return false;
        }
    });
    $.each(window.data.state, function(i, d) {
        if(d.client_id === clientId) {
            state = d;
            return false;
        }
    });

    if(startup === null) {
        $("#os").text("?");
    } else {
        $("#os").html($.mustache("{{os.name}}, versie {{os.version}} {{os.arch}}, MAC adres: {{machine_id}}", startup));
        $("#sync").html($.mustache("versie {{version}}, gebouwd op {{git_build_time}}<br>opgestart op {{startup}} ({{sinds}})", {
            version: startup.client.version,
            git_build_time: startup.client.git_build_time,
            startup: moment(startup.start_time).format("LLLL"),
            sinds: moment(startup.start_time).fromNow()
        }));
        $("#java").html($.mustache("{{vm_name}}, {{vm_version}}, {{name}}", startup.runtime));
    }



    if(state === null) {
        $("#filesets_t").hide();
        $("#state").empty();
    } else {
        $("#filesets_t").show();
        $("#filesets_tb").empty();
        $("#state").html("Status van taken zoals gerapporteerd op " + moment(state.report_time).format("LLLL"));

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
        window.data = data; // Useful for debugging
        display(data.state, data.startup);
    })
    .fail(function(jqXHR, textStatus, errorThrown) {
        $("#msg").text("Fout bij ophalen status: " + textStatus + ", " + jqXHR.responseText);
        window.setTimeout(update, 10000);
    });
}

function display(stateReports, startupReports) {
    var stats = {
        offline: 0,
        active: 0,
        waiting: 0
    };

    $("#offline_tb").empty();
    $("#active_tb").empty();
    $("#waiting_tb").empty();

    var activeRows = [];

    $.each(stateReports, function(i, sr) {
        var cutoff = moment().subtract(24, 'hours');
        var time = moment(sr.report_time);

        var view = {
            client_id: sr.client_id,
            datetime: moment(sr.report_time).format("DD-MM-YYYY HH:mm:ss"),
            time: moment(sr.report_time).format("LTS"),
            fromNow: moment(sr.report_time).fromNow(),
            mode: sr.mode
        };

        if(time.isBefore(cutoff)) {
            stats.offline++;
            $("#offline_tb").append($.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{datetime}}</td><td>{{fromNow}}</td></tr>", view));
        } else {
            if(sr.mode.indexOf("waiting") === 0) {
                stats.waiting++;
                $("#waiting_tb").append($.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{time}}</td><td>{{fromNow}}</td><td>{{mode}}</td></tr>", view));
            } else {
                stats.active++;

                view.fileset = sr.current_fileset;

                var fileset = null;
                $.each(sr.filesets, function(j, fs) {
                    if(fs.name === sr.current_fileset) {
                        fileset = fs;
                        return false;
                    }
                });
                if(fileset && fileset.state) {
                    var state = fileset.state;
                    view.action = state.action + ", sinds " + moment(state.action_since).fromNow();
                    var progress = {
                        count: state.progress_count,
                        total: state.progress_count_total,
                        size: state.progress_size ? progress_size / 1024 / 1024 + " MB" : null
                    };
                    if(state.progress_count_total && state.progress_count) {
                        progress.percent = Number(state.progress_count / state.progress_count_total * 100.0).toFixed(2);
                    }
                    view.progress = $.mustache("{{count}} {{#total}}van {{.}}{{#percent}} ({{.}}%){{/percent}}{{/total}}{{#size}}, {{.}}{{/size}}", progress);

                }
                var row = $.mustache("<tr data-id=\"{{client_id}}\"><td>{{client_id}}</td><td>{{time}}</td><td>{{fromNow}}</td><td>{{fileset}}</td><td>{{action}}</td><td>{{progress}}</td></tr>", view);
                activeRows.push({
                    since: fileset && fileset.mode_since ? fileset.mode_since : sr.report_time,
                    row: row
                });
            }
        }
    });

    activeRows.sort(function(lhs, rhs) {
        return lhs.since - rhs.since;
    });
    $.each(activeRows, function(i, r) {
        $("#active_tb").append(r.row);
    });

    $.each(startupReports, function(i, sr) {
        var hasState = false;
        $.each(stateReports, function(j, stateReport) {
            if(stateReport.client_id === sr.client_id && stateReport.machine_id === sr.machine_id) {
                hasState = true;
                return false;
            }
        });
        if(!hasState) {
            stats.offline++;
            $("#offline_tb").append($.mustache("<tr><td>{{client_id}}</td><td colspan=\"2\">Geen status bekend</td></tr>", sr));
        }
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
