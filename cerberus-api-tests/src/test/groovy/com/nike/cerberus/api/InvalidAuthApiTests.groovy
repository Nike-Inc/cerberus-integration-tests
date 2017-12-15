package com.nike.cerberus.api

import com.nike.cerberus.api.util.TestUtils
import org.apache.http.HttpStatus
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import static com.nike.cerberus.api.CerberusApiActions.IAM_PRINCIPAL_AUTH_PATH
import static com.nike.cerberus.api.CerberusApiActions.IAM_ROLE_AUTH_PATH
import static com.nike.cerberus.api.CerberusApiActions.USER_AUTH_PATH
import static com.nike.cerberus.api.CerberusApiActions.USER_CREDENTIALS_HEADER_NAME
import static com.nike.cerberus.api.CerberusApiActions.validateGETApiResponse
import static com.nike.cerberus.api.CerberusApiActions.validatePOSTApiResponse
import static com.nike.cerberus.api.CerberusCompositeApiActions.*

class InvalidAuthApiTests {

    @BeforeTest
    void beforeTest() {
        TestUtils.configureRestAssured()
    }

    @Test
    void "test that a secret cannot be created, read, updated or deleted with an invalid token"() {
        "create, read, update, list secrets with invalid auth token"()
    }

    @Test
    void "v1 test that a safe deposit box cannot be created, read, updated or deleted with an invalid token"() {
        "v1 create, read, update, list safe-deposit-boxes with invalid auth token"()
    }

    @Test
    void "v2 test that a safe deposit box cannot be created, read, updated or deleted with an invalid token"() {
        "v2 create, read, update, list safe-deposit-boxes with invalid auth token"()
    }

    @Test
    void "an IAM role cannot auth if it does not have permission to any safe deposit box"() {
        def schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/iam-role-auth-no-permission-to-any-sdb.json"
        def requestBody = [account_id: "0000000000", role_name: "non-existent-role-name", region: "us-west-2"]

        validatePOSTApiResponse("token not needed", IAM_ROLE_AUTH_PATH, HttpStatus.SC_BAD_REQUEST, schemaFilePath, requestBody)
    }

    @Test
    void "an IAM principal cannot auth if it does not have permission to any safe deposit box"() {
        def schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/iam-principal-auth-no-permission-to-any-sdb.json"
        def requestBody = [
                iam_principal_arn: "arn:aws:iam::1111111111:role/imaginary-role-name-should-not-exist",
                role_name        : "non-existent-role-name",
                region           : "us-west-2"
        ]

        validatePOSTApiResponse("token not needed", IAM_PRINCIPAL_AUTH_PATH, HttpStatus.SC_BAD_REQUEST, schemaFilePath, requestBody)
    }

    @Test
    void "a user cannot authenticate with invalid credentials"() {
        String email = "invalid.user@example.com"
        String password = "tooEZtoGuess"
        def schemaFilePath = "$NEGATIVE_JSON_SCHEMA_ROOT_PATH/user-auth-invalid-credentials.json"
        def credentialsHeaderValue = "Basic ${"$email:$password".bytes.encodeBase64()}"

        validateGETApiResponse(USER_CREDENTIALS_HEADER_NAME, credentialsHeaderValue, USER_AUTH_PATH, HttpStatus.SC_UNAUTHORIZED, schemaFilePath)
    }
}
