/*
 * Copyright © WebServices pour l'Éducation, 2016
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.service.impl;

import fr.wseduc.webutils.Either;
import org.entcore.common.neo4j.Neo4j;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class Neo4jZimbraService {

	private Neo4j neo;

	static final String TYPE_GROUP = "group";
	static final String TYPE_USER = "user";

	Neo4jZimbraService(){
		this.neo = Neo4j.getInstance();
	}


	void getIdsType(JsonArray idList, Handler<Either<String,JsonArray>> handler) {

		String query = "MATCH (v:Visible) "
				+ "where v.id in {ids} "
				+ "return v.id as id, "
				+ "coalesce(v.displayName, v.groupDisplayName) as displayName, "
				+ "v.name as groupName, "
				+ "case when 'User' in labels(v) then '" + TYPE_USER + "' "
				+ "when 'Group' in labels(v) then '" + TYPE_GROUP + "' end as type";

		neo.execute(query, new JsonObject().put("ids", idList), validResultHandler(handler));
	}

	void checkUserCommunication(String senderId, String recipientId, Handler<Either<String,JsonObject>> handler) {
		String query = "MATCH (s:User), (r:User) "
				+ "where s.id = {senderId} and r.id = {recipientId} "
				+ "return s.id as senderId, r.id as recipientId, "
				+ "exists((r)<-[:COMMUNIQUE*1..2]-()<-[:COMMUNIQUE]-(s)) OR "
				+ "exists((r)<-[:COMMUNIQUE_DIRECT]-(s)) as " + CommunicationService.CAN_COMMUNICATE;

		JsonObject params = new JsonObject()
				.put("senderId", senderId)
				.put("recipientId", recipientId);

		neo.execute(query, params, validUniqueResultHandler(handler));
	}

	void checkGroupCommunication(String senderId, String recipientId, Handler<Either<String,JsonObject>> handler) {
		String query = "MATCH (s:User), (r:Group) "
				+ "where s.id = {senderId} and r.id = {recipientId} "
				+ "return s.id as senderId, r.id as recipientId, "
				+ "exists((r)<-[:COMMUNIQUE*1..2]-()<-[:DEPENDS]-(s)) OR "
				+ "exists((r)<-[:COMMUNIQUE]-()<-[:COMMUNIQUE]-(s)) OR "
				+ "exists((r)<-[:COMMUNIQUE]-(s)) as " + CommunicationService.CAN_COMMUNICATE;

		JsonObject params = new JsonObject()
				.put("senderId", senderId)
				.put("recipientId", recipientId);

		neo.execute(query, params, validUniqueResultHandler(handler));
	}

}
