# ---------- 1) 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐시: gradle 설정 파일만 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 jar 생성 (테스트는 CI에서 돌리므로 여기선 생략)
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---------- 2) 실행 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER spring

ENV TZ=Asia/Seoul \
    JAVA_OPTS="-Xms256m -Xmx1g"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
