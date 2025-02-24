# Mercury

This application contains the user interface to list, manage and
explore collections and metadata in Fairspace.

For a detailed description of the application, see the [Fairspace documentation](../../README.adoc).

## Development

When starting development in Mercury,
please read about the [project structure](Structure.md) first.

### Running the app in development mode

Prerequisites:

- [nodejs and npm](https://www.npmjs.com/get-npm)
- [yarn](https://yarnpkg.com/lang/en/)
- docker
- Java 17

The Mercury frontend app needs a backend to communicate with. For convenience, there are a few scripts to use for local development:

- `yarn localdevelopment` starts Keycloak at port `5100`.
- `yarn saturn` starts the backend at port `8090`.
- `yarn pluto` starts a proxy server that also serves the frontend at port `8080`.
- `yarn start` starts the frontend and exposes it at port `3000`.

For the first run, execute the following command to install all dependencies locally:

```bash
yarn install
```

To start all these components at once, run:

```bash
yarn dev
```

This will start:

- Keycloak (http://localhost:5100)
- PostgreSQL (port `9432`)
- Saturn (JDK 17 required, port `8090`)
- Pluto (proxy, including Mercury frontend, http://localhost:8080)
- Mercury frontend in development mode (http://localhost:3000)
- a [backend proxy](src/setupProxy.js) for Mercury

The backend and frontend application will be available at http://localhost:8080/

A reloading version of the frontend application is available at http://localhost:3000.
Sometimes it is needed to navigate to http://localhost:3000/dev to login due to an issue in the backend proxy.

Keycloak has preconfigured user accounts `user`, `user2`, `organisation-admin`, `datasteward`, `external-user`, `coordinator` and `coordinator2` with password `fairspace123`.

In Keycloak, one setting needs to be configured manually: the permissions of the client service account. Follow the [local development instructions](https://docs.fairway.app/#_local_development) to configure these.

### Libraries

- The UI uses many components from [Material UI](https://material-ui.com/).
- [axios](https://github.com/axios/axios) is used for http requests.
- [webdav](https://github.com/perry-mitchell/webdav-client) is used for connecting to WebDAV servers.
- [jsonld](https://github.com/digitalbazaar/jsonld.js) is used for unpacking JSON-LD responses.

### Code style

The project has extended the eslint configuration by React and AirBnb. At the moment it is enforced and executed at project building,
and therefore it is recommended to use a plugin for eslint in your favorite IDE. The rules are in the _.eslintrc.js_ file.

You can also run [ESLint] manually by doing:

```bash
yarn lint
```

Besides ESlint, the project uses [Prettier] for code formatting. The rules are defined in the _.prettierrc.js_ config file. Auto code formatting is executed at project building. To run it manually, use the following command:

```bash
yarn format
```

Some types have been added using [flow]. To run the flow type checker:

```bash
yarn flow
```

### Testing

[Jest] is the main testing framework and runner for unit tests.
It is encouraged to use the [React Testing Library] for testing React components.
However, some tests still use [Enzyme].

To run the test scripts:

```bash
yarn test
```

[flow]: https://flow.org/en/docs/lang/
[Jest]: https://jestjs.io/docs/en/getting-started
[Enzyme]: https://airbnb.io/enzyme/
[React Testing Library]: https://github.com/testing-library/react-testing-library
[Prettier]: https://prettier.io/
[ESLint]: https://eslint.org/
