FROM amazoncorretto:21-alpine-jdk

# Create a directory
WORKDIR /app

# Copy all the files from the current directory to the image
COPY . .

# build the project avoiding tests
# RUN ./gradlew clean build -x test

# Expose port 8080
EXPOSE 8080

# this does not work because gradlew.bat is a windows file
ENTRYPOINT ["./gradlew", "bootRun"]