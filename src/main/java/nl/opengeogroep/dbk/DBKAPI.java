/*
 * Copyright (C) 2014 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.opengeogroep.dbk;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Meine Toonen
 */
public class DBKAPI extends HttpServlet {

    private static final Log log = LogFactory.getLog(DBKAPI.class);    
    private static final String API_PART = "/api/";
    private static final String FEATURES = "features.json";
    private static final String OBJECT = "object/";
    private static final String GEBIED = "gebied/";
    private static final String MEDIA = "media/";
    private static final String JSON = ".json";
    
    private static final String PARAMETER_SRID = "srid";
   
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        String method = null;
        OutputStream out = response.getOutputStream();
        try{
            String requestedUri = request.getRequestURI();
            method = requestedUri.substring(requestedUri.indexOf(API_PART)+ API_PART.length());
            JSONObject output = new JSONObject();
            if(method.contains(FEATURES)||method.contains(OBJECT) || method.contains(GEBIED)){
                if(method.contains(FEATURES)){
                    output = processFeatureRequest(request);    
                }else if(method.contains(OBJECT)){
                    output = processObjectRequest(request,method);
                }else {
                    output = processGebiedRequest(request,method);
                }
                response.setContentType("application/json;charset=UTF-8");
                out.write(output.toString().getBytes("UTF-8"));
            } else if(method.contains(MEDIA)){
                processMedia(method,request,response,out);
            }else{
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                output.put("success", Boolean.FALSE);
                output.put("message", "Requested method not understood. Method was: " + method + " but expected are: " + FEATURES +", " + OBJECT + " or " +MEDIA);
                out.write(output.toString().getBytes());
            }
        }catch (IllegalArgumentException ex){
            response.setContentType("text/plain;charset=UTF-8");
            log.error("Error happened with " + method +":",ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write(ex.getLocalizedMessage().getBytes());
        }catch(Exception e){
            log.error("Error happened with " + method +":",e );
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write(("Exception occured:" +e.getLocalizedMessage()).getBytes());
        }finally{
            out.flush();
            out.close();
        }
    }

    /**
     * Process the call to /api/features.json[?srid=<integer>]
     * @param request The requestobject
     * @return A JSONObject with the GeoJSON representation of all the DBK's
     * @throws SQLException 
     */
    private JSONObject processFeatureRequest(HttpServletRequest request) throws SQLException, Exception {
        JSONObject geoJSON = new JSONObject();
        JSONArray jFeatures = new JSONArray();
        boolean hasParameter = request.getParameter(PARAMETER_SRID) != null;
        Connection conn = getConnection();
        if(conn == null){
            throw new Exception("Connection could not be established");
        }
        MapListHandler h = new MapListHandler();
        QueryRunner run = new QueryRunner();
        
        geoJSON.put("type", "FeatureCollection");
        geoJSON.put("features",jFeatures);
        try {
            List<Map<String,Object>> features;
            if(hasParameter){
                String sridString = request.getParameter(PARAMETER_SRID);
                Integer srid = Integer.parseInt(sridString);
                features = run.query(conn, "select \"feature\" from dbk.dbkfeatures_json(?)", h,srid);
            }else{
                features = run.query(conn, "select \"feature\" from dbk.dbkfeatures_json()", h);
            }
            
            for (Map<String, Object> feature : features) {
                JSONObject jFeature = processFeature(feature);
                jFeatures.put(jFeature);
            }
        } finally {
            DbUtils.close(conn);
        }
        return geoJSON;
    }
    
    /**
     * Method for requesting a single DBKObject
     * @param request The HttpServletRequest of this request
     * @param method Method containing possible (mandatory!) parameters: the id
     * @return An JSONObject representing the requested DBKObject, of an empty JSONObject if none is found
     * @throws Exception 
     */
    private JSONObject processObjectRequest(HttpServletRequest request,String method) throws Exception {
        JSONObject json = new JSONObject();
        boolean hasSrid = request.getParameter(PARAMETER_SRID) != null;
        Connection conn = getConnection();
        if(conn == null){
            throw new Exception("Connection could not be established");
        }
        MapHandler h = new MapHandler();
        QueryRunner run = new QueryRunner();
        
        String idString = null;
        Integer id = null; 
        try{
            idString = method.substring(method.indexOf(OBJECT) + OBJECT.length(), method.indexOf(JSON));
            id = Integer.parseInt(idString);
        }catch(NumberFormatException ex){
            throw new IllegalArgumentException("Given id not correct, should be an integer. Was: " + idString);
        }
        try {
            Map<String, Object> feature;
            if (hasSrid) {
                String sridString = request.getParameter(PARAMETER_SRID);
                Integer srid = Integer.parseInt(sridString);
                feature = run.query(conn, "select \"DBKObject\" from dbk.dbkobject_json(?,?)", h, id, srid);
            } else {
                feature = run.query(conn, "select \"DBKObject\" from dbk.dbkobject_json(?)", h, id);
            }
            if(feature == null){
                throw new IllegalArgumentException("Given id didn't yield any results.");
            }
            JSONObject pgObject = new JSONObject( feature.get("DBKObject"));
            json = new JSONObject();
            json.put("DBKObject", new JSONObject(pgObject.getString("value")));
           
        } finally {
            DbUtils.close(conn);
        }
        return json;
    }

    private JSONObject processGebiedRequest(HttpServletRequest request,String method) throws Exception{
        JSONObject json = new JSONObject();
         boolean hasSrid = request.getParameter(PARAMETER_SRID) != null;
        Connection conn = getConnection();
        if(conn == null){
            throw new Exception("Connection could not be established");
        }
        MapHandler h = new MapHandler();
        QueryRunner run = new QueryRunner();

        String idString = null;
        Integer id = null;
        try{
            idString = method.substring(method.indexOf(GEBIED) + GEBIED.length(), method.indexOf(JSON));
            id = Integer.parseInt(idString);
        }catch(NumberFormatException ex){
            throw new IllegalArgumentException("Given id not correct, should be an integer. Was: " + idString);
        }
        try {
            Map<String, Object> feature;
            if (hasSrid) {
                String sridString = request.getParameter(PARAMETER_SRID);
                Integer srid = Integer.parseInt(sridString);
                feature = run.query(conn, "select \"DBKGebied\" from dbk.dbkgebied_json(?,?)", h, id, srid);
            } else {
                feature = run.query(conn, "select \"DBKGebied\" from dbk.dbkgebied_json(?)", h, id);
            }
            if(feature == null){
                throw new IllegalArgumentException("Given id didn't yield any results.");
            }
            JSONObject pgObject = new JSONObject( feature.get("DBKGebied"));
            json = new JSONObject();
            json.put("DBKGebied", new JSONObject(pgObject.getString("value")));

        } finally {
            DbUtils.close(conn);
        }
        return json;
    }
    
    /**
     * Processes the request for retrieving the media belonging to a DBK.
     * @param method The part of the url after /api/, containing the file name (and possible subdirectory)
     * @param request The http request
     * @param response The http response
     * @param out The outputstream to which the file must be written.
     * @throws IOException 
     */
    private void processMedia( String method, HttpServletRequest request,HttpServletResponse response, OutputStream out) throws IOException {
        FileInputStream fis = null;
        File requestedFile = null;
        String basePath = request.getServletContext().getInitParameter("dbk.media.path");
        try {
            String fileArgument = method.substring(method.indexOf(MEDIA) + MEDIA.length());
            String totalPath = basePath + File.separatorChar + fileArgument;

            totalPath = URLDecoder.decode(totalPath,response.getCharacterEncoding());
            requestedFile = new File(totalPath);

            fis = new FileInputStream(requestedFile);
            response.setContentType(request.getServletContext().getMimeType(totalPath));
            Long size = requestedFile.length();
            response.setContentLength(size.intValue());
            IOUtils.copy(fis, out);
        } catch (IOException ex) {
            log.error("Error retrieving media.", ex);
            if (requestedFile != null) {
                log.error("Cannot load media: " + requestedFile.getCanonicalPath() + " from basePath: " + basePath);
            }
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }finally{
            if(fis != null){
                fis.close();
            }
        }
    }
    
    private JSONObject processFeature(Map<String,Object> feature){
        JSONObject jsonFeature = new JSONObject();
        JSONObject properties = new JSONObject();
        
        JSONObject  dbFeatureObject = new JSONObject(feature.get("feature"));
        JSONObject featureValues = new JSONObject(dbFeatureObject.getString("value"));
        
        jsonFeature.put("type", "Feature");
        jsonFeature.put("id", "DBKFeature.gid--" + featureValues.get("gid"));
        jsonFeature.put("geometry", featureValues.get("geometry"));
        
        jsonFeature.put("properties", properties);
        for (Iterator it = featureValues.keys(); it.hasNext();) {
            String key = (String)it.next();
            if(key.equals("geometry")){
                continue;
            }
            Object value = featureValues.get(key);
            properties.put(key, value);
        }
        return jsonFeature;
    }

    public Connection getConnection() {
        try {
            InitialContext cxt = new InitialContext();
            if (cxt == null) {
                throw new Exception("Uh oh -- no context!");
            }
            
            DataSource ds = (DataSource) cxt.lookup("java:/comp/env/jdbc/dbk-api");
            
            if (ds == null) {
                throw new Exception("Data source not found!");
            }
            Connection connection = ds.getConnection();
            return connection;
        } catch (NamingException ex) {
            log.error("naming",ex);
        } catch (Exception ex) {
            log.error("exception",ex);
        }
        return null;
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
           log.error("GET failed: ",ex);
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
           log.error("POST failed: ",ex);
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
