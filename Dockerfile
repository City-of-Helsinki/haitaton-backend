FROM openjdk:11-jdk
EXPOSE 8080
VOLUME /tmp
ADD "/build/libs/haitaton-hanke-service-*.jar" haitaton-hanke-service.jar
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/urandom -jar /haitaton-hanke-service.jar" ]
