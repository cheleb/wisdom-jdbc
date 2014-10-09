package org.wisdom.framework.transaction.impl;

import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wisdom.framework.transaction.Propagation;

import javax.transaction.*;
import javax.transaction.xa.XAException;

import static org.assertj.core.api.Assertions.assertThat;

public class PropagationManagerTest {


    TransactionManager manager;
    PropagationManager propagation;


    @Before
    public void setUp() throws XAException {
        manager = new TransactionManagerImpl(10);
        propagation = new PropagationManager(manager);
    }

    @After
    public void tearDown() throws SystemException {
        if (manager.getTransaction() != null  && manager.getStatus() != Status.STATUS_NO_TRANSACTION) {
            manager.rollback();
        }
    }

    @Test
    public void testRequires() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        propagation.onEntry(Propagation.REQUIRES, 0, "route");
        assertThat(manager.getTransaction()).isNotNull();
        Transaction transaction = manager.getTransaction();
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(transaction);
        assertThat(callback.committed).isTrue();
        assertThat(callback.rolledBack).isFalse();
    }

    @Test
    public void testRequiresWithRunningTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();
        assertThat(manager.getTransaction()).isNotNull();

        propagation.onEntry(Propagation.REQUIRES, 0, "route");

        assertThat(manager.getTransaction()).isNotNull();
        assertThat(transaction).isEqualTo(manager.getTransaction());
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        transaction.rollback();
    }

    @Test
    public void testRequiresWithRollback() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        propagation.onEntry(Propagation.REQUIRES, 0, "route");
        assertThat(manager.getTransaction()).isNotNull();
        Transaction transaction = manager.getTransaction();
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        MyTransactionCallback callback = new MyTransactionCallback();
        transaction.setRollbackOnly();
        propagation.onExit(Propagation.REQUIRES, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(transaction);
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isTrue();
    }

    @Test(expected = IllegalStateException.class)
    public void testMandatoryWithoutTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        propagation.onEntry(Propagation.MANDATORY, 0, "route");
    }

    @Test
    public void testMandatoryWithTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.MANDATORY, 0, "route");
        assertThat(manager.getTransaction()).isEqualTo(transaction);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.MANDATORY, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        transaction.commit();
    }

    @Test(expected = IllegalStateException.class)
    public void testNeverWithTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        manager.begin();
        propagation.onEntry(Propagation.NEVER, 0, "route");
    }

    @Test
    public void testNeverWithoutTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        propagation.onEntry(Propagation.NEVER, 0, "route");
        propagation.onExit(Propagation.NEVER, "route", null);
    }

    @Test
    public void testNotSupportedWithRunningTransaction() throws NotSupportedException, RollbackException,
            SystemException, HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.NOT_SUPPORTED, 0, "route");

        // Transaction suspended.
        assertThat(manager.getTransaction()).isNull();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.NOT_SUPPORTED, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        // Transaction resumed.
        assertThat(transaction).isEqualTo(manager.getTransaction());
    }

    @Test(expected = IllegalStateException.class)
    public void testNotSupportedWithRunningTransactionAndANewTransactionCreatedInBetween()
            throws SystemException, NotSupportedException, RollbackException, HeuristicRollbackException,
            HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.NOT_SUPPORTED, 0, "route");

        // Transaction suspended.
        assertThat(manager.getTransaction()).isNull();

        manager.begin();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.NOT_SUPPORTED, "route", callback);
    }

    @Test
    public void testNotSupportedWithRunningTransactionAndAnotherTransactionCreatedAndCommitted()
            throws SystemException, NotSupportedException, RollbackException, HeuristicRollbackException,
            HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.NOT_SUPPORTED, 0, "route");

        // Transaction suspended.
        assertThat(manager.getTransaction()).isNull();

        manager.begin();
        manager.commit();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.NOT_SUPPORTED, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        // Transaction resumed.
        assertThat(transaction).isEqualTo(manager.getTransaction());
    }

    @Test
    public void testNotSupportedWithoutTransaction() throws NotSupportedException, RollbackException, SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();

        propagation.onEntry(Propagation.NOT_SUPPORTED, 0, "route");

        assertThat(manager.getTransaction()).isNull();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.NOT_SUPPORTED, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        assertThat(manager.getTransaction()).isNull();
    }

    @Test
    public void testSupportedWithoutTransaction() throws SystemException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, RollbackException, NotSupportedException {
        assertThat(manager.getTransaction()).isNull();

        propagation.onEntry(Propagation.SUPPORTED, 0, "route");

        assertThat(manager.getTransaction()).isNull();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.SUPPORTED, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        assertThat(manager.getTransaction()).isNull();
    }

    @Test
    public void testSupportedWithRunningTransaction() throws SystemException, NotSupportedException, RollbackException,
            HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.SUPPORTED, 0, "route");

        assertThat(manager.getTransaction()).isEqualTo(transaction);

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.SUPPORTED, "route", callback);

        assertThat(callback.transaction).isNull();
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isFalse();

        assertThat(manager.getTransaction()).isEqualTo(transaction);
    }

    @Test
    public void testRequireNewWithoutTransaction() throws HeuristicRollbackException, HeuristicMixedException,
            InvalidTransactionException, SystemException, RollbackException, NotSupportedException {
        assertThat(manager.getTransaction()).isNull();
        propagation.onEntry(Propagation.REQUIRES_NEW, 0, "route");
        assertThat(manager.getTransaction()).isNotNull();
        Transaction transaction = manager.getTransaction();
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES_NEW, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(transaction);
        assertThat(callback.committed).isTrue();
        assertThat(callback.rolledBack).isFalse();
    }

    @Test
    public void testRequireNewWithRunningTransaction() throws HeuristicRollbackException, HeuristicMixedException,
            InvalidTransactionException, SystemException, RollbackException, NotSupportedException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.REQUIRES_NEW, 0, "route");

        // Check we have another transaction.
        assertThat(manager.getTransaction()).isNotNull().isNotEqualTo(transaction);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        Transaction inner = manager.getTransaction();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES_NEW, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(inner);
        assertThat(callback.committed).isTrue();
        assertThat(callback.rolledBack).isFalse();

        assertThat(transaction).isEqualTo(manager.getTransaction());
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    @Test
    public void testRequireNewWithRunningTransactionAndRollback() throws HeuristicRollbackException,
            HeuristicMixedException,
            InvalidTransactionException, SystemException, RollbackException, NotSupportedException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.REQUIRES_NEW, 0, "route");

        // Check we have another transaction.
        assertThat(manager.getTransaction()).isNotNull().isNotEqualTo(transaction);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        Transaction inner = manager.getTransaction();
        inner.setRollbackOnly();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES_NEW, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(inner);
        assertThat(callback.committed).isFalse();
        assertThat(callback.rolledBack).isTrue();

        assertThat(transaction).isEqualTo(manager.getTransaction());
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    @Test
    public void testNestedRequiresNew() throws HeuristicRollbackException, HeuristicMixedException,
            InvalidTransactionException, SystemException, RollbackException, NotSupportedException {
        assertThat(manager.getTransaction()).isNull();
        manager.begin();
        Transaction transaction = manager.getTransaction();

        propagation.onEntry(Propagation.REQUIRES_NEW, 0, "route");

        // Check we have another transaction.
        assertThat(manager.getTransaction()).isNotNull().isNotEqualTo(transaction);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        Transaction inner = manager.getTransaction();

        propagation.onEntry(Propagation.REQUIRES_NEW, 0, "route-nested");
        assertThat(manager.getTransaction()).isNotNull().isNotEqualTo(transaction).isNotEqualTo(inner);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        Transaction inner2 = manager.getTransaction();

        MyTransactionCallback callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES_NEW, "route-nested", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(inner2);
        assertThat(callback.committed).isTrue();
        assertThat(callback.rolledBack).isFalse();

        callback = new MyTransactionCallback();
        propagation.onExit(Propagation.REQUIRES_NEW, "route", callback);
        assertThat(callback.transaction).isNotNull().isEqualTo(inner);
        assertThat(callback.committed).isTrue();
        assertThat(callback.rolledBack).isFalse();

        assertThat(transaction).isEqualTo(manager.getTransaction());
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    @Test
    public void testOnErrorWithDefault() throws HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, SystemException {
        propagation.onError(new NullPointerException(),
                Propagation.SUPPORTED,
                new Class[] {},
                new Class[] {}, "route", null);
    }

    @Test
    public void testOnErrorWithDefaultAndTransaction() throws HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, SystemException, NotSupportedException {
        manager.begin();
        Transaction transaction = manager.getTransaction();
        propagation.onError(new NullPointerException(),
                Propagation.MANDATORY,
                new Class[] {},
                new Class[] {}, "route", null);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
    }

    @Test
    public void testOnErrorWithNoRollbackAndTransaction() throws HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, SystemException, NotSupportedException {
        manager.begin();
        Transaction transaction = manager.getTransaction();
        propagation.onError(new NullPointerException(),
                Propagation.MANDATORY,
                new Class[] {NullPointerException.class},
                new Class[] {}, "route", null);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    @Test
    public void testOnErrorWithRollbackOnlyAndTransaction() throws HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, SystemException, NotSupportedException {
        manager.begin();
        Transaction transaction = manager.getTransaction();
        propagation.onError(new NullPointerException(),
                Propagation.MANDATORY,
                new Class[] {IllegalStateException.class},
                new Class[] {NullPointerException.class}, "route", null);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_MARKED_ROLLBACK);
    }

    @Test
    public void testOnErrorWithOtherRollbackOnlyAndTransaction() throws HeuristicRollbackException, HeuristicMixedException, InvalidTransactionException, SystemException, NotSupportedException {
        manager.begin();
        Transaction transaction = manager.getTransaction();
        propagation.onError(new NullPointerException(),
                Propagation.MANDATORY,
                new Class[] {},
                new Class[] {IllegalStateException.class}, "route", null);
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
    }

    private class MyTransactionCallback implements TransactionCallback {

        public Transaction transaction;
        public boolean committed;
        public boolean rolledBack;

        @Override
        public void transactionCommitted(Transaction transaction) {
            this.transaction = transaction;
            committed = true;
        }

        @Override
        public void transactionRolledBack(Transaction transaction) {
            this.transaction = transaction;
            rolledBack = true;
        }
    }




}