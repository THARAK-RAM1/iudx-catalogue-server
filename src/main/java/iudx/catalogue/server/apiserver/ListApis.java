/**
 * <h1>SearchApis.java</h1>
 * Callback handlers for List APIS
 */

package iudx.catalogue.server.apiserver;

import iudx.catalogue.server.util.Api;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import static iudx.catalogue.server.apiserver.util.Constants.*;
import static iudx.catalogue.server.util.Constants.*;
import iudx.catalogue.server.apiserver.util.QueryMapper;
import iudx.catalogue.server.apiserver.util.RespBuilder;
import iudx.catalogue.server.database.DatabaseService;


public final class ListApis {


  private DatabaseService dbService;

  private static final Logger LOGGER = LogManager.getLogger(ListApis.class);

  /**
   * Crud  constructor
   *
   * @param DBService DataBase Service class
   * @return void
   * @TODO Throw error if load failed
   */

  public void setDbService(DatabaseService dbService) {
    this.dbService = dbService;
  }

  /**
   * Get the list of items for a catalogue instance.
   *
   * @param routingContext handles web requests in Vert.x Web
   */
  public void listItemsHandler(RoutingContext routingContext) {

    LOGGER.debug("Info: Listing items");

    /* Handles HTTP request from client */
    HttpServerRequest request = routingContext.request();
    MultiMap queryParameters = routingContext.queryParams();

    /* Handles HTTP response from server to client */
    HttpServerResponse response = routingContext.response();

    response.putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON);

    /* HTTP request instance/host details */
    String instanceID = request.getHeader(HEADER_INSTANCE);

    String itemType = request.getParam(ITEM_TYPE);
    JsonObject requestBody = QueryMapper.map2Json(queryParameters);
    if (requestBody != null) {

      requestBody.put(ITEM_TYPE, itemType);
      /* Populating query mapper */
      requestBody.put(HEADER_INSTANCE, instanceID);

      JsonObject resp = QueryMapper.validateQueryParam(requestBody);
      if (resp.getString(STATUS).equals(SUCCESS)) {

        String type = null;

        switch (itemType) {
          case INSTANCE:
            type = ITEM_TYPE_INSTANCE;
            break;
          case RESOURCE_GRP:
            type = ITEM_TYPE_RESOURCE_GROUP;
            break;
          case RESOURCE_SVR:
            type = ITEM_TYPE_RESOURCE_SERVER;
            break;
          case PROVIDER:
            type = ITEM_TYPE_PROVIDER;
            break;
          case TAGS:
            type = itemType;
            break;
          default:
            LOGGER.error("Fail: Invalid itemType:" + itemType);
            response.setStatusCode(400)
                    .end(new RespBuilder()
                              .withType(TYPE_INVALID_SYNTAX)
                              .withTitle(TITLE_INVALID_SYNTAX)
                              .withDetail(DETAIL_WRONG_ITEM_TYPE)
                              .getResponse());
        return;
        }
        requestBody.put(TYPE, type);

        /* Request database service with requestBody for listing items */
        dbService.listItems(requestBody, dbhandler -> {
          if (dbhandler.succeeded()) {
            LOGGER.info("Success: Item listing");
            response.setStatusCode(200).end(dbhandler.result().toString());
          } else if (dbhandler.failed()) {
            LOGGER.error(
                "Fail: Issue in listing " + itemType + ": " + dbhandler.cause().getMessage());
            response.setStatusCode(400)
                    .end(new RespBuilder()
                              .withType(TYPE_INVALID_SYNTAX)
                              .withTitle(TITLE_INVALID_SYNTAX)
                              .withDetail(DETAIL_WRONG_ITEM_TYPE)
                              .getResponse());
          }
        });
      } else {
        LOGGER.error("Fail: Search/Count; Invalid request query parameters");
        response.setStatusCode(400)
          .end(new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_WRONG_ITEM_TYPE)
              .getResponse());
      }
    } else {
      LOGGER.error("Fail: Search/Count; Invalid request query parameters");
        response.setStatusCode(400)
          .end(new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_WRONG_ITEM_TYPE)
              .getResponse());
    }
  }
}
