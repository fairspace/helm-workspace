package io.fairspace.saturn.webdav;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import io.milton.config.HttpManagerBuilder;
import io.milton.event.ResponseEvent;
import io.milton.http.AuthenticationService;
import io.milton.http.HttpManager;
import io.milton.http.ProtocolHandlers;
import io.milton.http.Request;
import io.milton.http.RequestParseException;
import io.milton.http.ResourceFactory;
import io.milton.http.Response;
import io.milton.http.http11.DefaultHttp11ResponseHandler;
import io.milton.http.webdav.ResourceTypeHelper;
import io.milton.http.webdav.WebDavResponseHandler;
import io.milton.resource.Resource;
import io.milton.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.rdf.model.Literal;

import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.rdf.SparqlUtils.toXSDDateTimeLiteral;

import static io.milton.http.ResponseStatus.SC_UNSUPPORTED_MEDIA_TYPE;
import static io.milton.servlet.MiltonServlet.clearThreadlocals;
import static io.milton.servlet.MiltonServlet.setThreadlocals;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Ensures that all operations are handled in one transaction.
 * Contents of PUT requests is received and saved to the blob store BEFORE transaction is started.
 * Contents of GET responses is sent AFTER transaction is ended.
 */
public class WebDAVServlet extends HttpServlet {
    private static final String BLOB_ATTRIBUTE = "BLOB";
    private static final String TIMESTAMP_ATTRIBUTE = "TIMESTAMP";
    public static final String POST_COMMIT_ACTION_ATTRIBUTE = "POST_COMMIT";
    public static final String ERROR_MESSAGE = "ERROR_MESSAGE";
    public static final String VERSION = "version";

    private final HttpManager httpManager;
    private final BlobStore store;

    public WebDAVServlet(ResourceFactory factory, Transactions txn, BlobStore store) {
        this.store = store;

        httpManager = new HttpManagerBuilder() {
            {
                setResourceFactory(factory);
                setMultiNamespaceCustomPropertySourceEnabled(true);
                setAuthenticationService(new AuthenticationService(singletonList(new SaturnAuthenticationHandler())));
                setValueWriters(new NullSafeValueWriters());
                setEnabledJson(false);
                setEnabledCkBrowser(false);
                setContentGenerator(new AdvancedContentGenerator());
            }

            @Override
            protected void buildProtocolHandlers(
                    WebDavResponseHandler webdavResponseHandler, ResourceTypeHelper resourceTypeHelper) {
                super.buildProtocolHandlers(webdavResponseHandler, resourceTypeHelper);

                setProtocolHandlers(new ProtocolHandlers(getProtocols().stream()
                        .map(p -> new TransactionalHttpExtensionWrapper(p, txn))
                        .collect(toList())));
            }

            @Override
            protected DefaultHttp11ResponseHandler createDefaultHttp11ResponseHandler(
                    AuthenticationService authenticationService) {
                return new DefaultHttp11ResponseHandler(
                        authenticationService, geteTagGenerator(), getContentGenerator()) {
                    @Override
                    public void respondBadRequest(Resource resource, Response response, Request request) {
                        super.respondBadRequest(resource, response, request);
                        getContentGenerator().generate(resource, request, response, Response.Status.SC_BAD_REQUEST);
                    }
                };
            }
        }.buildHttpManager();

        httpManager.getEventManager().registerEventListener(new AuditEventListener(), ResponseEvent.class);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            setThreadlocals(req, res);

            switch (req.getMethod().toUpperCase()) {
                case "PUT" -> req.setAttribute(BLOB_ATTRIBUTE, store.store(req.getInputStream()));
                case "MKCOL" -> {
                    try (var in = req.getInputStream()) {
                        if (in.read() >= 0) {
                            res.setStatus(SC_UNSUPPORTED_MEDIA_TYPE); // RFC2518:8.3.1
                            return;
                        }
                    }
                }
            }

            try {
                httpManager.process(new PreParsedServletRequest(req, store), new ServletResponse(res));
            } catch (RequestParseException e) {
                throw new IOException(e);
            }

            var postCommitAction = (Runnable) req.getAttribute(POST_COMMIT_ACTION_ATTRIBUTE);
            if (postCommitAction != null) {
                postCommitAction.run();
            }
        } finally {
            clearThreadlocals();
            res.getOutputStream().flush();
            res.flushBuffer();
        }
    }

    public static Integer fileVersion() {
        return Optional.ofNullable(getCurrentRequest())
                .map(r -> (isEmpty(getCurrentRequest().getParameter(VERSION))
                        ? r.getHeader("Version")
                        : getCurrentRequest().getParameter(VERSION)))
                .map(Integer::parseInt)
                .orElse(null);
    }

    public static String owner() {
        return Optional.ofNullable(getCurrentRequest())
                .map(r -> r.getHeader("Owner"))
                .orElse(null);
    }

    public static boolean showDeleted() {
        return "on".equalsIgnoreCase(getCurrentRequest().getHeader("Show-Deleted"));
    }

    public static boolean includeMetadataLinks() {
        return "true".equalsIgnoreCase(getCurrentRequest().getHeader("With-Metadata-Links"));
    }

    public static boolean isMetadataRequest() {
        // todo: test the change
        return ("/api/metadata/").equalsIgnoreCase(getCurrentRequest().getRequestURI());
    }

    public static BlobInfo getBlob() {
        return (BlobInfo) getCurrentRequest().getAttribute(BLOB_ATTRIBUTE);
    }

    public static void setErrorMessage(String message) {
        getCurrentRequest().setAttribute(ERROR_MESSAGE, message);
    }

    public static String getErrorMessage() {
        return (String) getCurrentRequest().getAttribute(ERROR_MESSAGE);
    }

    public static Literal timestampLiteral() {
        var r = getCurrentRequest();
        var t = (Literal) r.getAttribute(TIMESTAMP_ATTRIBUTE);
        if (t == null) {
            t = toXSDDateTimeLiteral(Instant.now());
            r.setAttribute(TIMESTAMP_ATTRIBUTE, t);
        }
        return t;
    }
}
