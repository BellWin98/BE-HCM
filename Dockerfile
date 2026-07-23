FROM amazoncorretto:25-alpine-jdk

# 빌드 결과물 위치 지정
ARG JAR_FILE=build/libs/*.jar

# 환경 변수 기본값 설정 (기본은 prod로 설정하고, 필요 시 실행 시점에 변경 가능)
ENV SPRING_PROFILES_ACTIVE=prod

COPY ${JAR_FILE} app.jar

# 실행 명령어: SPRING_PROFILES_ACTIVE 변수를 적용하여 실행
#
# --enable-native-access=ALL-UNNAMED: JDK 24 의 JEP 472 부터 System.loadLibrary 같은 제한된
# 메서드를 네이티브 접근 허용 없이 호출하면 경고가 뜨고, 향후 릴리스에서는 아예 차단된다.
# Netty(netty-common)가 epoll 네이티브 라이브러리를 시도하면서 이 경고를 내므로 미리 허용해 둔다.
# fat jar 의 클래스는 모두 unnamed module 이라 ALL-UNNAMED 로 지정한다.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "-Duser.timezone=Asia/Seoul", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "/app.jar"]