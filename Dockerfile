
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN mkdir -p out && javac -cp "lib/*" -d out src/*.java src/enjami/*.java

EXPOSE 8080

CMD ["sh", "-c", "java -cp 'out:lib/*' enjami.EnjamiServer"]
