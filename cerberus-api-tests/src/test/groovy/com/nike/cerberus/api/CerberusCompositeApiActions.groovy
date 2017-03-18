package com.nike.cerberus.api

import io.restassured.response.ResponseBody
import org.jboss.aerogear.security.otp.Totp

import static org.junit.Assert.assertEquals
import static com.nike.cerberus.api.CerberusApiActions.*

class CerberusCompositeApiActions {
    private CerberusCompositeApiActions() {}

    static final String ROOT_INTEGRATION_TEST_SDB_PATH = "app/cerberus-integration-tests-sdb"

    static void "create, read, update then delete a secret node"(String cerberusAuthToken) {
        def path = "${ROOT_INTEGRATION_TEST_SDB_PATH}/${UUID.randomUUID().toString()}"
        String value1 = 'value1'
        String value2 = 'value2'

        // Create the initial secret node
        createOrUpdateSecretNode([value: value1], path, cerberusAuthToken)
        // Read and verify that it was created
        def resp = readSecretNode(path, cerberusAuthToken)
        assertEquals(value1, resp?.'data'?.'value')
        // Update the secret node
        createOrUpdateSecretNode([value: value2], path, cerberusAuthToken)
        // Read that the node was updated
        def resp2 = readSecretNode(path, cerberusAuthToken)
        assertEquals(value2, resp2?.'data'?.'value')
        // Delete the node
        deleteSecretNode(path, cerberusAuthToken)
        // Verify that the node was deleted
        assertThatSecretNodeDoesNotExist(path, cerberusAuthToken)
    }

    static void "create, read, update then delete a safe deposit box"(String cerberusAuthToken) {
        // todo
    }

    static String "login user with multi factor authentication (or skip mfa if not required) and return auth token"(
            String username, String password, String otpSecret, String deviceId) {

        ResponseBody loginResp = loginUser(username, password)
        String status = loginResp.jsonPath().getString("status")
        if (status == "success") {
            return loginResp.jsonPath().getString("data.client_token")
        } else {
            return finishMfaUserAuth(
                    loginResp.jsonPath().getString("data.state_token"),
                    deviceId,
                    new Totp(otpSecret).now())
        }
    }
}
