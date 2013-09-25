/**
 * Copyright 2011 the original author or authors.
 *
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
 */

package org.springframework.data.neo4j.aspects.support.typerepresentation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.ClosableIterable;
import org.neo4j.helpers.collection.IteratorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.annotation.NodeEntity;
import org.springframework.data.neo4j.aspects.support.EntityTestBase;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.support.mapping.StoredEntityType;
import org.springframework.data.neo4j.support.typerepresentation.LabelingNodeTypeRepresentationStrategy;
import org.springframework.test.context.CleanContextCacheTestExecutionListener;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:org/springframework/data/neo4j/aspects/support/Neo4jGraphPersistenceTests-context.xml",
        "classpath:org/springframework/data/neo4j/aspects/support/LabelingTypeRepresentationStrategyOverride-context.xml"})
@TestExecutionListeners({CleanContextCacheTestExecutionListener.class, DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class})
public class LabelingNodeTypeRepresentationStrategyTests extends EntityTestBase {

	@Autowired
	private LabelingNodeTypeRepresentationStrategy nodeTypeRepresentationStrategy;

    @Autowired
    Neo4jTemplate neo4jTemplate;
    @Autowired
    Neo4jMappingContext ctx;

	private Thing thing;
	private SubThing subThing;
    private StoredEntityType thingType;
    private StoredEntityType subThingType;

    @BeforeTransaction
	public void cleanDb() {
		super.cleanDb();
	}

	@Before
	public void setUp() throws Exception {
		if (thing == null) {
			createThingsAndLinks();
		}
        thingType = typeOf(Thing.class);
        subThingType = typeOf(SubThing.class);
    }

	@Test
	@Transactional
	public void testPostEntityCreation() throws Exception {
        // Anything to test here??
	}

	@Test
	public void testPreEntityRemoval() throws Exception {
        // preEntityRemoval is a no op method, so nothing to test here!
	}

	@Test
	@Transactional
	public void testFindAll() throws Exception {

        ClosableIterable<Node> allThings = nodeTypeRepresentationStrategy.findAll(thingType);
		assertEquals("Did not find all things.",
                new HashSet<PropertyContainer>(Arrays.asList(neo4jTemplate.getPersistentState(subThing), neo4jTemplate.getPersistentState(thing))),
                IteratorUtil.addToCollection(allThings, new HashSet<Node>()));
	}

	@Test
	@Transactional
	public void testCountOfSuperTypeIncludesSubTypes() throws Exception {
        final int EXPECTED_NUM_THINGS = 1;
        final int EXPECTED_NUM_SUBTHINGS = 1;
        final int TOTAL_EXPECTED = EXPECTED_NUM_THINGS + EXPECTED_NUM_SUBTHINGS;
		assertEquals(TOTAL_EXPECTED, nodeTypeRepresentationStrategy.count(thingType));
	}

    @Test
    @Transactional
    public void testCountOfSubTypeExcludesConcreteParents() throws Exception {
        final int EXPECTED_NUM_THINGS = 1;
        final int EXPECTED_NUM_SUBTHINGS = 1;
        final int TOTAL_EXPECTED =  EXPECTED_NUM_SUBTHINGS;
        assertEquals(TOTAL_EXPECTED, nodeTypeRepresentationStrategy.count(subThingType));
    }

	@Test
	@Transactional
	public void testGetJavaType() throws Exception {
		assertEquals(thingType.getAlias(), nodeTypeRepresentationStrategy.readAliasFrom(node(thing)));
		assertEquals(subThingType.getAlias(), nodeTypeRepresentationStrategy.readAliasFrom(node(subThing)));
		assertEquals(Thing.class, neo4jTemplate.getStoredJavaType(node(thing)));
		assertEquals(SubThing.class, neo4jTemplate.getStoredJavaType(node(subThing)));
	}

	@Test
	@Transactional
	public void testCreateEntityAndInferType() throws Exception {
        Thing newThing = neo4jTemplate.createEntityFromStoredType(node(thing), neo4jTemplate.getMappingPolicy(thing));
        assertEquals(thing, newThing);
    }

	@Test
	@Transactional
	public void testCreateEntityAndSpecifyType() throws Exception {
        Thing newThing = neo4jTemplate.createEntityFromState(node(subThing), Thing.class, neo4jTemplate.getMappingPolicy(subThing));
        assertEquals(subThing, newThing);
    }

    @Test
    @Transactional
	public void testProjectEntity() throws Exception {
        Unrelated other = neo4jTemplate.projectTo(node(thing), Unrelated.class);
        assertEquals("thing", other.getName());
	}

	private Node node(Thing thing) {
        return getNodeState(thing);
	}

	private Thing createThingsAndLinks() {
		Transaction tx = graphDatabaseService.beginTx();
		try {
            Node n1 = graphDatabaseService.createNode();
            thing = neo4jTemplate.setPersistentState(new Thing(),n1);
			nodeTypeRepresentationStrategy.writeTypeTo(n1, neo4jTemplate.getEntityType(Thing.class));
            thing.setName("thing");
            Node n2 = graphDatabaseService.createNode();
            subThing = neo4jTemplate.setPersistentState(new SubThing(),n2);
			nodeTypeRepresentationStrategy.writeTypeTo(n2, neo4jTemplate.getEntityType(SubThing.class));
            subThing.setName("subThing");
			tx.success();
			return thing;
		} finally {
			tx.finish();
		}
	}

    @NodeEntity
    public static class Unrelated {
        String name;

        public String getName() {
            return name;
        }
    }

	@NodeEntity
	public static class Thing {
		String name;

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

	public static class SubThing extends Thing {
    }
}
