FROM java:8-alpine
MAINTAINER Helping Hands <helpinghands@hh.com>

ADD target/helping-hands-alert-0.0.1-SNAPSHOT-standalone.jar /helping-hands/app.jar
ADD config /helping-hands/

EXPOSE 8080

CMD ["java", "-Dconf=/helping-hands/config/conf.edn" "-jar", "/helping-hands/app.jar"]
