# DBKBackend


Backend for the AJAX calls made by the DBKComponent of @escheper.


## Functionality
Deze webapplicatie maakt de calls naar de Postgres database waarin de DBK objecten staan. Het ondersteund de volgende API-calls:

**/api/features.json**
Hij geeft GeoJSON terug met een featurecollection van alle DBK's

__ToDo:__
srid als parameter implementeren

**/api/object/<id>.json**


__ToDo:__
srid als parameter implementeren


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
 
Hier moeten uiteraard de juiste gegevens worden ingevuld. Hierna moet de Postgres driver worden gedownload en worden gekopieerd in tomcat/lib. Deze kan [hier | http://jdbc.postgresql.org/download.html] gedownload worden.
Hierna moet tomcat worden herstart en kan de applicatie worden gedeployed.

### 


