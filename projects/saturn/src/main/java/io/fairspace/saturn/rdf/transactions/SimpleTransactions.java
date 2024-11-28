package io.fairspace.saturn.rdf.transactions;

import com.pivovarit.function.ThrowingFunction;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "application.jena.bulkTransactions", havingValue = "false")
public class SimpleTransactions extends BaseTransactions {

    public SimpleTransactions(@Qualifier("dataset") Dataset ds) {
        super(ds);
    }

    @Override
    public <R, E extends Exception> R calculateWrite(ThrowingFunction<? super Model, R, E> job) throws E {
        return Txn.calculateWrite(ds, () -> ThrowingFunction.sneaky(job).apply(ds.getDefaultModel()));
    }
}
