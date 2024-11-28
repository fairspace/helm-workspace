package io.fairspace.saturn.rdf;

import org.apache.jena.query.text.changes.TextDatasetChanges;

/**
 * A class providing default implementations for rarely used methods of DatasetChanges
 */
public abstract class AbstractDatasetChanges implements TextDatasetChanges {
    @Override
    public void start() {}

    @Override
    public void finish() {}

    @Override
    public void reset() {}
}
