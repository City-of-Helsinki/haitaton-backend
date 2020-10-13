FROM gradle:jdk11 as staticbuilder
ADD . /build
WORKDIR /build
RUN gradle build --no-daemon
 

FROM openjdk:11-jdk as production
EXPOSE 8080
VOLUME /tmp
COPY --from=staticbuilder "/build/build/libs/haitaton-hanke-service-*-SNAPSHOT.jar" app.jar
CMD [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
