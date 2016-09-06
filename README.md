# Safetymaps-server

Provides server-side functionality and configuration for online SafetyMaps DBK viewer and filesetsync-server
monitoring.

## DBK functionality

Functionality for the features module:
```
**/api/features.json**  
```
GeoJSON with all DBK's. Optional parameter is __srid__ (defaults to 28992).

```
**/api/object/<id>.json**
```
JSON object with DBKObject. Optional parameter is __srid__ (defaults to 28992).

Note the `/media/` path is not handled, configure webserver to map it to the media directory.

## Installation

Execute scripts db/safetymaps.sql to create the safetymaps schema. Edit the settings in safetymaps.settings table.

|**Setting name** | **Description** |
|:---------------:|:---------------:|
| `title` | Instance title |
| `static_url` | URL with link to online static viewer |
| `static_mapserver_searchdirs` | Directory to search for mapfiles |
| `static_outputdir` | Directory the update script updates |
| `static_update_command` | Command to call script for updating static viewer |

Add to tomcat/conf/server.xml the following under <GlobalNamingResources>:

```xml
 <Resource 
    name="jdbc/safetymaps-server"
    auth="Container"
    type="javax.sql.DataSource"
    username="<dbuser>"
    password="<dbpassword>"
    driverClassName="org.postgresql.Driver"
    url="jdbc:postgresql://<server>:<port>/<dbname>"
    maxActive="40"
    validationQuery="select 1"
    timeBetweenEvictionRunsMillis="30000"
    minEvictableIdleTimeMillis="5000"
/>
```
Use the correct database settings and download the PostgreSQL JDBC driver (http://jdbc.postgresql.org/download.html) and put it in tomcat/lib.

To display sync status from filesetsync-server, configure the following resource:

```xml
<Resource
   name="jdbc/filesetsync-server"
   auth="Container"
   type="javax.sql.DataSource"
   username="<dbuser>"
   password="<dbpassword>"
   driverClassName="org.postgresql.Driver"
   url="jdbc:postgresql://<server>:<port>/<dbname>?currentSchema=sync"
   maxActive="40"
   validationQuery="select 1"
   timeBetweenEvictionRunsMillis="30000"
   minEvictableIdleTimeMillis="5000"
/>
```

