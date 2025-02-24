// Use of proxy middleware for local development, assumption is it only listens to port 3000 (confirm reference?)
const {createProxyMiddleware} = require('http-proxy-middleware');
// eslint-disable-next-line import/no-extraneous-dependencies
const Keycloak = require('keycloak-connect');
// eslint-disable-next-line import/no-extraneous-dependencies
const session = require('express-session');

module.exports = app => {
    const store = new session.MemoryStore();

    app.use(
        session({
            secret: 'secret',
            resave: false,
            saveUninitialized: true,
            // Uncomment to limit the session lifetime to 1 minute
            // cookie: {maxAge: 60 * 1000 },
            store
        })
    );

    const keycloak = new Keycloak(
        {
            store
        },
        {
            authServerUrl: 'http://localhost:5100/',
            realm: 'fairspace',
            clientId: 'workspace-client',
            secret: '**********'
        }
    );

    // Return 401 Unauthorized for API requests
    keycloak.redirectToLogin = request => !request.baseUrl.startsWith('/api/');

    app.use(keycloak.middleware({logout: '/logout'}));

    // The next statement enables debugging and setting breakpoints in Intellij and VSCode. Not fully understand it but don't remove :)
    app.use('/dev', keycloak.protect());

    const addToken = (proxyReq, req) =>
        req.kauth.grant && proxyReq.setHeader('Authorization', `Bearer ${req.kauth.grant.access_token.token}`);

    app.use(
        '/api',
        createProxyMiddleware({
            target: 'http://localhost:8080/api',
            on: {
                proxyReq: addToken
            }
        })
    );

    app.use(
        '/actuator/health',
        createProxyMiddleware({
            target: 'http://localhost:8080/actuator/health'
        })
    );
};
