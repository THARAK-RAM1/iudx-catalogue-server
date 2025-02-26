package iudx.catalogue.server.mockauthenticator;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.ext.web.client.WebClient;
import java.util.Arrays;

import iudx.catalogue.server.authenticator.AuthenticationService;
import static iudx.catalogue.server.authenticator.Constants.*;
import static iudx.catalogue.server.util.Constants.*;

/**
 * Mock Auth Service. Bypass main auth service.
 */

public class MockAuthenticationServiceImpl implements AuthenticationService {

    private static final Logger LOGGER = LogManager.getLogger(MockAuthenticationServiceImpl.class);
    private String authHost;

    public MockAuthenticationServiceImpl(WebClient client, String authHost) {
        this.authHost = authHost;
    }

    static void validateAuthInfo(JsonObject authInfo) throws IllegalArgumentException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthenticationService tokenInterospect(JsonObject request, JsonObject authenticationInfo,
                                                  Handler<AsyncResult<JsonObject>> handler) {

      LOGGER.debug("In mock auth");
      JsonObject result = new JsonObject();
      result.put(STATUS, SUCCESS);
      result.put(BODY, new JsonObject());
      handler.handle(Future.succeededFuture(result));
      return this;
    }

    public boolean isPermittedProviderID(String requestID, String providerID) {
        String tipProvider = String.join("/", Arrays.asList(requestID.split("/", 3)).subList(0, 2));
        return providerID.equals(tipProvider);
    }

    public boolean isPermittedMethod(JsonArray methods, String operation) {
        return false;
    }

}
