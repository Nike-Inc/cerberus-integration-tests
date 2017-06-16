package com.nike.cerberus.api.util

import com.fieldju.commons.PropUtils
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

    static void configureRestAssured() throws NoSuchAlgorithmException {
        baseURI = PropUtils.getRequiredProperty("CERBERUS_API_URL", "The Cerberus API URL to Test")
        enableLoggingOfRequestAndResponseIfValidationFails()

        // Use our custom socket factory to enable SSL with SNI
        SSLSocketFactory customSslFactory = new GatewaySslSocketFactory(
                SSLContext.getDefault(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
        config = config().sslConfig(
                SSLConfig.sslConfig().allowAllHostnames().sslSocketFactory(customSslFactory))
        config.getHttpClientConfig().reuseHttpClientInstance()
    }
}
