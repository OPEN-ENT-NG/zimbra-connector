/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.apizimbra.service.data;


import fr.openent.apizimbra.helper.AsyncHelper;
import fr.openent.apizimbra.model.constant.ModuleConstants;
import fr.openent.apizimbra.service.CommunicationService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class Neo4jZimbraService {

	private Neo4j neo;


	public Neo4jZimbraService(){
		this.neo = Neo4j.getInstance();
	}

	public void hasExternalCommunicationRole(String userId, Handler<AsyncResult<JsonObject>> handler) {
		String query = "MATCH (u:User), (r:Role) "
				+ "WHERE u.id = {userId} "
				+ "AND r.name = {roleName} "
				+ "RETURN exists((r)<-[:AUTHORIZED]-(:Group)-[:IN]-(u)) as " + CommunicationService.HAS_EXTERNAL_ROLE;
		JsonObject params = new JsonObject()
				.put("userId", userId)
				.put("roleName", ModuleConstants.ROLE_NAME);
		neo.execute(query, params, validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
	}

	public void checkUserCommunication(String senderId, String recipientId, Handler<Either<String,JsonObject>> handler) {
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

	public void checkGroupCommunication(String senderId, String recipientId, Handler<Either<String,JsonObject>> handler) {
		String query = "MATCH (s:User), (r:Group) "
				+ "where s.id = {senderId} and r.id = {recipientId} "
				+ "return s.id as senderId, r.id as recipientId, "
				+ "exists((s)-[:COMMUNIQUE*1..2]->()<-[:DEPENDS]-(r)) OR "
				+ "exists((r)<-[:COMMUNIQUE]-()<-[:COMMUNIQUE]-(s)) OR "
				+ "(exists((r)<-[:COMMUNIQUE]-(s)) AND r.users <> 'INCOMING') as " + CommunicationService.CAN_COMMUNICATE;

		JsonObject params = new JsonObject()
				.put("senderId", senderId)
				.put("recipientId", recipientId);

		neo.execute(query, params, validUniqueResultHandler(handler));
	}

}
