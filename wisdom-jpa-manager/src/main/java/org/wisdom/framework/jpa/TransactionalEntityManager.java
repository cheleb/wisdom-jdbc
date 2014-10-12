/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.jpa;


import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.metamodel.Metamodel;
import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Map;

/**
 * A Transactional Entity Manager delegates all requests to another Entity
 * Manager. The delegate is created when it is needed for the first time in a
 * transaction and later reused. The delegate is automatically closed at the end
 * of the transaction.
 */
@SuppressWarnings("rawtypes")
class TransactionalEntityManager implements EntityManager {

    private final TransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final PersistenceUnitComponent unit;
    final ThreadLocal<EntityManager> perThreadEntityManager = new ThreadLocal<>();
    volatile boolean open = true;

    //TODO Manage transaction manager in the request scope.

    public TransactionalEntityManager(TransactionManager tm, EntityManagerFactory emf, PersistenceUnitComponent unit) {
        this.transactionManager = tm;
        this.entityManagerFactory = emf;
        this.unit = unit;
    }


    /**
     * The delegated methods call this method to get the delegate. This method
     * verifies if we're still open, if there already is an Entity Manager for
     * this thread and otherwise creates it and enlists it for auto close at the
     * current transaction.
     *
     * @return an Entity Manager
     */
    private EntityManager getEM() throws IllegalStateException {
        if (!open) {
            throw new IllegalStateException("The JPA bridge has closed");
        }

        try {

            // Do we already have one on this thread?

            EntityManager em = perThreadEntityManager.get();
            if (em != null) {
                return em;
            }


            // Nope, so we need to check if there actually is a transaction
            final Transaction transaction = transactionManager.getTransaction();
            if (transaction == null) {
                throw new TransactionRequiredException("Cannot create an EM since no transaction active");
            }

            // Ok, now we can create one since we can also close it.
            final Thread transactionThread = Thread.currentThread();

            em = entityManagerFactory.createEntityManager();


            if ("managed".equalsIgnoreCase(unit.getProperties().getProperty("openjpa.TransactionMode"))) {

                try {
                    // Register a callback at the end of the transaction
                    transaction.registerSynchronization(new Synchronization() {

                        @Override
                        public void beforeCompletion() {
                            if (!open)
                                throw new IllegalStateException(
                                        "The Transaction Entity Manager was closed in the mean time");
                        }

                        @Override
                        public void afterCompletion(int arg0) {
                            // TODO this is true?
                            assert transactionThread == Thread.currentThread();

                            EntityManager em = perThreadEntityManager.get();
                            perThreadEntityManager.set(null);
                            em.close();
                        }
                    });
                } catch (Exception e) {
                    em.close();
                    throw new IllegalStateException("Registering synchronization to close EM", e);
                }
            }

            //
            // Make it available for later calls on this thread
            //
            perThreadEntityManager.set(em);

            //
            // And make sure it joins the current transaction. I guess
            // this means that the Jta Data Source is used?
            //
            em.joinTransaction();
            return em;
        } catch (Exception e) {
            throw new IllegalStateException("Error while retrieving entity manager", e);
        }

    }

    void shutdown() {
        open = false;
    }

    /**
     * We automatically close so ignore.
     */
    @Override
    public void close() {
    }

    @Override
    public void clear() {
        getEM().clear();
    }

    @Override
    public boolean contains(Object arg0) {
        return getEM().contains(arg0);
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(String arg0, Class<T> arg1) {
        return getEM().createNamedQuery(arg0, arg1);
    }

    @Override
    public Query createNamedQuery(String arg0) {
        return getEM().createNamedQuery(arg0);
    }

    @Override
    public Query createNativeQuery(String arg0, Class arg1) {
        return getEM().createNativeQuery(arg0, arg1);
    }

    @Override
    public Query createNativeQuery(String arg0, String arg1) {
        return getEM().createNativeQuery(arg0, arg1);
    }

    @Override
    public Query createNativeQuery(String arg0) {
        return getEM().createNativeQuery(arg0);
    }

    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> arg0) {
        return getEM().createQuery(arg0);
    }

    @Override
    public <T> TypedQuery<T> createQuery(String arg0, Class<T> arg1) {
        return getEM().createQuery(arg0, arg1);
    }

    @Override
    public Query createQuery(String arg0) {
        return getEM().createQuery(arg0);
    }

    @Override
    public void detach(Object arg0) {
        getEM().detach(arg0);
    }

    @Override
    public <T> T find(Class<T> arg0, Object arg1, LockModeType arg2, Map<String, Object> arg3) {
        return getEM().find(arg0, arg1, arg2, arg3);
    }

    @Override
    public <T> T find(Class<T> arg0, Object arg1, LockModeType arg2) {
        return getEM().find(arg0, arg1, arg2);
    }

    @Override
    public <T> T find(Class<T> arg0, Object arg1, Map<String, Object> arg2) {
        return getEM().find(arg0, arg1, arg2);
    }

    @Override
    public <T> T find(Class<T> arg0, Object arg1) {
        return getEM().find(arg0, arg1);
    }

    @Override
    public void flush() {
        getEM().flush();
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return getEM().getCriteriaBuilder();
    }

    @Override
    public Object getDelegate() {
        return getEM().getDelegate();
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return getEM().getEntityManagerFactory();
    }

    @Override
    public FlushModeType getFlushMode() {
        return getEM().getFlushMode();
    }

    @Override
    public LockModeType getLockMode(Object arg0) {
        return getEM().getLockMode(arg0);
    }

    @Override
    public Metamodel getMetamodel() {
        return getEM().getMetamodel();
    }

    @Override
    public Map<String, Object> getProperties() {
        return getEM().getProperties();
    }

    @Override
    public <T> T getReference(Class<T> arg0, Object arg1) {
        return getEM().getReference(arg0, arg1);
    }

    @Override
    public EntityTransaction getTransaction() {
        return getEM().getTransaction();
    }

    @Override
    public boolean isOpen() {
        return getEM().isOpen();
    }

    @Override
    public void joinTransaction() {
        getEM().joinTransaction();
    }

    @Override
    public void lock(Object arg0, LockModeType arg1, Map<String, Object> arg2) {
        getEM().lock(arg0, arg1, arg2);
    }

    @Override
    public void lock(Object arg0, LockModeType arg1) {
        getEM().lock(arg0, arg1);
    }

    @Override
    public <T> T merge(T arg0) {
        return getEM().merge(arg0);
    }

    @Override
    public void persist(Object arg0) {
        getEM().persist(arg0);
    }

    @Override
    public void refresh(Object arg0, LockModeType arg1, Map<String, Object> arg2) {
        getEM().refresh(arg0, arg1, arg2);
    }

    @Override
    public void refresh(Object arg0, LockModeType arg1) {
        getEM().refresh(arg0, arg1);
    }

    @Override
    public void refresh(Object arg0, Map<String, Object> arg1) {
        getEM().refresh(arg0, arg1);
    }

    @Override
    public void refresh(Object arg0) {
        getEM().refresh(arg0);
    }

    @Override
    public void remove(Object arg0) {
        getEM().remove(arg0);
    }

    @Override
    public void setFlushMode(FlushModeType arg0) {
        getEM().setFlushMode(arg0);
    }

    @Override
    public void setProperty(String arg0, Object arg1) {
        getEM().setProperty(arg0, arg1);
    }

    @Override
    public <T> T unwrap(Class<T> arg0) {
        return getEM().unwrap(arg0);
    }
}
