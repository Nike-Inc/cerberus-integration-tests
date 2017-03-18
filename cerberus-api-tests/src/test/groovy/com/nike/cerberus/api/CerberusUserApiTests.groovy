package com.nike.cerberus.api

import com.nike.cerberus.api.util.TestUtils
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import static com.nike.cerberus.api.CerberusCompositeApiActions.*
import static com.nike.cerberus.api.CerberusApiActions.*

class CerberusUserApiTests {

    private String username
    private String password
    private String otpDeviceId
    private String otpSecret
    private String cerberusAuthToken

    @BeforeTest
    void beforeTest() {
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        cerberusAuthToken = "login user with multi factor authentication (or skip mfa if not required) and return auth token"(username, password, otpSecret, otpDeviceId)
    }

    @AfterTest
    void afterTest() {
        logoutUser(cerberusAuthToken)
    }

    @Test
    void "test that an authenticated user can create, read, update then delete  a safe deposit box"() {
        "create, read, update then delete a safe deposit box"(cerberusAuthToken)
    }

    @Test
    void "test that an authenticated user can create, create, update then delete a secret node in a safe deposit box"() {
        'create, read, update then delete a secret node'(cerberusAuthToken)
    }

    private void loadRequiredEnvVars() {
        username = TestUtils.getRequiredEnvVar("TEST_USER_EMAIL",
                "The email address for a test user for testing user based endpoints")

        password = TestUtils.getRequiredEnvVar("TEST_USER_PASSWORD",
                "The password for a test user for testing user based endpoints")

        // todo this make this optional
        otpSecret = TestUtils.getRequiredEnvVar("TEST_USER_OTP_SECRET",
                "The secret for the test users OTP MFA (OTP == Google auth)")

        otpDeviceId = TestUtils.getRequiredEnvVar("TEST_USER_OTP_DEVICE_ID",
                "The device id for the test users OTP MFA (OTP == Google auth)")
    }
}
