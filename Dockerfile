FROM maven:3.8.1-openjdk-8
COPY . /app
WORKDIR /app
RUN mvn package -Dmaven.test.skip=true -q

FROM openjdk:8-jre
RUN wget https://get.docker.com/builds/Linux/x86_64/docker-1.13.0.tgz \
    && tar -xzvf docker-1.13.0.tgz \
    && rm docker-1.13.0.tgz \
    && mv docker/docker /usr/local/bin/ \
    && rm -rf docker
COPY --from=0 /app/target/*.jar /app/main.jar
ENV JAVA_PARAM ''
ENV JVM_PARAM ''
CMD "java"  $JVM_PARAM "-jar" "/app/main.jar" $JAVA_PARAM
