package com.nike.cerberus.api

import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.AWSKMSClient
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.kms.model.DecryptResult
import groovy.json.JsonSlurper
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import io.restassured.response.ResponseBody

import java.nio.ByteBuffer

import static io.restassured.RestAssured.*
import static io.restassured.module.jsv.JsonSchemaValidator.*
import static org.junit.Assert.assertThat

class CerberusApiActions {

    /**
     * Executes a delete on the v1 auth endpoint to trigger a logout / destroy token action
     *
     * @param cerberusAuthToken The token to destroy
     */
    static void deleteAuthToken(String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .delete("/v1/auth")
        .then()
                .statusCode(204)
    }

    /**
     * Authenticates with Cerberus's IAM auth endpoint get token
     *
     * @param accountId The account id to do iam auth with
     * @param roleName The role name to do iam auth with
     * @param region The region to do iam auth with
     * @return The authentication token
     */
    static String retrieveIamAuthToken(String accountId, String roleName, String region) {
        // get the encrypted payload and validate response
        Response response =
        given()
                .contentType("application/json")
                .body([
                    'account_id': accountId,
                    'role_name': roleName,
                    'region': region
                ])
        .when()
                .post("/v1/auth/iam-role")
        .then()
                .statusCode(200)
                .contentType("application/json")
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/auth/iam-role-encrypted.json"))
        .extract().
                response()

        // decrypt the payload
        String base64EncodedKmsEncryptedAuthPayload = response.body().jsonPath().getString("auth_data")
        AWSKMSClient kmsClient = new AWSKMSClient(new STSProfileCredentialsServiceProvider(
                new RoleInfo().withRoleArn(String.format("arn:aws:iam::%s:role/%s", accountId, roleName))
                        .withRoleSessionName(UUID.randomUUID().toString()))).withRegion(Regions.fromName(region))

        DecryptResult result = kmsClient.decrypt(
                new DecryptRequest()
                        .withCiphertextBlob(
                        ByteBuffer.wrap(Base64.getDecoder().decode(base64EncodedKmsEncryptedAuthPayload)))
        )

        // validate decrypted schema and return auth token
        String jsonString = new String(result.getPlaintext().array())
        assertThat(jsonString, matchesJsonSchemaInClasspath("json-schema/v1/auth/iam-role-decrypted.json"))
        return new JsonSlurper().parseText(jsonString)."client_token"
    }

    static void createOrUpdateSecretNode(Map data, String path, String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
                .body(data)
        .when()
                .post("/v1/secret/${path}")
        .then()
                .statusCode(204)
    }

    static JsonPath readSecretNode(String path, String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get("/v1/secret/${path}")
        .then()
                .statusCode(200)
                .contentType("application/json")
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/secret/get-secret.json"))
        .extract()
                .body().jsonPath()
    }

    static void deleteSecretNode(String path, String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .delete("/v1/secret/${path}")
        .then()
                .statusCode(204)
    }

    static void assertThatSecretNodeDoesNotExist(String path, String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get("/v1/secret/${path}")
        .then()
                .statusCode(404)
    }

    static ResponseBody loginUser(String username, String password) {
        def body =
        given()
                .header("Authorization", "Basic ${"$username:$password".bytes.encodeBase64()}")
        .when()
                .get("/v2/auth/user")
        .then()
                .statusCode(200)
                .contentType("application/json")
        .extract()
                .body()

        String status = body.jsonPath().getString("status")
        if (status == "success") {
            assertThat(body.asString(), matchesJsonSchemaInClasspath("json-schema/v2/auth/user-success.json"))
        } else if (status == "mfa_req") {
            assertThat(body.asString(), matchesJsonSchemaInClasspath("json-schema/v2/auth/user-mfa_req.json"))
        } else {
            throw new IllegalStateException("unreconized status from login user: ${status}")
        }
        return body
    }

    static void logoutUser(String cerberusAuthToken) {
        deleteAuthToken(cerberusAuthToken)
    }

    static String finishMfaUserAuth(String stateToken, String deviceId, String otpToken) {
        given()
                .contentType("application/json")
                .body([
                    'state_token': stateToken,
                    'device_id': deviceId,
                    'otp_token': otpToken
                ])
        .when()
                .post("/v2/auth/mfa_check")
        .then()
                .statusCode(200)
                .contentType("application/json")
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v2/auth/mfa_check.json"))
        .extract().
                body().jsonPath().getString("data.client_token.client_token")
    }
}
