FROM gradle:8.11.1-jdk23 as builder
WORKDIR /home/gradle/src

COPY --chown=gradle:gradle . /home/gradle/src
COPY .env.docker.test /home/gradle/src/.env.test
USER root
RUN chown -R gradle /home/gradle/src

USER gradle
RUN gradle clean build -x test

FROM amazoncorretto:23-alpine-jdk
RUN mkdir /app

COPY --from=builder /home/gradle/src/build/libs/*.jar /app/
COPY .env.docker /app/.env

# build the project avoiding tests
# RUN ./gradlew clean build -x test

# Expose port 8080
EXPOSE 8080

WORKDIR /app
CMD ["java", "-jar", "point-0.0.1-SNAPSHOT.jar"]
