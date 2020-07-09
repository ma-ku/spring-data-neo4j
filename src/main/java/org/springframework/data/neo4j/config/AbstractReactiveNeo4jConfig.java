/*
 * Copyright 2011-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.config;

import org.apiguardian.api.API;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.ReactiveDatabaseSelectionProvider;
import org.springframework.data.neo4j.core.ReactiveNeo4jClient;
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.core.transaction.ReactiveNeo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.ReactiveNeo4jRepositoryConfigurationExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

/**
 * Base class for reactive SDN configuration using JavaConfig. This can be included in all scenarios in which Spring
 * Boot is not an option.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 * @since 1.0
 */
@Configuration
@API(status = API.Status.STABLE, since = "1.0")
@Import(Neo4jDefaultReactiveCallbacksRegistrar.class)
public abstract class AbstractReactiveNeo4jConfig extends Neo4jConfigurationSupport {

	/**
	 * The driver to be used for interacting with Neo4j.
	 *
	 * @return the Neo4j Java driver instance to work with.
	 */
	public abstract Driver driver();

	/**
	 * The driver used here should be the driver resulting from {@link #driver()}, which is the default.
	 *
	 * @param driver The driver to connect with.
	 * @return A reactive Neo4j client.
	 */
	@Bean(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_NEO4J_CLIENT_BEAN_NAME)
	public ReactiveNeo4jClient neo4jClient(Driver driver) {
		return ReactiveNeo4jClient.create(driver);
	}

	@Bean(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_NEO4J_TEMPLATE_BEAN_NAME)
	public ReactiveNeo4jTemplate neo4jTemplate(final ReactiveNeo4jClient neo4jClient,
			final Neo4jMappingContext mappingContext, final ReactiveDatabaseSelectionProvider databaseNameProvider) {

		return new ReactiveNeo4jTemplate(neo4jClient, mappingContext, databaseNameProvider);
	}

	/**
	 * Provides a {@link PlatformTransactionManager} for Neo4j based on the driver resulting from {@link #driver()}.
	 *
	 * @param driver The driver to synchronize against
	 * @return A platform transaction manager
	 */
	@Bean(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
	public ReactiveTransactionManager reactiveTransactionManager(Driver driver,
			ReactiveDatabaseSelectionProvider databaseNameProvider) {

		return new ReactiveNeo4jTransactionManager(driver, databaseNameProvider);
	}

	/**
	 * Configures the database name provider.
	 *
	 * @return The default database name provider, defaulting to the default database on Neo4j 4.0 and on no default on
	 *         Neo4j 3.5 and prior.
	 */
	@Bean
	protected ReactiveDatabaseSelectionProvider reactiveNeo4jDatabaseNameProvider() {

		return ReactiveDatabaseSelectionProvider.getDefaultSelectionProvider();
	}
}
