package io.fairspace.saturn.rdf.transactions;

import com.pivovarit.function.ThrowingRunnable;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.query.text.changes.TextQuadAction;
import org.apache.jena.sparql.core.DatasetGraph;

import io.fairspace.saturn.auth.RequestContext;
import io.fairspace.saturn.rdf.AbstractChangesAwareDatasetGraph;

import static io.fairspace.saturn.auth.RequestContext.getClaims;

import static java.lang.System.currentTimeMillis;

@Log4j2
public class TxnLogDatasetGraph extends AbstractChangesAwareDatasetGraph {
    private static final String ERROR_MSG =
            "Catastrophic failure. Shutting down. The system requires admin's intervention.";

    private final TransactionLog transactionLog;
    private volatile RequestContext.SaturnClaims claims;
    private DatasetGraph dsg;

    public TxnLogDatasetGraph(DatasetGraph dsg, TransactionLog transactionLog) {
        super(dsg);
        this.transactionLog = transactionLog;
        this.dsg = dsg;
    }

    /**
     * Collects changes
     */
    @Override
    protected void onChange(TextQuadAction action, Node graph, Node subject, Node predicate, Node object) {
        critical(() -> {
            var newClaims = getClaims();
            if (!newClaims.equals(claims)) {
                this.claims = newClaims;
                transactionLog.onMetadata(this.claims.getSubject(), this.claims.getName(), currentTimeMillis());
            }
            switch (action) {
                case ADD -> transactionLog.onAdd(graph, subject, predicate, object);
                case DELETE -> transactionLog.onDelete(graph, subject, predicate, object);
            }
        });
    }

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }

    @Override
    public void begin(TxnType type) {
        begin(TxnType.convert(type));
    }

    /**
     * Start either a READ or WRITE transaction.
     **/
    @Override
    public void begin(ReadWrite readWrite) {
        super.begin(readWrite);

        if (readWrite == ReadWrite.WRITE) { // a write transaction => be ready to collect changes
            claims = null;
            critical(transactionLog::onBegin);
        }
    }

    @Override
    public void commit() {
        if (isInWriteTransaction()) {
            critical(() -> {
                transactionLog.onCommit();
                super.commit();
            });
        } else {
            super.commit();
        }
    }

    @Override
    public void abort() {
        if (isInWriteTransaction()) {
            critical(transactionLog::onAbort);
        }

        super.abort();
    }

    private boolean isInWriteTransaction() {
        return transactionMode() == ReadWrite.WRITE;
    }

    private void critical(ThrowingRunnable<Exception> action) {
        try {
            action.run();
        } catch (Throwable t) {
            log.error(ERROR_MSG, t);

            // SLF4J has no flush method.
            System.err.println(ERROR_MSG);
            t.printStackTrace();

            System.err.flush();

            log.error(ERROR_MSG, t);
            // There's no log.flush() :-(

            System.exit(1);
        }
    }
}
