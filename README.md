# DBKBackend


Backend voor de AJAX calls die gemaakt worden door het  DBKComponent van @escheper.


## Functionality
Deze webapplicatie maakt de calls naar de Postgres database waarin de DBK objecten staan. Het ondersteund de volgende API-calls:
```
**/api/features.json**  
```
Geeft GeoJSON terug met een featurecollection van alle DBK's. Mogelijke parameter is __srid__ (niet verplicht, defaults naar 28992).

```
**/api/object/<id>.json**
```
Geeft een JSON object terug met een DBKObject. Mogelijke parameter is __srid__ (niet verplicht, defaults naar 28992).

## Installation
Om deze webapp te installeren moet je de tomcat vertellen hoe hij met de database kan connecten. Dit doe je door in tomcat/conf/server.xml het volgende blok onder <GlobalNamingResources> te zetten:

```xml
 <Resource 
    name="jdbc/dbk-api"
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
 
Hier moeten uiteraard de juiste gegevens worden ingevuld. Hierna moet de Postgres driver worden gedownload en worden gekopieerd in tomcat/lib. Deze kan gedownload worden van de volgende site:http://jdbc.postgresql.org/download.html.  
Daarna moet de context.xml aangepast worden om te vertellen waar de mediabestenden worden geupload. Dit kan met de volgende contextparameter:

```xml
  <Parameter name="dbk.media.path" value="<pad/naar/media>" override="false"/>
```
De context.xml kan aangepast worden in de .war file zelf, of hij kan - afhankelijk van de configuratie van tomcat - na deployen in /tomcat/conf/Catalina/localhost/dbk-api.xml (hij wordt door tomcat hernoemd).
Hierna moet tomcat worden herstart en kan de applicatie worden gedeployed.

