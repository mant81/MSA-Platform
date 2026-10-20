FROM eclipse-temurin@sha256:dbb43d81099eec91e0800226ed2296d03146ee7857165b95112372ad0d67e87f
ARG SERVICE
ARG REVISION=unknown
LABEL org.opencontainers.image.revision=$REVISION
WORKDIR /app
RUN mkdir /data && chown 10001:10001 /data /app
COPY --chown=10001:10001 --chmod=0444 ${SERVICE}/build/libs/${SERVICE}-0.1.0.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java","-jar","/app/app.jar"]
