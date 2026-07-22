# 런타임 전용 이미지. jar은 CI 러너(또는 로컬)에서 미리 빌드해 COPY한다.
# (기존의 이미지 내 gradle 빌드 제거 → arm64 빌드 시 QEMU로 gradle을 돌리지 않아 빠르다)
FROM amazoncorretto:21
WORKDIR /app
COPY oneulsogae-api/build/libs/*.jar app.jar
EXPOSE 8080
# LocalDateTime(zone 없음)이 JVM 기본 타임존으로 기록되므로 KST로 고정한다.
# (미설정 시 컨테이너 기본 UTC로 저장·응답되어 클라이언트 표시가 9시간 어긋난다)
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Duser.timezone=Asia/Seoul"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
