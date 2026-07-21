# 1. 빌드 스테이지
# Java 21 및 빌드 속도가 빠른 정식 eclipse-temurin 이미지를 사용합니다.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .

# gradlew 실행 권한 부여 및 빌드 (테스트는 제외하여 시간 단축)
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar -x test

# 2. 실행 스테이지
# 배포용 컨테이너는 가벼운 JRE 환경으로 구성하여 용량을 줄입니다.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 jar 파일만 쏙 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Java 21 가상머신 최적화 옵션을 추가하여 실행
ENTRYPOINT ["java", "-jar", "-Dfile.encoding=UTF-8", "-Dspring.profiles.active=prod", "app.jar"]
