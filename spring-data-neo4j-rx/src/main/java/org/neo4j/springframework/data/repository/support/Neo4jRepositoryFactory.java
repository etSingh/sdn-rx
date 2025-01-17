/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.springframework.data.repository.support;

import static org.neo4j.springframework.data.repository.query.CypherAdapterUtils.*;
import static org.neo4j.springframework.data.repository.support.Neo4jRepositoryFactorySupport.*;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.neo4j.driver.Record;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.springframework.data.core.Neo4jClient;
import org.neo4j.springframework.data.core.mapping.Neo4jMappingContext;
import org.neo4j.springframework.data.core.mapping.Neo4jPersistentEntity;
import org.neo4j.springframework.data.repository.Neo4jRepository;
import org.neo4j.springframework.data.repository.query.CypherAdapterUtils;
import org.neo4j.springframework.data.repository.query.Neo4jQueryLookupStrategy;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryFragment;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;

/**
 * Factory to create {@link Neo4jRepository} instances.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 * @since 1.0
 */
final class Neo4jRepositoryFactory extends RepositoryFactorySupport {

	private final Neo4jClient neo4jClient;

	private final Neo4jMappingContext mappingContext;

	private final SchemaBasedStatementBuilder schemaBasedStatementBuilder;

	private final Neo4jEvents eventSupport;

	Neo4jRepositoryFactory(Neo4jClient neo4jClient, Neo4jMappingContext mappingContext, Neo4jEvents eventSupport) {
		this.neo4jClient = neo4jClient;
		this.mappingContext = mappingContext;
		this.eventSupport = eventSupport;

		this.schemaBasedStatementBuilder = CypherAdapterUtils.createSchemaBasedStatementBuilder(this.mappingContext);
	}

	@Override
	public <T, ID> Neo4jEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {

		Neo4jPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(domainClass);
		BiFunction<TypeSystem, Record, T> mappingFunction = mappingContext.getRequiredMappingFunctionFor(domainClass);
		Function<T, Map<String, Object>> binderFunction = mappingContext.getRequiredBinderFunctionFor(domainClass);

		return new DefaultNeo4jEntityInformation<>((Neo4jPersistentEntity<T>) entity, mappingFunction, binderFunction);
	}

	@Override
	protected Object getTargetRepository(RepositoryInformation metadata) {

		Neo4jEntityInformation<?, Object> entityInformation = getEntityInformation(metadata.getDomainType());
		assertIdentifierType(metadata.getIdType(), entityInformation.getIdType());
		return getTargetRepositoryViaReflection(metadata,
			neo4jClient, entityInformation, schemaBasedStatementBuilder, eventSupport, mappingContext);
	}

	@Override
	protected RepositoryFragments getRepositoryFragments(RepositoryMetadata metadata) {

		RepositoryFragments fragments = RepositoryFragments.empty();

		Object byExampleExecutor = getTargetRepositoryViaReflection(
			SimpleQueryByExampleExecutor.class,
			neo4jClient, mappingContext, schemaBasedStatementBuilder);

		fragments = fragments.append(RepositoryFragment.implemented(byExampleExecutor));

		return fragments;
	}

	@Override
	protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
		return SimpleNeo4jRepository.class;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.core.support.RepositoryFactorySupport#getQueryLookupStrategy(org.springframework.data.repository.query.QueryLookupStrategy.Key, org.springframework.data.repository.query.EvaluationContextProvider)
	 */
	@Override
	protected Optional<QueryLookupStrategy> getQueryLookupStrategy(Key key,
			QueryMethodEvaluationContextProvider evaluationContextProvider) {

		return Optional.of(new Neo4jQueryLookupStrategy(neo4jClient, mappingContext, evaluationContextProvider));
	}
}
