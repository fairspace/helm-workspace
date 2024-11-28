package io.fairspace.saturn.webdav;

import io.milton.http.Handler;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.http.exceptions.MiltonException;
import io.milton.resource.Resource;
import lombok.SneakyThrows;

import io.fairspace.saturn.rdf.transactions.Transactions;

class TransactionalHandlerWrapper implements Handler {
    private final Handler wrapped;
    private final Transactions txn;

    public TransactionalHandlerWrapper(Handler wrapped, Transactions txn) {
        this.wrapped = wrapped;
        this.txn = txn;
    }

    @Override
    public String[] getMethods() {
        return wrapped.getMethods();
    }

    @Override
    @SneakyThrows
    public void process(HttpManager httpManager, Request request, Response response) {
        if (request.getMethod().isWrite) {
            try {
                txn.executeWrite(ds -> wrapped.process(httpManager, request, response));
            } catch (MiltonException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                txn.executeRead(ds -> wrapped.process(httpManager, request, response));
            } catch (MiltonException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isCompatible(Resource res) {
        return wrapped.isCompatible(res);
    }
}
