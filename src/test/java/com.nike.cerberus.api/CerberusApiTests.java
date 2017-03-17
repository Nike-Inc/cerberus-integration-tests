/*
 * Copyright (c) 2017 Nike Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.api;

import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.DecryptResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.*;
import static io.restassured.module.jsv.JsonSchemaValidator.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class CerberusApiTests {

    private String accountId;
    private String roleName;
    private String region;
    private String iamToken;

    private String username;
    private String password;

    private AWSKMSClient kmsClient;
    private ObjectMapper mapper;

    @Before
    public void before() throws NoSuchAlgorithmException {
        configureRestAssured();
        loadRequiredEnvVars();
    }

    private void loadRequiredEnvVars() {
        accountId = TestEnvVarUtils.getRequiredEnvVar("TEST_ACCOUNT_ID",
                "The account id to use when authenticating with Cerberus using the IAM Auth endpoint");

        roleName = TestEnvVarUtils.getRequiredEnvVar("TEST_ROLE_NAME",
                "The role name to use when authenticating with Cerberus using the IAM Auth endpoint");

        region = TestEnvVarUtils.getRequiredEnvVar("TEST_REGION",
                "The region to use when authenticating with Cerberus using the IAM Auth endpoint");

        kmsClient = new AWSKMSClient(new STSProfileCredentialsServiceProvider(
                new RoleInfo().withRoleArn(String.format("arn:aws:iam::%s:role/%s", accountId, roleName))
                        .withRoleSessionName(UUID.randomUUID().toString()))).withRegion(Regions.fromName(region));

        mapper = new ObjectMapper();

//        username =  getRequiredEnvVar("TEST_USER_EMAIL",
//                "The email address for a test user for testing user based endpoints");
//
//        password =  getRequiredEnvVar("TEST_USER_PASSWORD",
//                "The password for a test user for testing user based endpoints");
    }

    private void configureRestAssured() throws NoSuchAlgorithmException {
        RestAssured.baseURI = TestEnvVarUtils.getRequiredEnvVar("CERBERUS_API_URL", "The Cerberus API URL to Test");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        // Use our custom socket factory to enable SSL with SNI
        SSLSocketFactory customSslFactory = new GatewaySslSocketFactory(
                SSLContext.getDefault(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        RestAssured.config = RestAssured.config().sslConfig(
                SSLConfig.sslConfig().sslSocketFactory(customSslFactory));
        RestAssured.config.getHttpClientConfig().reuseHttpClientInstance();
    }

    @Test public void
    test_that_an_iam_role_can_authenticate_with_cerberus_v1_auth_endpoint() {
        String token = validateAndRetrieveIamAuthToken();
        assertNotNull(token);
        assertFalse(token.trim().equals(""));
    }

    @Test public void // todo change this to be test that an authenticated IAM role can CRUD a secret node
    test_that_an_iam_role_can_read_the_healthcheck_sdb() {
        String expectedValue = "I am healthy";
        String authToken = validateAndRetrieveIamAuthToken();

        given()
                .header("X-Vault-Token", authToken).
        when()
                .get("/v1/secret/app/health-check-bucket/healthcheck").
        then()
                .statusCode(200)
                .contentType("application/json")
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/secret/get-secret.json"))
                .body("data.value", equalTo(expectedValue));
    }

    /**
     * Since many tests will need an auth token but we also need to test getting an auth token we will have a private method
     * that will fetch an auth token as well as validate it once and caching token for future fetches.
     */
    private String validateAndRetrieveIamAuthToken() {
        if (iamToken != null) {
            return iamToken;
        }

        // verify the actual auth call
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("account_id", accountId);
        requestBody.put("role_name", roleName);
        requestBody.put("region", region);

        Response response =
        given()
                .contentType("application/json")
                .body(requestBody).
        when()
                .post("/v1/auth/iam-role").
        then()
                .statusCode(200)
                .contentType("application/json")
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/auth/iam-role-encrypted.json")).
        extract().
                response();

        String base64EncodedKmsEncryptedAuthPayload = response.body().jsonPath().getString("auth_data");
        DecryptResult result = kmsClient.decrypt(
                new DecryptRequest()
                        .withCiphertextBlob(
                                ByteBuffer.wrap(Base64.getDecoder().decode(base64EncodedKmsEncryptedAuthPayload)))
        );

        String jsonString = new String(result.getPlaintext().array());
        assertThat(jsonString, matchesJsonSchemaInClasspath("json-schema/v1/auth/iam-role-decrypted.json"));
        JsonNode node;
        try {
            node = mapper.readTree(jsonString);
        } catch (IOException e) {
            throw new RuntimeException("Some how failed to deserialize auth data even though we asserted that we matched the json schema");
        }
        iamToken = node.get("client_token").asText();
        return iamToken;
    }
}
