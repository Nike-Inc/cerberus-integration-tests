package com.nike.cerberus.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.nike.cerberus.api.util.TestUtils
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import java.security.NoSuchAlgorithmException

import static com.nike.cerberus.api.CerberusCompositeApiActions.*
import static com.nike.cerberus.api.CerberusApiActions.*

class CerberusIamApiTests {

    private String accountId
    private String roleName
    private String region
    private String cerberusAuthToken
    private def cerberusAuthData

    private ObjectMapper mapper

    @BeforeTest
    void beforeTest() throws NoSuchAlgorithmException {
        mapper = new ObjectMapper()
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        cerberusAuthData = retrieveIamAuthToken(accountId, roleName, region)
        cerberusAuthToken = cerberusAuthData."client_token"
    }

    @AfterTest
    void afterTest() {
        deleteAuthToken(cerberusAuthToken)
    }

    private void loadRequiredEnvVars() {
        accountId = TestUtils.getRequiredEnvVar("TEST_ACCOUNT_ID",
                "The account id to use when authenticating with Cerberus using the IAM Auth endpoint")

        roleName = TestUtils.getRequiredEnvVar("TEST_ROLE_NAME",
                "The role name to use when authenticating with Cerberus using the IAM Auth endpoint")

        region = TestUtils.getRequiredEnvVar("TEST_REGION",
                "The region to use when authenticating with Cerberus using the IAM Auth endpoint")
    }

    @Test
    void "test that an authenticated IAM role can create, read, update then delete a secret node"() {
        'create, read, update then delete a secret node'(cerberusAuthToken)
    }

    @Test
    void "test that an authenticated IAM role can create, read, update then delete a safe deposit box"() {
        "create, read, list, update and then delete a safe deposit box"(cerberusAuthData)
    }
}
