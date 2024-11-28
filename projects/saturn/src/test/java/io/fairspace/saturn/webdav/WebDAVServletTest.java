package io.fairspace.saturn.webdav;

import java.io.IOException;
import java.util.Vector;

import com.pivovarit.function.ThrowingConsumer;
import io.milton.http.ResourceFactory;
import io.milton.resource.FolderResource;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.webdav.blobstore.BlobInfo;
import io.fairspace.saturn.webdav.blobstore.BlobStore;

import static io.fairspace.saturn.TestUtils.setupRequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WebDAVServletTest {
    @Mock
    ResourceFactory factory;

    @Mock
    Transactions txn;

    @Mock
    BlobStore store;

    @Mock
    HttpServletRequest req;

    @Mock
    HttpServletResponse res;

    @Mock
    ServletInputStream in;

    @Mock
    ServletOutputStream out;

    @Mock
    FolderResource resource;

    WebDAVServlet servlet;

    @Before
    public void before() throws Exception {
        servlet = new WebDAVServlet(factory, txn, store);

        when(req.getRequestURL()).thenReturn(new StringBuffer("http://ex.com/api/webdav/resource"));
        when(req.getInputStream()).thenReturn(in);
        when(req.getParameterNames()).thenReturn(new Vector<String>().elements());
        when(req.getHeaderNames()).thenReturn(new Vector<String>().elements());
        when(res.getOutputStream()).thenReturn(out);
        when(res.getOutputStream()).thenReturn(out);
        when(factory.getResource(any(), any())).thenReturn(resource);
        when(resource.authorise(any(), any(), any())).thenReturn(true);
        when(resource.createNew(any(), any(), any(), any())).thenReturn(resource);

        setupRequestContext();

        doAnswer(invocation -> {
                    ThrowingConsumer job = invocation.getArgument(0);
                    job.accept(null);
                    return null;
                })
                .when(txn)
                .executeRead(any());

        doAnswer(invocation -> {
                    ThrowingConsumer job = invocation.getArgument(0);
                    job.accept(null);
                    return null;
                })
                .when(txn)
                .executeWrite(any());
    }

    @Test
    public void testReadTxn() throws IOException {
        when(req.getMethod()).thenReturn("GET");
        servlet.service(req, res);
        verify(txn).executeRead(any());
    }

    @Test
    public void testWriteTxn() throws IOException {
        when(req.getMethod()).thenReturn("DELETE");
        servlet.service(req, res);
        verify(txn).executeWrite(any());
    }

    @Test
    public void testPutConsumesPayloadOutsideTransaction() throws IOException {
        var blob = new BlobInfo("id", 1, "md5");

        when(req.getMethod()).thenReturn("PUT");
        when(store.store(in)).thenReturn(blob);

        servlet.service(req, res);

        var order = inOrder(store, req, txn);

        order.verify(store).store(in);
        order.verify(req).setAttribute("BLOB", blob);
        // Transaction is executed afterwards
        order.verify(txn).executeWrite(any());
    }

    @Test
    public void testGetPayloadIsReadOutsideTransaction() throws Exception {
        when(req.getMethod()).thenReturn("GET");

        servlet.service(req, res);

        var order = inOrder(txn, resource);

        order.verify(txn).executeRead(any());

        // Called after the transaction is finished
        order.verify(resource).sendContent(eq(out), any(), any(), any());
    }
}
