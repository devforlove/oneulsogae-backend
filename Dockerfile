# 빌드 스테이지
FROM amazoncorretto:21 AS build
WORKDIR /app
COPY . .
RUN ./gradlew :meeple-api:bootJar --no-daemon

# 런타임 스테이지
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/meeple-api/build/libs/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
