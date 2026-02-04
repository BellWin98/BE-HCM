FROM amazoncorretto:21-alpine-jdk

# 빌드 결과물 위치 지정
ARG JAR_FILE=build/libs/*.jar

# 환경 변수 기본값 설정 (기본은 prod로 설정하고, 필요 시 실행 시점에 변경 가능)
ENV SPRING_PROFILES_ACTIVE=prod

COPY ${JAR_FILE} app.jar

# 실행 명령어: SPRING_PROFILES_ACTIVE 변수를 적용하여 실행
ENTRYPOINT ["java", "-jar", "-Duser.timezone=Asia/Seoul", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "/app.jar"]