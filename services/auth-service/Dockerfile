FROM quay.io/keycloak/keycloak:12.0.4

USER jboss
RUN mkdir /opt/jboss/keycloak/themes/haitaton
RUN cp -R /opt/jboss/keycloak/themes/keycloak/* /opt/jboss/keycloak/themes/haitaton/.
COPY --chown=jboss:root ./themes/haitaton/login/theme.properties /opt/jboss/keycloak/themes/haitaton/login/.
COPY --chown=jboss:root ./themes/haitaton/login/template.ftl /opt/jboss/keycloak/themes/haitaton/login/.
COPY --chown=jboss:root ./themes/haitaton/login/resources/css/haitaton.css /opt/jboss/keycloak/themes/haitaton/login/resources/css/.
COPY --chown=jboss:root ./themes/haitaton/login/resources/img/bgLogin.jpg /opt/jboss/keycloak/themes/haitaton/login/resources/img/.
COPY --chown=jboss:root ./themes/haitaton/login/messages /opt/jboss/keycloak/themes/haitaton/login/messages
