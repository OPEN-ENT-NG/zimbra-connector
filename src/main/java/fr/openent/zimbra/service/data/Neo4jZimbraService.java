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

package fr.openent.zimbra.service.data;


import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.service.impl.CommunicationService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import org.entcore.common.neo4j.Neo4j;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class Neo4jZimbraService {

	private Neo4j neo;

	public static final String TYPE_GROUP = "group";
	public static final String TYPE_USER = "user";

	public static final String GROUP_NAME = "groupName";
	public static final String GROUP_DISPLAYNAME = "groupDisplayName";
	public static final String GROUP_ID = "groupId";

	public Neo4jZimbraService(){
		this.neo = Neo4j.getInstance();
	}


	public void getIdsType(JsonArray idList, Handler<Either<String,JsonArray>> handler) {

		String query = "MATCH (v:Visible) "
				+ "where v.id in {ids} "
				+ "return v.id as id, "
				+ "coalesce(v.displayName, v.groupDisplayName) as displayName, "
				+ "v.name as groupName, "
				+ "case when 'User' in labels(v) then '" + TYPE_USER + "' "
				+ "when 'Group' in labels(v) then '" + TYPE_GROUP + "' end as type";

		neo.execute(query, new JsonObject().put("ids", idList), validResultHandler(handler));
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
				+ "exists((r)<-[:COMMUNIQUE*1..2]-()<-[:DEPENDS]-(s)) OR "
				+ "exists((r)<-[:COMMUNIQUE]-()<-[:COMMUNIQUE]-(s)) OR "
				+ "exists((r)<-[:COMMUNIQUE]-(s)) as " + CommunicationService.CAN_COMMUNICATE;

		JsonObject params = new JsonObject()
				.put("senderId", senderId)
				.put("recipientId", recipientId);

		neo.execute(query, params, validUniqueResultHandler(handler));
	}

	public void getLoginFromIds(JsonArray idList, Handler<Either<String,JsonObject>> handler) {
		String query = "MATCH (u:User) "
				+ "WHERE u.id in {ids} "
				+ "return u.login as login";

		neo.execute(query, new JsonObject().put("ids", idList), validUniqueResultHandler(handler));
	}

	/**
	 * Get every info from Neo4j that needs to be synchronized with Zimbra
	 * @param id User Id
	 * @param handler result handler
	 */
	public void getUserFromNeo4j(String id, Handler<AsyncResult<JsonObject>> handler) {

		JsonArray fields = new JsonArray().add("externalId").add("lastName").add("firstName").add("login");
		fields.add("email").add("emailAcademy").add("mobile").add("deleteDate").add("functions").add("displayName");


		StringBuilder query = new StringBuilder();

		query.append("MATCH (s:Structure)<-[:DEPENDS]-(pg:ProfileGroup)")
				.append("-[:HAS_PROFILE]->(p:Profile)")
				.append(", pg<-[:IN]-(u:User) ");

		String  filter =  "WHERE u.id = {id} ";
		JsonObject params = new JsonObject().put("id", id);

		query.append(filter);
		query.append("OPTIONAL MATCH (g:Group)<-[:IN]-u ");

		query.append("RETURN DISTINCT ");
		for (Object field : fields) {
			query.append(" u.").append(field);
			query.append(" as ").append(field).append(",");
		}
		query.deleteCharAt(query.length() - 1);
		query.append(", p.name as profiles");
		query.append(", collect(distinct s.UAI) as structures")
				.append(" , CASE WHEN size(u.classes) > 0  THEN  last(collect(u.classes)) END as classes")
				.append(" , collect(distinct {groupName:g.name, groupId:g.id}) as groups");
		neo.execute(query.toString(), params, validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
	}

	public void getGroupFromNeo4j(String id, Handler<AsyncResult<JsonObject>> handler) {
		String query = "MATCH (g:Group) "
				+ "WHERE g.id = {id} "
				+ "RETURN g.id as " + GROUP_ID + ", "
				+ "g.groupDisplayName as " + GROUP_DISPLAYNAME + ", "
				+ "g.name as " + GROUP_NAME;
		JsonObject params = new JsonObject().put("id", id);

		neo.execute(query, params, validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
	}

}
