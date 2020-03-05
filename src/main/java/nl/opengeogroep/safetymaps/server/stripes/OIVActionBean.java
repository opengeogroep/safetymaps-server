package nl.opengeogroep.safetymaps.server.stripes;

import java.io.OutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.server.db.JSONUtils;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.logExceptionAndReturnJSONObject;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.KeyedHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/viewer/api/oiv/{path}")
public class OIVActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog(OIVActionBean.class);

    private static final String MAP_OBJECTEN = "features.json";
    private static final String TYPE_OBJECT = "object";

    @Validate
    private String path;

    @Validate
    private int indent = 0;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }    

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public Resolution api() {
        try(Connection c = DB.getConnection()) {
            if(path != null) {
                Object result;
                JSONObject r = new JSONObject();

                if(MAP_OBJECTEN.equals(path)) {
                    result = objectsJson(c);
                    r.put("success", true);
                    r.put("results", result);
                } else if(path.indexOf(TYPE_OBJECT + "/") == 0) {
                    r = objectJson(c, Integer.parseInt(getIdFromPath(TYPE_OBJECT)));
                } else {
                    return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: " + getContext().getRequest().getRequestURI());
                }
            
                return jsonStreamingResolution(r, null);
            }

            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: " + getContext().getRequest().getRequestURI());
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on " + getContext().getRequest().getRequestURI(), e).toString(indent));
        }
    }

    private Resolution jsonStreamingResolution(final JSONObject j, final Long lastModified) {
        return new Resolution() {
            @Override
            public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                String encoding = "UTF-8";
                response.setCharacterEncoding(encoding);
                response.setContentType("application/json");
                if(lastModified != null) {
                    response.addDateHeader("Last-Modified", lastModified * 1000);
                }

                OutputStream out;
                String acceptEncoding = request.getHeader("Accept-Encoding");
                if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    response.setHeader("Content-Encoding", "gzip");
                    out = new GZIPOutputStream(response.getOutputStream(), true);
                } else {
                    out = response.getOutputStream();
                }
                IOUtils.copy(new StringReader(j.toString(indent)), out, encoding);
                out.flush();
                out.close();
            }
        };
    }

    private String getIdFromPath(String type) throws Exception {
        Pattern p = Pattern.compile(type + "\\/([0-9]+)\\.json");
        Matcher m = p.matcher(path);

        if(!m.find()) {
            throw new Exception("No " + type + " id found in path: " + getContext().getRequest().getRequestURI());
        } else {
            return m.group(1);
        }
    }    

    private static JSONArray objectsJson (Connection c) throws Exception {
        List<Map<String,Object>> rows = DB.oivQr().query("select id " +
                "	, '' as oms_nummer " +
                "	, formelenaam as formele_naam " +
                "	, '' as informele_naam " +
                "	, 'Object' as symbool " +
                "	, st_astext(st_centroid(geom)) as pand_centroid " +
                "	, null as selectiekader " +
                "	, null as selectiekader_centroid " +
                "	, box2d(geom)::varchar as extent " +
                "	, '' as straatnaam " +
                "	, 0 as huisnummer " +
                "	, '' as huisletter " +
                "	, '' as toevoeging " +
                "	, '' as postcode " +
                "	, '' as plaats " +
                "from objecten.view_objectgegevens"
            , new MapListHandler());

        Set<Integer> layerIds = new HashSet(DB.oivQr().query("select object_id " +
            "from objecten.view_bouwlagen " +
            "where object_id is not null"
            , new ColumnListHandler<>()));

        JSONArray objects = new JSONArray();

        for(Map<String, Object> row: rows) {
            Integer object_id = (Integer)row.get("id");
            JSONObject object = rowToJson(row, true, true);
            if(layerIds.contains(object_id)) {
                object.put("heeft_verdiepingen", true);
            }
            objects.put(object);
        }

        return objects;
    }

    private static JSONObject objectJson (Connection c, int id) throws Exception {
        List<Map<String,Object>> rows = DB.oivQr().query("select basic.id " +
        "	, '' as oms_nummer " +
        "	, og.formelenaam as formele_naam " +
        "	, '' as informele_naam " +
        "	, 0 as bouwlaag_max " +
        "	, 0 as bouwlaag_min " +
        "	, null as datum_controle " +
        "	, og.datum_gewijzigd as datum_actualisatie " +
        "	, false as bhv_aanwezig " +
        "	, '' as inzetprocedure " +
        "	, '' as risicoklasse " +
        "	, 'Object' as symbool " +
        "	, 0 as adres_id " +
        "	, basic.object_id as hoofdobject_id " +
        "	, basic.bouwlaag " +
        "	, '' as gebruikstype " +
        "	, '' as gebruikstype_specifiek " +
        "	, og.bijzonderheden " +
        "	, '' as bijzonderheden2 " +
        "	, '' as prev_bijz_1 " +
        "	, '' as prev_bijz_2 " +
        "	, '' as prep_bijz_1 " +
        "	, '' as prep_bijz_2 " +
        "	, '' as repr_bijz_1 " +
        "	, '' as repr_bijz_2 " +
        "	, '' as straatnaam " +
        "	, 0 as huisnummer " +
        "	, '' as huisletter " +
        "	, '' as toevoeging " +
        "	, '' as postcode " +
        "	, '' as plaats " +
        "	, '' as gebouwconstructie " +
        "	, (select array_to_json(array_agg(row_to_json(bl.*)))  " + 
        "	   from (select 1000000000 + id as id, bouwlaag::varchar, formelenaam as formele_naam, '' as informele_naam, false as is_hoofdobject " +     
        "	 	     from objecten.view_bouwlagen " +        
        "	 	     where object_id = basic.object_id) bl " +
        "	  ) as verdiepingen " +
        "	, null as verblijf " +
        "	, null as bijzonderhedenlijst " +
        "	, null as contacten " +
        "	, null as media " +
        "	, (select array_to_json(array_agg(row_to_json(cc.*))) " + 
        "      from (select ap.soort as aanvullende_informatie, false as dekking, coalesce(ap.handelingsaanwijzing , '') as alternatief, st_astext(ap.geom) as location " +
        "            from objecten.view_afw_binnendekking ap " +
        "            where ap.bouwlaag_id = basic.bouwlaag_id) cc " +
        "     ) as communication_coverage " +
        "	, (select array_to_json(array_agg(row_to_json(s.*))) " +
        "      from (select ap.symbol_name as code, coalesce(ap.label, '') as omschrijving, coalesce(ap.rotatie, 0) as rotation, '' as picture, st_astext(ap.geom) as location " +
        "		     from objecten.view_veiligh_install ap " +	         
        "		   	 where ap.geom is not null and ap.bouwlaag_id = basic.bouwlaag_id " +
        "		     union " +
        "		     select ap.symbol_name as code, coalesce(ap.label, '') as omschrijving, coalesce(ap.rotatie, 0) as rotation, '' as picture, st_astext(ap.geom) as location " +
        "		     from objecten.view_veiligh_ruimtelijk ap " +	         
        "		     where ap.geom is not null and ap.object_id = basic.object_id " +
        "            union " +
        "            select ap.symbol_name as code, coalesce(ap.label, '') as omschrijving, coalesce(ap.rotatie, 0) as rotation, '' as picture, st_astext(ap.geom) as location " +
        "            from objecten.view_ingang_bouwlaag ap " + 
        "            where ap.geom is not null and ap.bouwlaag_id = basic.bouwlaag_id " +
        "            union " +
        "            select ap.symbol_name as code, coalesce(ap.label, '') as omschrijving, coalesce(ap.rotatie, 0) as rotation, '' as picture, st_astext(ap.geom) as location " +
        "            from objecten.view_ingang_ruimtelijk ap " +
        "            where ap.geom is not null and ap.object_id = basic.object_id " +
        "		  	) s " +
        "	  ) as symbols " +
        "	, (select array_to_json(array_agg(row_to_json(fc.*)))  " +
        "      from (select ap.soort as style, '' as omschrijving, '' as label, st_astext(ap.geom) as line " +
        "            from objecten.view_veiligh_bouwk ap " +
        "           where ap.bouwlaag_id = basic.bouwlaag_id) fc " +
        "      ) as fire_compartmentation " +
        "	, null as select_area " +
        "	, null as custom_polygons " +
        "	, (select array_to_json(array_agg(row_to_json(s.*))) " +
        "      from (select ap.omschrijving as omschrijving, 'Gevaarlijke stof' as symbol, ap.gevi_nr as gevi_code, ap.vn_nr as un_nr, ap.hoeveelheid, ap.omschrijving as naam_stof, st_astext(op.geom) as location " +
        "            from objecten.view_gevaarlijkestof_bouwlaag ap " +
        "            join objecten.gevaarlijkestof_opslag op on op.id = ap.opslag_id " +
        "                where ap.geom is not null and op.bouwlaag_id = basic.bouwlaag_id " +
        "            union " +
        "            select ap.omschrijving as omschrijving, 'Gevaarlijke stof' as symbol, ap.gevi_nr as gevi_code, ap.vn_nr as un_nr, ap.hoeveelheid, ap.omschrijving as naam_stof, st_astext(op.geom) as location " +
        "            from objecten.view_gevaarlijkestof_ruimtelijk ap " +
        "            join objecten.gevaarlijkestof_opslag op on op.id = ap.opslag_id " +
        "            where ap.geom is not null and op.object_id = basic.object_id " +
        "           ) s " +
        "      ) as danger_symbols " +
        "	, null as lines " +
        "	, (select array_to_json(string_to_array(st_astext(bl.geom), ';')) " +
        "	   from (select geom " +
        "	         from objecten.view_bouwlagen " +
        "	         where id = basic.bouwlaag_id) bl " +
        "	  ) as buildings " +
        "	, null as labels " +
        "	, null as approach_routes " +
        "from (select id, id as object_id, 0::varchar as bouwlaag, null as bouwlaag_id " +
        "	  from objecten.view_objectgegevens " +
        "	  union select 1000000000 + id as id, object_id, bouwlaag::varchar as bouwlaag, id as bouwlaag_id " +
        "	  from objecten.view_bouwlagen) as basic " +
        "join objecten.view_objectgegevens og " +
        "	on og.id = basic.object_id " +
        "where basic.id = ?"
            , new MapListHandler(), id);

        if(rows.isEmpty()) {
            throw new IllegalArgumentException("OIV object met ID " + id + " niet gevonden");
        }

        return rowToJson(rows.get(0), true, true);
    }
}