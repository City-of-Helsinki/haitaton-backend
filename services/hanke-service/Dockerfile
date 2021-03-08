FROM registry.redhat.io/openjdk/openjdk-11-rhel7
WORKDIR /app
EXPOSE 8080 8081
VOLUME /tmp
COPY hanke-service-*.jar /app/hanke-service.jar
COPY scripts/wait-for-it.sh /app/wait-for-it.sh
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/urandom -jar hanke-service.jar" ]
