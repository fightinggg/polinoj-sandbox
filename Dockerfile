FROM maven:3.8.1-openjdk-8
COPY . /app
WORKDIR /app
RUN mvn package -Dmaven.test.skip=true -q

FROM openjdk:8-jre
COPY --from=0 /app/target/*.jar /app/main.jar
ENV JAVA_PARAM ''
ENV JVM_PARAM ''
CMD "java"  $JVM_PARAM "-jar" "/app/main.jar" $JAVA_PARAM
