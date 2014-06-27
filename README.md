# DBKBackend


Backend for the AJAX calls made by the DBKComponent of @escheper.


## Functionality
Deze webapplicatie maakt de calls naar de Postgres database waarin de DBK objecten staan. Het ondersteund de volgende API-calls:

** /api/features.json **
Hij geeft GeoJSON terug met een featurecollection van alle DBK's

''ToDo:''
srid als parameter implementeren

** /api/object/<id>.json

''ToDo:''
srid als parameter implementeren


## Installation



### Tomcat/conf/server.xml
### dbk-api/META-INF/context.xml


