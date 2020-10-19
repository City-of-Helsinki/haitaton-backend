FROM registry.redhat.io/openjdk/openjdk-11-rhel7
EXPOSE 8080
VOLUME /tmp
ADD "haitaton-hanke-service-*.jar" haitaton-hanke-service.jar
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/urandom -jar haitaton-hanke-service.jar" ]
