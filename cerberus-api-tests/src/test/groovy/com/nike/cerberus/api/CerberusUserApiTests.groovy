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
    private Map cerberusAuthData

    @BeforeTest
    void beforeTest() {
        TestUtils.configureRestAssured()
        loadRequiredEnvVars()
        cerberusAuthData = auth()
        cerberusAuthToken = cerberusAuthData.'client_token'
    }

    def auth(retryCount = 0) {
        try {
            "login user with multi factor authentication (or skip mfa if not required) and return auth data"(username, password, otpSecret, otpDeviceId)
            System.out.println("login for " + username + " successful on try " + retryCount)
        } catch (Throwable t) {
            System.err.println("login for " + username + " failed on try " + retryCount)
            if (retryCount < 3) {
                sleep(10000)
                return auth(retryCount + 1)
            } else {throw t}
        }
    }

    @AfterTest
    void afterTest() {
        logoutUser(cerberusAuthToken)
    }

    @Test
    void "test that an authenticated user can create, read, update then delete a safe deposit box"() {
        "v1 create, read, list, update and then delete a safe deposit box"(cerberusAuthData)
        "v2 create, read, list, update and then delete a safe deposit box"(cerberusAuthData)
    }

    @Test
    void "test that an authenticated user can create, update then delete a secret node in a safe deposit box"() {
        "create, read, update then delete a secret node"(cerberusAuthToken)
    }

    private void loadRequiredEnvVars() {
        username = TestUtils.getRequiredEnvVar("TEST_USER_EMAIL",
                "The email address for a test user for testing user based endpoints")

        password = TestUtils.getRequiredEnvVar("TEST_USER_PASSWORD",
                "The password for a test user for testing user based endpoints")

        // todo: make this optional
        otpSecret = TestUtils.getRequiredEnvVar("TEST_USER_OTP_SECRET",
                "The secret for the test users OTP MFA (OTP == Google auth)")

        otpDeviceId = TestUtils.getRequiredEnvVar("TEST_USER_OTP_DEVICE_ID",
                "The device id for the test users OTP MFA (OTP == Google auth)")
    }
}
