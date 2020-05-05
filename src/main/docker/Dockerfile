#FROM fabric8/s2i-java:2.3
#FROM registry.redhat.io/redhat-openjdk-18/openjdk18-openshift
FROM jorgemoralespou/s2i-java
ENV JAVA_APP_DIR=/deployments \
    JAVA_OPTIONS="-Dvertx.cacheDirBase=/tmp -Dvertx.disableDnsResolver=true"
# -Dvertx.disableDnsResolver=true
LABEL org.label-schema.description="" \
        org.label-schema.version=1.0.0-SNAPSHOT \
        org.label-schema.schema-version=1.0 \
        org.label-schema.build-date=2020-02-26T12:14:12.656 \
        org.label-schema.name=reptoro \
        org.label-schema.vcs-ref=6a027fe935ab79adfefe54966d7182eb727d71c9 \
        org.label-schema.vcs-url=https://github.com/geored/reptoro.git
EXPOSE 8080 8778 9779
#USER root
COPY maven /deployments/
#RUN cd /etc/pki/ca-trust/source/anchors
#RUN curl -O https://password.corp.redhat.com/RH-IT-Root-CA.crt
#COPY maven/Eng_Ops_CA.crt /etc/pki/ca-trust/source/anchors/Eng_Ops_CA.crt
#COPY maven/Red_Hat_IS_CA.crt /etc/pki/ca-trust/source/anchors/Red_Hat_IS_CA.crt
#COPY maven/RH-IT-Root-CA.crt /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt
#COPY maven/paas_psi_redhat_com.crt /etc/pki/ca-trust/source/anchors/paas_psi_redhat_com.crts
#RUN cd /etc/pki/ca-trust/source/anchors && update-ca-trust enable && update-ca-trust extract

#ENV jdk.tls.client.cipherSuites true

#RUN cd /etc/pki/ca-trust/source/anchors && \
#    curl -O --insecure https://password.corp.redhat.com/RH-IT-Root-CA.crt && \
#    curl -O --insecure https://password.corp.redhat.com/legacy.crt && \
#    curl -O --insecure https://engineering.redhat.com/Eng-CA.crt && \
#    update-ca-trust enable \
#    update-ca-trust extract

#RUN cd $JAVA_HOME/lib/security \
#    && keytool -keystore cacerts -storepass changeit -noprompt -trustcacerts -importcert -alias redhatcert -file /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt

#RUN keytool -genkey -keyalg RSA -alias redhat -keystore keystore.jks -validity 360 -keysize 2048s
#RUN keytool -import -alias redhat.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/paas_psi_redhat_com.crt -keystore $JAVA_HOME/lib/security/cacerts
#RUN keytool -import -alias redhat.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/Eng_Ops_CA.crt -keystore $JAVA_HOME/lib/security/cacerts
#RUN keytool -import -alias redhat1.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/Red_Hat_IS_CA.crt -keystore $JAVA_HOME/lib/security/cacerts
#RUN keytool -import -alias redhat2.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt -keystore $JAVA_HOME/lib/security/cacerts

#RUN keytool -import -alias redhat.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/Eng-CA.crt -keystore $JAVA_HOME/lib/security/cacerts
#RUN keytool -import -alias redhat1.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/legacy.crt -keystore $JAVA_HOME/lib/security/cacerts
#RUN keytool -import -alias redhat2.com -storepass changeit -noprompt -trustcacerts -import -file /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt -keystore $JAVA_HOME/lib/security/cacerts
#
#USER 1001