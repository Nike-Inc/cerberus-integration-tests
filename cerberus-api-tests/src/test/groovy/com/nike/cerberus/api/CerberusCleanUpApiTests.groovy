package com.nike.cerberus.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.nike.cerberus.api.util.TestUtils
import com.fieldju.commons.EnvUtils
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import java.security.NoSuchAlgorithmException

import static com.nike.cerberus.api.CerberusApiActions.deleteAuthToken
import static com.nike.cerberus.api.CerberusApiActions.retrieveIamAuthToken
import static com.nike.cerberus.api.CerberusCompositeApiActions.*

class CerberusCleanUpApiTests {

    private static final Integer DEFAULT_KMS_EXPIRATION_PERIOD_IN_DAYS = 30

    private String iamPrincipalArn
    private String region
    private String cerberusAuthToken
    private def cerberusAuthData

    private ObjectMapper mapper

    @BeforeTest
    void beforeTest() throws NoSuchAlgorithmException {
        mapper = new ObjectMapper()
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        cerberusAuthData = retrieveIamAuthToken(iamPrincipalArn, region, false)
        cerberusAuthToken = cerberusAuthData."client_token"
    }

    @AfterTest
    void afterTest() {
        deleteAuthToken(cerberusAuthToken)
    }

    private void loadRequiredEnvVars() {
        iamPrincipalArn = EnvUtils.getRequiredEnv("TEST_IAM_PRINCIPAL_ARN",
                "The account id to use when authenticating with Cerberus using the IAM Auth endpoint")

        region = EnvUtils.getRequiredEnv("TEST_REGION",
                "The region to use when authenticating with Cerberus using the IAM Auth endpoint")
    }

    @Test
    void "test that an authenticated admin IAM role can clean up KMS keys"() {
        "clean up kms keys and iam roles is successful"(cerberusAuthData, DEFAULT_KMS_EXPIRATION_PERIOD_IN_DAYS)
    }
}
