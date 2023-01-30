FROM openjdk:17

ADD target/Substitution_R4B-1.0.jar app.jar

EXPOSE 8082

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
