FROM java:8-alpine
MAINTAINER Helping Hands <helpinghands@hh.com>

COPY target/helping-hands-auth-0.0.1-SNAPSHOT-standalone.jar /helping-hands/app.jar
COPY config/conf.edn /helping-hands/

EXPOSE 8080

CMD exec java -Dconf=/helping-hands/conf.edn -jar /helping-hands/app.jar
