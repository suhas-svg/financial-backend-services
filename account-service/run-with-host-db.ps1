# Run the application connecting to PostgreSQL on host machine
# Make sure PostgreSQL is running on your Windows host on port 5432

docker run --rm `
  --name account-service-local `
  -p 8080:8080 -p 9001:9001 `
  -e SPRING_PROFILES_ACTIVE=local `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/myfirstdb" `
  -e SPRING_DATASOURCE_USERNAME=postgres `
  -e SPRING_DATASOURCE_PASSWORD=postgres `
  account-service:local