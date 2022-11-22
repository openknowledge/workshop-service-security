/*
 * Copyright 2019 open knowledge GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.openknowledge.sample.address.domain;

import static javax.ws.rs.client.Entity.entity;

import java.io.StringReader;
import java.util.Map;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.validation.ValidationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import de.openknowledge.sample.jwt.infrastructure.JwtClientFilter;

@ApplicationScoped
public class AddressValidationService {

    private static final Logger LOG = Logger.getLogger(AddressValidationService.class.getSimpleName());
    private static final String ADDRESS_VALIDATION_PATH = "valid-addresses";
    private static final String AUTHENTICATION_PATH = "auth/realms/master/protocol/openid-connect/token";

    @Inject
    @ConfigProperty(name = "address-validation-service.url")
    String addressValidationServiceUrl;

    @Inject
    @ConfigProperty(name = "authentication-service.url")
    String authenticationServiceUrl;

    @Inject
    @ConfigProperty(name = "address-validation.clientid")
    String addressValidationClientId;


    @Inject
    @ConfigProperty(name = "address-validation.secret")
    String addressValidationSecret;

    @Inject
    JsonWebToken token;

    public void validate(Address address) {
        Response validationResult = ClientBuilder
            .newClient()
            .register(JsonbJaxrsProvider.class)
            .register(new JwtClientFilter(getAddressValidationToken()))
            .target(addressValidationServiceUrl)
            .path(ADDRESS_VALIDATION_PATH)
            .request(MediaType.APPLICATION_JSON)
            .post(entity(address, MediaType.APPLICATION_JSON_TYPE));
        if (validationResult.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
            || validationResult.getStatus() == Response.Status.FORBIDDEN.getStatusCode()) {

            LOG.info("authentication failed");
            throw new SecurityException(Integer.toString(validationResult.getStatus()));
        } else if (validationResult.getStatus() != Response.Status.OK.getStatusCode()) {
            LOG.info("validation failed");
            JsonObject problem = Json.createReader(new StringReader(validationResult.readEntity(String.class))).readObject();
            throw new ValidationException(problem.getString("detail"));
        }
    }

    private String getAddressValidationToken() {
        Response tokenResponse = ClientBuilder.newClient()
            .register(JsonbJaxrsProvider.class)
            .target(authenticationServiceUrl)
            .path(AUTHENTICATION_PATH)
            .request(MediaType.APPLICATION_FORM_URLENCODED)
            .post(Entity
            .form(new Form("client_id", addressValidationClientId)
            .param("client_secret", addressValidationSecret)
            .param("grant_type", "client_credentials")));
        return (String)tokenResponse.readEntity(Map.class).get("access_token");
    }
}
