#Gradlew Build Jar
FROM adoptopenjdk/openjdk11:alpine-slim as build
COPY . /
RUN ./gradlew shadow --stacktrace

#Main image
FROM adoptopenjdk/openjdk11:alpine-slim
COPY --from=build /build/libs/DiscordTeamBot-all.jar app.jar

ENTRYPOINT []