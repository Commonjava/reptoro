FROM fabric8/java-centos-openjdk8-jdk:1.5
ENV JAVA_APP_DIR=/deployments JAVA_OPTIONS="-Dvertx.cacheDirBase=/tmp -Dvertx.disableDnsResolver=true -Dvertx.metrics.options.enabled=true -Dvertx.metrics.options.jmxEnabled=true -Dvertx.metrics.options.jmxDomain=vertx"
LABEL org.label-schema.description="" org.label-schema.version=1.0.0-SNAPSHOT org.label-schema.schema-version=1.0 org.label-schema.build-date=2019-11-08T23:57:24.783825 org.label-schema.name=repomigrator-test
EXPOSE 8080 8778 9779
COPY maven /deployments/
