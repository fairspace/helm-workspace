package io.fairspace.saturn.rdf;

import org.apache.jena.graph.Node;
import org.apache.jena.query.text.changes.TextQuadAction;

interface GraphChangeListener {
    void onChange(TextQuadAction action, Node graph, Node subject, Node predicate, Node object);
}
