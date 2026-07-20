# 런타임 전용 이미지. jar은 CI 러너(또는 로컬)에서 미리 빌드해 COPY한다.
# (기존의 이미지 내 gradle 빌드 제거 → arm64 빌드 시 QEMU로 gradle을 돌리지 않아 빠르다)
FROM amazoncorretto:21
WORKDIR /app
COPY oneulsogae-api/build/libs/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
