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
package org.springframework.data.neo4j.core.cypher;

import org.apiguardian.api.API;
import org.springframework.data.neo4j.core.cypher.support.Visitor;
import org.springframework.util.Assert;

/**
 * A binary operation.
 *
 * @author Michael J. Simons
 * @since 1.0
 */
@API(status = API.Status.INTERNAL, since = "1.0")
public final class Operation implements Expression {

	static Operation create(Expression op1, Operator operator, Expression op2) {

		Assert.notNull(op1, "The first operand must not be null.");
		Assert.notNull(operator, "Operator must not be empty.");
		Assert.notNull(op2, "The second operand must not be null.");

		return new Operation(op1, operator, op2);
	}

	private final Expression left;
	private final Operator operator;
	private final Expression right;

	private Operation(Expression left, Operator operator, Expression right) {

		this.left = left;
		this.operator = operator;
		this.right = right;
	}

	@Override
	public void accept(Visitor visitor) {

		visitor.enter(this);
		left.accept(visitor);
		operator.accept(visitor);
		right.accept(visitor);
		visitor.leave(this);
	}
}