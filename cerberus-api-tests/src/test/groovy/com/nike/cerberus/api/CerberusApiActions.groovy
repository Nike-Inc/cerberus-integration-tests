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
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class CerberusApiActions {

    public static String V1_SAFE_DEPOSIT_BOX_PATH = "/v1/safe-deposit-box"
    public static String V2_SAFE_DEPOSIT_BOX_PATH = "/v2/safe-deposit-box"

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
    static def retrieveIamAuthToken(String accountId, String roleName, String region) {
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
        return new JsonSlurper().parseText(jsonString)
    }

    static def retrieveIamAuthToken(String roleArn, String region) {
        // get the encrypted payload and validate response
        Response response =
                given()
                        .contentType("application/json")
                        .body([
                        'iam_principal_arn': roleArn,
                        'region': region
                ])
                        .when()
                        .post("/v2/auth/iam-principal")
                        .then()
                        .statusCode(200)
                        .contentType("application/json")
                        .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v2/auth/iam-role-encrypted.json"))
                        .extract().
                        response()

        // decrypt the payload
        String base64EncodedKmsEncryptedAuthPayload = response.body().jsonPath().getString("auth_data")
        AWSKMSClient kmsClient = new AWSKMSClient(new STSProfileCredentialsServiceProvider(
                new RoleInfo().withRoleArn(roleArn)
                        .withRoleSessionName(UUID.randomUUID().toString()))).withRegion(Regions.fromName(region))

        DecryptResult result = kmsClient.decrypt(
                new DecryptRequest()
                        .withCiphertextBlob(
                        ByteBuffer.wrap(Base64.getDecoder().decode(base64EncodedKmsEncryptedAuthPayload)))
        )

        // validate decrypted schema and return auth token
        String jsonString = new String(result.getPlaintext().array())
        assertThat(jsonString, matchesJsonSchemaInClasspath("json-schema/v2/auth/iam-role-decrypted.json"))
//        return new JsonSlurper().parseText(jsonString)."client_token"
        return new JsonSlurper().parseText(jsonString)

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

    static JsonPath loginUser(String username, String password) {
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
        return body.jsonPath()
    }

    static void logoutUser(String cerberusAuthToken) {
        deleteAuthToken(cerberusAuthToken)
    }

    static JsonPath finishMfaUserAuth(String stateToken, String deviceId, String otpToken) {
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
                body().jsonPath()
    }

    static String createSdbV1(String cerberusAuthToken,
                              String name,
                              String description,
                              String categoryId,
                              String owner,
                              List<Map<String, String>> userGroupPermissions,
                              List<Map<String, String>> iamRolePermissions) {

        given()
                .header("X-Vault-Token", cerberusAuthToken)
                .contentType("application/json")
                .body([
                    name: name,
                    description: description,
                    'category_id': categoryId,
                    owner: owner,
                    'user_group_permissions': userGroupPermissions,
                    'iam_role_permissions': iamRolePermissions
                ])
        .when()
                .post(V1_SAFE_DEPOSIT_BOX_PATH)
        .then()
                .statusCode(201)
                .header('X-Refresh-Token', 'true')
                .header('Location', not(isEmptyOrNullString()))
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/$V1_SAFE_DEPOSIT_BOX_PATH/create_success.json"))
        .extract().
                body().jsonPath().getString("id")
    }

    static JsonPath createSdbV2(String cerberusAuthToken,
                              String name,
                              String description,
                              String categoryId,
                              String owner,
                              List<Map<String, String>> userGroupPermissions,
                              List<Map<String, String>> iamPrincipalPermissions) {

        given()
            .header("X-Vault-Token", cerberusAuthToken)
            .contentType("application/json")
            .body([
                name: name,
                description: description,
                'category_id': categoryId,
                owner: owner,
                'user_group_permissions': userGroupPermissions,
                'iam_principal_permissions': iamPrincipalPermissions
            ])
            .when()
                .post(V2_SAFE_DEPOSIT_BOX_PATH)
            .then()
                .statusCode(201)
                .header('X-Refresh-Token', 'true')
                .header('Location', not(isEmptyOrNullString()))
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/$V2_SAFE_DEPOSIT_BOX_PATH/create_success.json"))
            .extract().
                body().jsonPath()
    }

    static JsonPath readSdb(String cerberusAuthToken, String sdbId, String baseSdbApiPath = V1_SAFE_DEPOSIT_BOX_PATH) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get("${baseSdbApiPath}/${sdbId}")
        .then()
                .statusCode(200)
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/$baseSdbApiPath/read_success.json"))
        .extract().
                body().jsonPath()
    }

    static void updateSdbV1(String cerberusAuthToken,
                            String sdbId,
                            String description,
                            String owner,
                            List<Map<String, String>> userGroupPermissions,
                            List<Map<String, String>> iamRolePermissions) {

        given()
            .header("X-Vault-Token", cerberusAuthToken)
            .contentType("application/json")
            .body([
                description: description,
                owner: owner,
                'user_group_permissions': userGroupPermissions,
                'iam_role_permissions': iamRolePermissions
            ])
        .when()
            .put("$V1_SAFE_DEPOSIT_BOX_PATH/${sdbId}")
        .then()
            .statusCode(204)
            .header('X-Refresh-Token', 'true')

    }

    static JsonPath updateSdbV2(String cerberusAuthToken,
                            String sdbId,
                            String description,
                            String owner,
                            List<Map<String, String>> userGroupPermissions,
                            List<Map<String, String>> iamPrincipalPermissions) {

        given()
            .header("X-Vault-Token", cerberusAuthToken)
            .contentType("application/json")
            .body([
                description: description,
                owner: owner,
                'user_group_permissions': userGroupPermissions,
                'iam_principal_permissions': iamPrincipalPermissions
            ])
        .when()
            .put("$V2_SAFE_DEPOSIT_BOX_PATH/${sdbId}")
        .then()
            .statusCode(200)
            .header('X-Refresh-Token', 'true')
            .assertThat().body(matchesJsonSchemaInClasspath("json-schema/$V2_SAFE_DEPOSIT_BOX_PATH/read_success.json"))
        .extract().
            body().jsonPath()
    }

    static void deleteSdb(String cerberusAuthToken, String sdbId, String baseSdbApiPath = V1_SAFE_DEPOSIT_BOX_PATH) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
            .when()
                .delete("${baseSdbApiPath}/${sdbId}")
            .then()
                .statusCode(200)
                .header('X-Refresh-Token', 'true')
    }

    static JsonPath getRoles(String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get("/v1/role")
        .then()
                .statusCode(200)
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/role/get_roles_success.json"))
        .extract().
                body().jsonPath()
    }

    static JsonPath getCategories(String cerberusAuthToken) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get("/v1/category")
        .then()
                .statusCode(200)
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/category/get_categories_success.json"))
        .extract().
                body().jsonPath()
    }

    static JsonPath listSdbs(String cerberusAuthToken, String baseSdbApiPath = V1_SAFE_DEPOSIT_BOX_PATH) {
        given()
                .header("X-Vault-Token", cerberusAuthToken)
        .when()
                .get(baseSdbApiPath)
        .then()
                .statusCode(200)
                .assertThat().body(matchesJsonSchemaInClasspath("json-schema/v1/safe-deposit-box/list_success.json"))
        .extract().
                body().jsonPath()
    }
}
