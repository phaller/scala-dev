/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.concurrent;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import scala.concurrent.forkjoin.*;

abstract class AbstractPromise extends RecursiveAction {
    private volatile Object _ref = FState.EmptyPending();
    protected final static AtomicReferenceFieldUpdater<AbstractPromise, Object> updater =
            AtomicReferenceFieldUpdater.newUpdater(AbstractPromise.class, Object.class, "_ref");

    protected void compute() { /* do nothing */ }
}
