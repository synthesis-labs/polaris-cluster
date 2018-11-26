FROM gradle:4.2.1-jdk8-alpine as build

ADD --chown=gradle . /home/gradle/project/
RUN cd /home/gradle/project/ && gradle clean installDist

FROM openjdk:10-jre

WORKDIR app
COPY --from=build /home/gradle/project/build/install/facesearch-service .

VOLUME /tmp
