# point-system-kt
A point system implementation using Kotlin


## Running the project

### Creating the DB tables via docker-compose
```shell
docker-compose --profile commands build
docker-compose run --rm db_creation
```

#### Todo
Currently, there is only a container for creating DB tables.

Need to make more general container for running multiple commands(
such as deleting DB tables or some other admin functions) with options.

In this case, the container `create_db` and the main class file `DBCreation.kd` should be renamed. 

### Running the application via docker-compose
```shell
docker-compose build
docker-compose up
```

## Running the test

### building docker containers
```shell
docker-compose --profile build
```

### Running all tests
```shell
docker-compose run --rm test
docker-compose --profile test stop
```

You need to run `docker-compose --profile test stop` after running a test
in order to stop the test database containers.

Because the test database containers do not have persistent volumes,
stoping the containers will remove all the data.
(I am not really sure if I should make the test containers down instead of stopping in order to reset the data. 
You may want to run `docker-compose --profile test down`)

### Some other testing examples
```shell
# run test with detailed messages
docker-compose run "test" gradle test -i --rm
docker-compose --profile test stop

# run test that includes testSimpleRedis in the path
docker-compose run "test" gradle test --tests "*testSimpleRedis" --rm
docker-compose --profile test stop
```

### Running the test `test` container

If `test_mysql_db` container and `test_redis_cache` containers are running, 
you can run the integration tests outside of the docker container.

#### Some note

The integration tests use `.env.test` file to configure test settings. 

This `.env.test` file's content is replaced by the content of `.env.docker.test` file in `test` container.

The only difference between `.env.test` and `env.docker.test` are the database host names.  
host names are just `localhost` in `env.test` 
while the host names are the host names accessible by `test` container in `env.docker.test`(which are the service names).


### SWagger

Swagger Path(with localhost as the host)
- http://localhost:8080/swagger-ui/index.html 
