package io.fairspace.saturn.rdf.transactions;

import java.io.IOException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Statement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.context.SecurityContextHolder;

import static io.fairspace.saturn.TestUtils.setupRequestContext;

import static org.apache.jena.rdf.model.ResourceFactory.createPlainLiteral;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStatement;
import static org.apache.jena.sparql.core.DatasetGraphFactory.createTxnMem;
import static org.apache.jena.sparql.core.Quad.defaultGraphNodeGenerated;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class TxnLogDatasetGraphTest {
    @Mock
    private TransactionLog log;

    private Dataset ds;
    private BulkTransactions txn;
    private static final Statement statement = createStatement(
            createResource("http://example.com/s1"),
            createProperty("http://example.com/p1"),
            createPlainLiteral("blah"));

    @Before
    public void before() {
        setupRequestContext();
        ds = DatasetFactory.wrap(new TxnLogDatasetGraph(createTxnMem(), log));
        txn = new BulkTransactions(ds);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldLogWriteTransactions() throws IOException {
        txn.calculateWrite(m -> m.add(statement).remove(statement));

        verify(log).onBegin();
        verify(log).onMetadata(eq("user"), eq("fullname"), anyLong());
        verify(log)
                .onAdd(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log)
                .onDelete(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log).onCommit();
        verifyNoMoreInteractions(log);
    }

    @Test
    public void shouldHandleAbortedTransactions() throws IOException {
        txn.executeWrite(m -> {
            m.add(statement).remove(statement);
            ds.abort();
        });

        verify(log).onBegin();
        verify(log).onMetadata(eq("user"), eq("fullname"), anyLong());
        verify(log)
                .onAdd(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log)
                .onDelete(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log).onAbort();
        verifyNoMoreInteractions(log);
    }

    @Test
    public void shouldNotLogReadTransactions() {
        txn.executeRead(m -> m.listStatements().toList());

        verifyNoMoreInteractions(log);
    }

    @Test
    public void testThatAnExceptionWithinATransactionIsHandledProperly() throws IOException {
        try {
            txn.executeWrite(m -> {
                m.add(statement).remove(statement);
                throw new RuntimeException();
            });
        } catch (Exception ignore) {
        }
        verify(log).onBegin();
        verify(log).onMetadata(eq("user"), eq("fullname"), anyLong());
        verify(log)
                .onAdd(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log)
                .onDelete(
                        defaultGraphNodeGenerated,
                        statement.getSubject().asNode(),
                        statement.getPredicate().asNode(),
                        statement.getObject().asNode());
        verify(log).onAbort();
    }
}
