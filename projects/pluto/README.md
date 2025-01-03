# Pluto

The service is a Spring Boot application playing role of a lightweight API gateway using Spring Cloud Gateway.

For a detailed description of the service, see the [Fairspace documentation](../../README.adoc).

## Development

### Running the app in development mode

Prerequisites:

- Java 21
- Gradle
- Running Saturn service
- Running Keycloak instance
- Running PostgreSQL instance (optional)
- Environment variable `KEYCLOAK_CLIENT_SECRET` set to `**********` for local run (or an appropriate value for other environments)

To run the application, execute the following command:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Code style

To ensure a consistent code style, we use the Spotless Gradle plugin. The plugin config is based on Palantir's code style with a few modifications. See the [Gradle file](build.gradle) for the exact configuration.
To format the code, run the following command:

```bash
./gradlew :spotlessApply
```

NOTE: The Spotless plugin also runs as part of the CI pipeline. Build will fail if the code is not formatted correctly.

### Licenses

The project uses the Gradle license plugin to manage licenses. To check if the licenses of the dependencies 
are compatible with list of allowed licenses defined in [allowed-licenses.json](allowed-licenses.json),
run the following command:

```bash
./gradlew :checkLicense
```
