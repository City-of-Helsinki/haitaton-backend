FROM registry.redhat.io/openjdk/openjdk-11-rhel7
EXPOSE 8080 8081
VOLUME /tmp
ADD "hanke-service-*.jar" hanke-service.jar
ADD "scripts/wait-for-it.sh" wait-for-it.sh
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/urandom -jar hanke-service.jar" ]
