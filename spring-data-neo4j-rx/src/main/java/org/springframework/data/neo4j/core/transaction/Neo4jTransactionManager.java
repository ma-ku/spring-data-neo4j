/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.core.transaction;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.apiguardian.api.API;
import org.neo4j.driver.v1.AccessMode;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.TransactionConfig;
import org.springframework.lang.Nullable;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.InvalidIsolationLevelException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.Assert;

/**
 * Dedicated {@link org.springframework.transaction.PlatformTransactionManager} for native Neo4j transactions.
 *
 * @author Michael J. Simons
 */
public class Neo4jTransactionManager extends AbstractPlatformTransactionManager {

	private final Driver driver;

	@API(status = API.Status.STABLE, since = "1.0")
	public Neo4jTransactionManager(Driver driver) {
		this.driver = driver;
	}

	@Override
	protected Object doGetTransaction() throws TransactionException {

		Neo4jResourceHolder resourceHolder = (Neo4jResourceHolder) TransactionSynchronizationManager
			.getResource(driver);
		return new Neo4jTransactionObject(resourceHolder);
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) throws TransactionException {

		return extractNeo4jTransaction(transaction).hasResourceHolder();
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(transaction);

		TransactionConfig transactionConfig = createTransactionConfigFrom(definition);
		boolean readOnly = definition.isReadOnly();
		AccessMode accessMode = readOnly ? AccessMode.READ : AccessMode.WRITE;
		List<String> bookmarks = Collections.emptyList(); // TODO Bookmarksupport

		TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);

		try {
			Session session = this.driver.session(accessMode, bookmarks);

			Neo4jResourceHolder resourceHolder = new Neo4jResourceHolder(session, transactionConfig);
			transactionObject.setResourceHolder(resourceHolder);

			resourceHolder.setSynchronizedWithTransaction(true);
			TransactionSynchronizationManager.bindResource(driver, resourceHolder);
		} catch (Exception ex) {
			throw new TransactionSystemException(String.format("Could not open a new Neo4j session: %s", ex.getMessage()));
		}
	}

	@Override
	protected Object doSuspend(Object transaction) throws TransactionException {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(transaction);
		transactionObject.setResourceHolder(null);

		return TransactionSynchronizationManager.unbindResource(driver);
	}

	@Override
	protected void doResume(@Nullable Object transaction, Object suspendedResources) {

		TransactionSynchronizationManager.bindResource(driver, suspendedResources);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) throws TransactionException {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(status);
		transactionObject.getRequiredResourceHolder().commit();
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) throws TransactionException {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(status);
		transactionObject.getRequiredResourceHolder().rollback();
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(status);
		transactionObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {

		Neo4jTransactionObject transactionObject = extractNeo4jTransaction(transaction);
		transactionObject.getRequiredResourceHolder().close();

		TransactionSynchronizationManager.unbindResource(driver);
	}

	private static TransactionConfig createTransactionConfigFrom(TransactionDefinition definition) {

		if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
			throw new InvalidIsolationLevelException(
				"Neo4jTransactionManager is not allowed to support custom isolation levels.");
		}

		if (definition.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) {
			throw new IllegalTransactionStateException("Neo4jTransactionManager only supports 'required' propagation.");
		}

		TransactionConfig.Builder builder = TransactionConfig.builder();
		if (definition.getTimeout() > 0) {
			builder = builder.withTimeout(Duration.ofSeconds(definition.getTimeout()));
		}

		return builder.build();
	}

	private static Neo4jTransactionObject extractNeo4jTransaction(Object transaction) {

		Assert.isInstanceOf(Neo4jTransactionObject.class, transaction,
			() -> String.format("Expected to find a %s but it turned out to be %s.", Neo4jTransactionObject.class,
				transaction.getClass()));

		return (Neo4jTransactionObject) transaction;
	}

	private static Neo4jTransactionObject extractNeo4jTransaction(DefaultTransactionStatus status) {

		return extractNeo4jTransaction(status.getTransaction());
	}

	static class Neo4jTransactionObject implements SmartTransactionObject {

		private static final String RESOURCE_HOLDER_NOT_PRESENT_MESSAGE = "Neo4jResourceHolder is required but not present. o_O";

		// The resource holder is null when the call to TransactionSynchronizationManager.getResource
		// in Neo4jTransactionManager.doGetTransaction didn't return a corresponding resource holder.
		// If it is null, there's no existing session / transaction.
		private @Nullable Neo4jResourceHolder resourceHolder;

		Neo4jTransactionObject(@Nullable Neo4jResourceHolder resourceHolder) {
			this.resourceHolder = resourceHolder;
		}

		/**
		 * Usually called in {@link #doBegin(Object, TransactionDefinition)} which is called when there's
		 * no existing transaction.
		 *
		 * @param resourceHolder A newly created resource holder with a fresh drivers session,
		 */
		void setResourceHolder(@Nullable Neo4jResourceHolder resourceHolder) {
			this.resourceHolder = resourceHolder;
		}

		/**
		 * @return {@literal true} if a {@link Neo4jResourceHolder} is set.
		 */
		boolean hasResourceHolder() {
			return resourceHolder != null;
		}

		Neo4jResourceHolder getRequiredResourceHolder() {

			Assert.state(hasResourceHolder(), RESOURCE_HOLDER_NOT_PRESENT_MESSAGE);
			return resourceHolder;
		}

		void setRollbackOnly() {

			getRequiredResourceHolder().setRollbackOnly();
		}

		@Override
		public boolean isRollbackOnly() {
			return this.hasResourceHolder() && this.resourceHolder.isRollbackOnly();
		}

		@Override
		public void flush() {

			TransactionSynchronizationUtils.triggerFlush();
		}
	}
}
