package io.soo.springboot

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestConstructor

@Tag("context")
@SpringBootTest(
    properties = [
        "app.security.jwt.keystore.location=classpath:keys/jwt-keystore.p12",
        "app.security.jwt.keystore.password=admin123",
        "app.security.jwt.keystore.alias=jwt",
        "app.security.jwt.keystore.key-password=admin123",
    ],
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
abstract class ContextTest
