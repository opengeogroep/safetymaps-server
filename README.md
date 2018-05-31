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

For searching for addresses in the NLExtract BAG database using the API path `/api/autocomplete/`, configure the following resource:

```xml
<Resource
   name="jdbc/nlextract-bag"
   auth="Container"
   type="javax.sql.DataSource"
   username="<dbuser>"
   password="<dbpassword>"
   driverClassName="org.postgresql.Driver"
   url="jdbc:postgresql://<server>:<port>/bag"
   maxActive="40"
   validationQuery="select 1"
   timeBetweenEvictionRunsMillis="30000"
   minEvictableIdleTimeMillis="5000"
/>
```

## Mailing functionality

Add to Tomcat server.xml:

```xml
    <Resource name="mail/session" auth="Container" type="javax.mail.Session" mail.smtp.host="localhost" />
```
Add settings to safetymaps.settings table as follows:

```sql
-- Customized mail template, if this setting is not configured the default /WEB-INF/mail.txt template 
-- is used. Put template contents in here, not a filename of a template.
-- In the template, "${param}" will be replaced with the URL-parameter "param". See the support module
-- or the default template for the URL params sent.
--insert into safetymaps.settings(name,value) values ('support_mail_template', 'put customized contents of /WEB-INF/mail.txt here');

-- Use the next parameters to replace a link to the onboard viewer to an online version
-- (`permalink` parameter).
-- The example values will replace URL's like http://10.0.0.1/safetymaps-viewer/?params-with-notice-details
-- with the part before the query path changed to https://online-viewer.yourcompany.com/ so the link in the
-- mail works in an online viewer instead of only working in the onboard viewer environment.
-- Regular expression to match the part to replace
insert into safetymaps.settings(name,value) values ('support_mail_replace_search', 'https?://.*/safetymaps-viewer(.*)');
-- Replacement for the match in the link to the viewer
insert into safetymaps.settings(name,value) values ('support_mail_replacement', 'https://online-viewer.yourcompany.com/safetymaps-viewer$1');

-- Mail address to send the e-mail to
insert into safetymaps.settings(name,value) values ('support_mail_to', 'you@yourfiredepartment.com');
-- From address in the mail
insert into safetymaps.settings(name,value) values ('support_mail_from', 'noreply@yourfiredepartment.com');
-- Mail subject line
insert into safetymaps.settings(name,value) values ('support_mail_subject', 'Notice from SafetyMaps Viewer');
```
