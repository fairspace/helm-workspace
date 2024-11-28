package io.fairspace.saturn.rdf;

import org.apache.jena.graph.Node;
import org.apache.jena.query.text.changes.DatasetGraphTextMonitor;
import org.apache.jena.query.text.changes.TextQuadAction;
import org.apache.jena.sparql.core.DatasetGraph;

import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;

/**
 * A DatasetGraphMonitor which handles changes itself.
 * Can be useful if changes-handling logic depends on Graph's state.
 * Normally DatasetGraphMonitor reports changes to an external listener.
 * That can be very inconvenient, if you need to implement complex logic involving not only quad operations, but also
 * some other aspect's of the dataset graph;s behavior, e.g. transaction lifecycle.
 */
public abstract class AbstractChangesAwareDatasetGraph extends DatasetGraphTextMonitor {

    public AbstractChangesAwareDatasetGraph(DatasetGraph dsg) {
        super(dsg, new DelegatingDatasetChanges(), true);

        ((DelegatingDatasetChanges) getMonitor()).setChangeListener(this::onChange); // delegates handling to itself
    }

    protected void onChange(TextQuadAction action, Node graph, Node subject, Node predicate, Node object) {}

    @Override
    public String toString() {
        return getClass().getName() + "@" + toHexString(identityHashCode(this));
    }
}
