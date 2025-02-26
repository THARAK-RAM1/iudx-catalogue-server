package iudx.catalogue.server.database;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import org.apache.http.util.EntityUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.auth.AuthScope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;

import static iudx.catalogue.server.database.Constants.*;
import static iudx.catalogue.server.database.Constants.SOURCE_AND_ID_GEOQUERY;
import static iudx.catalogue.server.geocoding.util.Constants.*;
import static iudx.catalogue.server.geocoding.util.Constants.BBOX;
import static iudx.catalogue.server.util.Constants.*;
import static iudx.catalogue.server.util.Constants.RESULTS;
import static iudx.catalogue.server.util.Constants.TYPE;

public final class ElasticClient {

  private static final Logger LOGGER = LogManager.getLogger(ElasticClient.class);
  private final RestClient client;
  private String index;

  /**
   * ElasticClient - Wrapper around ElasticSearch low level client
   *
   * @param databaseIP IP of the DB
   * @param databasePort Port
   * @TODO XPack Security
   */
  public ElasticClient(String databaseIP, int databasePort, String index,
                        String databaseUser, String databasePassword) {
    CredentialsProvider credentials = new BasicCredentialsProvider();
    credentials.setCredentials(AuthScope.ANY,
                                new UsernamePasswordCredentials(databaseUser, databasePassword));
    client = RestClient.builder(new HttpHost(databaseIP, databasePort)).setHttpClientConfigCallback(
        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentials)).build();
    this.index = index;
  }


  /**
   * searchAsync - Wrapper around elasticsearch async search requests
   *
   * @param query Query
   * @param resultHandler JsonObject result {@link AsyncResult}
   * @TODO XPack Security
   */
  public ElasticClient searchAsync(String query, String index,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    Request queryRequest = new Request(REQUEST_GET, index + "/_search" + FILTER_PATH);
    queryRequest.setJsonEntity(query);
    LOGGER.debug(queryRequest);
    Future<JsonObject> future = searchAsync(queryRequest, SOURCE_ONLY);
    future.onComplete(resultHandler);
    return this;
  }

  public ElasticClient searchAsyncDataset(
      String query, String index, Handler<AsyncResult<JsonObject>> resultHandler) {
    Request queryRequest = new Request(REQUEST_GET, index + "/_search" + FILTER_PATH);
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = searchAsync(queryRequest, DATASET);
    future.onComplete(resultHandler);
    return this;
  }

  public ElasticClient searchAsyncGeoQuery(
      String query, String index, Handler<AsyncResult<JsonObject>> resultHandler) {
    Request queryRequest =
        new Request(REQUEST_GET, index + "/_search" + FILTER_PATH_ID_AND_SOURCE);
    queryRequest.setJsonEntity(query);
    LOGGER.debug(queryRequest);
    Future<JsonObject> future = searchAsync(queryRequest, SOURCE_AND_ID_GEOQUERY);
    future.onComplete(resultHandler);
    return this;
  }

  public ElasticClient searchAsyncGetId(
      String query, String index, Handler<AsyncResult<JsonObject>> resultHandler) {
    Request queryRequest = new Request(REQUEST_GET, index + "/_search" + FILTER_PATH_ID_AND_SOURCE);
    queryRequest.setJsonEntity(query);
    LOGGER.debug(queryRequest);
    Future<JsonObject> future = searchAsync(queryRequest, SOURCE_AND_ID);
    future.onComplete(resultHandler);
    return this;
  }

  public ElasticClient scriptSearch(JsonArray queryVector,
                                      Handler<AsyncResult<JsonObject>> resultHandler) {
      // String query = NLP_SEARCH.replace("$1", queryVector.toString());
     String query = "{\"query\": {\"script_score\": {\"query\": {\"match\": {}}," +
         " \"script\": {\"source\": \"doc['_word_vector'].size() == 0 ? 0 : cosineSimilarity(params.query_vector, '_word_vector') + 1.0\"," +
         "\"lang\": \"painless\",\"params\": {\"query_vector\":" + queryVector.toString() + "}}}}," +
         "\"_source\": {\"excludes\": [\"_word_vector\"]}}";

    Request queryRequest = new Request(REQUEST_GET, index + "/_search");
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = searchAsync(queryRequest, SOURCE_ONLY);
    future.onComplete(resultHandler);
    return this;
  }

  public Future<JsonObject> scriptLocationSearch(JsonArray queryVector, JsonObject queryParams) {
    Promise<JsonObject> promise = Promise.promise();
    JsonArray bboxCoords = queryParams.getJsonArray(BBOX);

    StringBuilder query = new StringBuilder("{\"query\": {\"script_score\": {\"query\": {\"bool\": {\"should\": [");
    if(queryParams.containsKey(BOROUGH)) {
      query.append("{\"match\": {\"_geosummary._geocoded.results.borough\": \"").append(queryParams.getString(BOROUGH)).append("\"}},");
    }
    if(queryParams.containsKey(LOCALITY)) {
      query.append("{\"match\": {\"_geosummary._geocoded.results.locality\": \"").append(queryParams.getString(LOCALITY)).append("\"}},");
    }
    if(queryParams.containsKey(COUNTY)) {
      query.append("{\"match\": {\"_geosummary._geocoded.results.county\": \"").append(queryParams.getString(COUNTY)).append("\"}},");
    }
    if(queryParams.containsKey(REGION)) {
      query.append("{\"match\": {\"_geosummary._geocoded.results.region\": \"").append(queryParams.getString(REGION)).append("\"}},");
    }
    if(queryParams.containsKey(COUNTRY)) {
      query.append("{\"match\": {\"_geosummary._geocoded.results.country\": \"").append(queryParams.getString(COUNTRY)).append("\"}}");
    } else {
      query.deleteCharAt(query.length() - 1);
    }
    query.append("],\"minimum_should_match\": 1, \"filter\": {\"geo_shape\": {\"location.geometry\": {\"shape\": {\"type\": \"envelope\",")
        .append("\"coordinates\": [ [ ").append(bboxCoords.getFloat(0)).append(",").append(bboxCoords.getFloat(3)).append("],")
        .append("[").append(bboxCoords.getFloat(2)).append(",").append(bboxCoords.getFloat(1)).append("] ]}, \"relation\": \"intersects\" }}}}},")
        .append("\"script\": {\"source\": \"doc['_word_vector'].size() == 0 ? 0 : cosineSimilarity(params.query_vector, '_word_vector') + 1.0\",")
        .append("\"params\": { \"query_vector\":").append(queryVector.toString()).append("}}}}, \"_source\": {\"excludes\": [\"_word_vector\"]}}");

    Request queryRequest = new Request(REQUEST_GET, index + "/_search");
    queryRequest.setJsonEntity(query.toString());
    Future<JsonObject> future = searchAsync(queryRequest, SOURCE_ONLY);

    future.onSuccess(h -> {
        promise.complete(future.result());
    }).onFailure(h -> {
        promise.fail(future.cause());
    });
    return promise.future();
  }


  /**
   * searchGetIdAsync - Get document IDs matching a query
   *
   * @param query Query
   * @param resultHandler JsonObject result {@link AsyncResult}
   * @TODO XPack Security
   */
  public ElasticClient searchGetId(String query, String index,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    Request queryRequest = new Request(REQUEST_GET, index + "/_search" + FILTER_ID_ONLY_PATH);
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = searchAsync(queryRequest, DOC_IDS_ONLY);
    future.onComplete(resultHandler);
    return this;
  }

  public ElasticClient ratingAggregationAsync(String query, String index,
      Handler<AsyncResult<JsonObject>> resultHandler) {
    Request queryRequest = new Request(REQUEST_GET, index
        + "/_search"
        + FILTER_PATH_AGGREGATION);
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = searchAsync(queryRequest, RATING_AGGREGATION_ONLY);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * aggregationsAsync - Wrapper around elasticsearch async search requests
   *
   * @param query Query
   * @param resultHandler JsonObject result {@link AsyncResult}
   * @TODO XPack Security
   */
  public ElasticClient listAggregationAsync(String query,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    Request queryRequest = new Request(REQUEST_GET, index
                              + "/_search"
                              + FILTER_PATH_AGGREGATION);
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = searchAsync(queryRequest, AGGREGATION_ONLY);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * countAsync - Wrapper around elasticsearch async count requests
   *
   * @param index Index to search on
   * @param query Query
   * @param resultHandler JsonObject result {@link AsyncResult}
   * @TODO XPack Security
   */
  public ElasticClient countAsync(String query, String index,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    Request queryRequest = new Request(REQUEST_GET, index + "/_count");
    queryRequest.setJsonEntity(query);
    Future<JsonObject> future = countAsync(queryRequest);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * docPostAsync - Wrapper around elasticsearch async doc post request
   *
   * @param index Index to search on
   * @param doc Document
   * @param resultHandler JsonObject
   * @TODO XPack Security
   */
  public ElasticClient docPostAsync(
      String index, String doc, Handler<AsyncResult<JsonObject>> resultHandler) {

    /** TODO: Validation */
    Request docRequest = new Request(REQUEST_POST, index + "/_doc");
    docRequest.setJsonEntity(doc.toString());

    Future<JsonObject> future = docAsync(REQUEST_POST, docRequest);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * docPutAsync - Wrapper around elasticsearch async doc put request
   *
   * @param index Index to search on
   * @param docId Document id (elastic id)
   * @param doc Document
   * @param resultHandler JsonObject
   * @TODO XPack Security
   */
  public ElasticClient docPutAsync(String docId, String index, String doc,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    /** TODO: Validation */
    Request docRequest = new Request(REQUEST_PUT, index + "/_doc/" + docId);
    docRequest.setJsonEntity(doc.toString());
    Future<JsonObject> future = docAsync(REQUEST_PUT, docRequest);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * docDelAsync - Wrapper around elasticsearch async doc delete request
   *
   * @param index Index to search on
   * @param docId Document
   * @param resultHandler JsonObject
   * @TODO XPack Security
   */
  public ElasticClient docDelAsync(String docId, String index,
      Handler<AsyncResult<JsonObject>> resultHandler) {

    /** TODO: Validation */
    Request docRequest = new Request(REQUEST_DELETE, index + "/_doc/" + docId);

    Future<JsonObject> future = docAsync(REQUEST_DELETE, docRequest);
    future.onComplete(resultHandler);
    return this;
  }

  /**
   * DBRespMsgBuilder} Message builder for search APIs
   */
  private class DBRespMsgBuilder {
    private JsonObject response = new JsonObject();
    private JsonArray results = new JsonArray();

    DBRespMsgBuilder() {
    }

    DBRespMsgBuilder statusSuccess() {
      response.put(TYPE, TYPE_SUCCESS);
      response.put(TITLE, TITLE_SUCCESS);
      return this;
    }

    DBRespMsgBuilder setTotalHits(int hits) {
      response.put(TOTAL_HITS, hits);
      return this;
    }

    /** Overloaded for source only request */
    DBRespMsgBuilder addResult(JsonObject obj) {
      response.put(RESULTS, results.add(obj));
      return this;
    }

    /** Overloaded for doc-ids request */
    DBRespMsgBuilder addResult(String value) {
      response.put(RESULTS, results.add(value));
      return this;
    }

    DBRespMsgBuilder addResult() {
      response.put(RESULTS, results);
      return this;
    }

    JsonObject getResponse() {
      return response;
    }
  }

  /**
   * searchAsync - private function which perform performRequestAsync for search apis
   *
   * @param request Elastic Request
   * @param options SOURCE - Source only
   *                DOCIDS - DOCIDs only
   *                IDS - IDs only
   * @TODO XPack Security
   */
  private Future<JsonObject> searchAsync(Request request, String options) {
    Promise<JsonObject> promise = Promise.promise();

    DBRespMsgBuilder responseMsg = new DBRespMsgBuilder();

    client.performRequestAsync(request, new ResponseListener() {
      @Override
      public void onSuccess(Response response) {

        try {
          int statusCode = response.getStatusLine().getStatusCode();
          if (statusCode != 200 && statusCode != 204) {
            promise.fail(DATABASE_BAD_QUERY);
            return;
          }
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          int totalHits = responseJson.getJsonObject(HITS)
                                                .getJsonObject(TOTAL)
                                                .getInteger(VALUE);
          responseMsg.statusSuccess()
                      .setTotalHits(totalHits);
          if (totalHits > 0 ) {
            JsonArray results = new JsonArray();

                if ((options == SOURCE_ONLY
                    || options == DOC_IDS_ONLY
                    || options == SOURCE_AND_ID
                    || options == SOURCE_AND_ID_GEOQUERY
                    || options == DATASET) && responseJson.getJsonObject(HITS).containsKey(HITS)){

                    results = responseJson.getJsonObject(HITS).getJsonArray(HITS);
                }

                if (options == AGGREGATION_ONLY || options == RATING_AGGREGATION_ONLY) {
                  results = responseJson.getJsonObject(AGGREGATIONS)
                                  .getJsonObject(RESULTS)
                                  .getJsonArray(BUCKETS);
            }

            for (int i = 0; i < results.size(); i++) {
              if (options == SOURCE_ONLY) {
                /** Todo: This might slow system down */
                JsonObject source = results.getJsonObject(i).getJsonObject(SOURCE);
                source.remove(SUMMARY_KEY);
                source.remove(WORD_VECTOR_KEY);
                responseMsg.addResult(source);
              }
              if (options == DOC_IDS_ONLY) {
                responseMsg.addResult(results.getJsonObject(i).getString(DOC_ID));
              }
              if (options == AGGREGATION_ONLY) {
                responseMsg.addResult(results.getJsonObject(i).getString(KEY));
              }
              if (options == RATING_AGGREGATION_ONLY) {
                JsonObject result = new JsonObject()
                    .put(ID, results.getJsonObject(i).getString(KEY))
                    .put(TOTAL_RATINGS, results.getJsonObject(i).getString(DOC_COUNT))
                    .put(AVERAGE_RATING, results.getJsonObject(i).getJsonObject(AVERAGE_RATING).getDouble(VALUE));
                responseMsg.addResult(result);
              }
                  if (options == SOURCE_AND_ID) {

                    JsonObject source = results.getJsonObject(i).getJsonObject(SOURCE);
                    String docId = results.getJsonObject(i).getString(DOC_ID);
                    JsonObject result = new JsonObject().put(SOURCE, source).put(DOC_ID, docId);
                    responseMsg.addResult(result);
                  }
                  if (options == SOURCE_AND_ID_GEOQUERY) {
                    String docId = results.getJsonObject(i).getString(DOC_ID);
                    JsonObject source = results.getJsonObject(i).getJsonObject(SOURCE);
                    source.put("doc_id",docId);
                    JsonObject result = new JsonObject();
                    result.mergeIn(source);
                    responseMsg.addResult(result);
                  }
                }
                if (options == DATASET) {
                  JsonArray resource = new JsonArray();
                  JsonObject datasetDetail = new JsonObject();
                  JsonObject dataset = new JsonObject();
                  for (int i = 0; i < results.size(); i++) {
                    JsonObject record = results.getJsonObject(i).getJsonObject(SOURCE);
                    JsonObject provider = new JsonObject();
                    String type = record.getJsonArray(TYPE).getString(0);
                    if (type.equals("iudx:Provider")) {
                      provider
                          .put(ID, record.getString(ID))
                          .put(DESCRIPTION_ATTR, record.getString(DESCRIPTION_ATTR));
                      dataset.put(PROVIDER, provider);
                    }
                    if (type.equals("iudx:Resource")) {
                      JsonObject resourceJson = new JsonObject();
                      resourceJson
                          .put(RESOURCE_ID, record.getString(ID))
                          .put(LABEL, record.getString(LABEL))
                          .put(DESCRIPTION_ATTR, record.getString(DESCRIPTION_ATTR))
                          .put(DATA_SAMPLE, record.getJsonObject(DATA_SAMPLE))
                          .put(DATA_DESCRIPTOR, record.getJsonObject(DATA_DESCRIPTOR))
                          .put(RESOURCETYPE, record.getString(RESOURCETYPE));
                      resource.add(resourceJson);
                    }
                    if (type.equals("iudx:ResourceGroup")) {
                      String schema =
                          record.getString("@context")
                              + record
                                  .getJsonArray(TYPE)
                                  .getString(1)
                                  .substring(5, record.getJsonArray(TYPE).getString(1).length());
                      dataset
                          .put(ID, record.getString(ID))
                          .put(LABEL, record.getString(LABEL))
                          .put(DESCRIPTION_ATTR, record.getString(DESCRIPTION_ATTR))
                          .put(ACCESS_POLICY, record.getString(ACCESS_POLICY))
                          .put(INSTANCE, record.getString(INSTANCE))
                          .put(DATA_SAMPLE, record.getJsonObject(DATA_SAMPLE))
                          .put("dataSampleFile", record.getJsonArray("dataSampleFile"))
                          .put("dataQualityFile", record.getJsonArray("dataQualityFile"))
                          .put(DATA_DESCRIPTOR, record.getJsonObject(DATA_DESCRIPTOR))
                          .put("schema", schema);
                    }
                  }
                  datasetDetail.put("dataset", dataset);
                  datasetDetail.put("resource", resource);
                  responseMsg.addResult(datasetDetail);
                }
              } else {
                responseMsg.addResult();
              }
              promise.complete(responseMsg.getResponse());

        } catch (IOException e) {
          promise.fail(e);
        } finally {
        }
      }
      @Override
      public void onFailure(Exception e) {
        promise.fail(e);
      }
    });
    return promise.future();
  }

  /**
   * countAsync - private function which perform performRequestAsync for count apis
   *
   * @param request Elastic Request
   * @TODO XPack Security
   * @TODO Can combine countAsync and searchAsync
   */
  private Future<JsonObject> countAsync(Request request) {
    Promise<JsonObject> promise = Promise.promise();

    DBRespMsgBuilder responseMsg = new DBRespMsgBuilder();

    client.performRequestAsync(request, new ResponseListener() {
      @Override
      public void onSuccess(Response response) {

        try {
          int statusCode = response.getStatusLine().getStatusCode();
          if (statusCode != 200 && statusCode != 204) {
            promise.fail(DATABASE_BAD_QUERY);
            return;
          }
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          responseMsg.statusSuccess()
                      .setTotalHits(responseJson.getInteger(COUNT));
          promise.complete(responseMsg.getResponse());

        } catch (IOException e) {
            promise.fail(e);
        } finally {
        }
      }
      @Override
      public void onFailure(Exception e) {
        promise.fail(e);
      }
    });
    return promise.future();
  }


  /**
   * docAsync - private function which perform performRequestAsync for doc apis
   *
   * @param request Elastic Request
   * @param options SOURCE - Source only
   *                DOCIDS - DOCIDs only
   *                IDS - IDs only
   * @TODO XPack Security
   * @TODO Can combine countAsync and searchAsync
   */
  private Future<JsonObject> docAsync(String method, Request request) {
    Promise<JsonObject> promise = Promise.promise();

    client.performRequestAsync(request, new ResponseListener() {
      @Override
      public void onSuccess(Response response) {
        try {
          JsonObject responseJson = new JsonObject(EntityUtils.toString(response.getEntity()));
          int statusCode = response.getStatusLine().getStatusCode();
          switch (method) {
            case REQUEST_POST:
              if (statusCode == 201) {
                promise.complete(responseJson);
                return;
              }
            case REQUEST_DELETE:
              if (statusCode == 200) {
                promise.complete(responseJson);
                return;
              }
            case REQUEST_PUT:
              if (statusCode == 200) {
                promise.complete(responseJson);
                return;
              }
            default:
              promise.fail(DATABASE_BAD_QUERY);
          }
          promise.fail("Failed request");
        } catch (IOException e) {
            promise.fail(e);
        } finally {
        }
      }
      @Override
      public void onFailure(Exception e) {
        promise.fail(e);
      }
    });
    return promise.future();
  }


}
