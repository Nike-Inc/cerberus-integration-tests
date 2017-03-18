package com.nike.cerberus.api.util

import com.nike.cerberus.api.GatewaySslSocketFactory
import io.restassured.config.SSLConfig
import org.apache.http.conn.ssl.SSLSocketFactory

import javax.net.ssl.SSLContext
import java.security.NoSuchAlgorithmException

import static io.restassured.RestAssured.*

class TestUtils {

    private TestUtils() {
        // no constructing
    }

    static String getRequiredEnvVar(String key, String msg) {
        String value = System.getenv(key)
        if (value == null || "" == value.trim()) {
            throw new IllegalStateException(String.format("The environment variable: %s is required for these tests, msg: %s", key, msg))
        }
        return value
    }

    static void configureRestAssured() throws NoSuchAlgorithmException {
        baseURI = getRequiredEnvVar("CERBERUS_API_URL", "The Cerberus API URL to Test")
        enableLoggingOfRequestAndResponseIfValidationFails()

        // Use our custom socket factory to enable SSL with SNI
        SSLSocketFactory customSslFactory = new GatewaySslSocketFactory(
                SSLContext.getDefault(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
        config = config().sslConfig(
                SSLConfig.sslConfig().sslSocketFactory(customSslFactory))
        config.getHttpClientConfig().reuseHttpClientInstance()
    }
}
