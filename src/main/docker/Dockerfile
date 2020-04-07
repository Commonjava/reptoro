FROM fabric8/s2i-java:2.3
ENV JAVA_APP_DIR=/deployments JAVA_OPTIONS="-Dvertx.cacheDirBase=/tmp -Dvertx.disableDnsResolver=true"
LABEL org.label-schema.description="" org.label-schema.version=1.0.0-SNAPSHOT org.label-schema.schema-version=1.0 org.label-schema.build-date=2020-02-26T12:14:12.656 org.label-schema.name=reptoro org.label-schema.vcs-ref=6a027fe935ab79adfefe54966d7182eb727d71c9 org.label-schema.vcs-url=https://github.com/geored/reptoro.git
EXPOSE 8080 8778 9779
USER root
COPY maven /deployments/
ADD maven/Eng_Ops_CA.crt /etc/pki/ca-trust/source/anchors/Eng_Ops_CA.crt
ADD maven/Red_Hat_IS_CA.crt /etc/pki/ca-trust/source/anchors/Red_Hat_IS_CA.crt
ADD maven/RH-IT-Root-CA.crt /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt
#ADD maven/paas_psi_redhat_com.crt /etc/pki/ca-trust/source/anchors/paas_psi_redhat_com.crt
#RUN keytool -import -alias psicert1 -storepass changeit -noprompt -trustcacerts -file /etc/pki/ca-trust/source/anchors/paas_psi_redhat_com.crt -keystore $JAVA_HOME/lib/security/cacerts
RUN keytool -import -alias psicert2 -storepass changeit -noprompt -trustcacerts -file /etc/pki/ca-trust/source/anchors/Eng_Ops_CA.crt -keystore $JAVA_HOME/lib/security/cacerts
RUN keytool -import -alias psicert3 -storepass changeit -noprompt -trustcacerts -file /etc/pki/ca-trust/source/anchors/Red_Hat_IS_CA.crt -keystore $JAVA_HOME/lib/security/cacerts
RUN keytool -import -alias psicert4 -storepass changeit -noprompt -trustcacerts -file /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt -keystore $JAVA_HOME/lib/security/cacerts
RUN update-ca-trust extract
USER 1001
