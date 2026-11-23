package com.sb24.asanzeman.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sb24.asanzeman.auth.application.BankLoginSettings;
import com.sb24.asanzeman.auth.application.OtpSettings;
import com.sb24.asanzeman.auth.application.TokenSettings;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.AccessTokenIssuerPort;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.support.TestKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** The wiring is excluded from the coverage gate, so this proves the beans come up from the properties. */
class AuthConfigurationTest {

    @Test
    void buildsTheJwtBeansFromOneLinePemPropertiesAndSignsATokenTheDecoderAccepts() {
        String privateKey = TestKeys.pem("PRIVATE KEY", TestKeys.pair().getPrivate().getEncoded()).replace("\n", "\\n");
        String publicKey = TestKeys.pem("PUBLIC KEY", TestKeys.pair().getPublic().getEncoded()).replace("\n", "\\n");

        new ApplicationContextRunner().withUserConfiguration(AuthConfiguration.class)
                .withBean(JwtBlacklistPort.class, () -> mock(JwtBlacklistPort.class))
                .withPropertyValues(
                        "azs.security.jwt.issuer=asanzeman",
                        "azs.security.jwt.private-key-pem=" + privateKey,
                        "azs.security.jwt.public-key-pem=" + publicKey,
                        "azs.security.jwt.access-token-ttl=15m",
                        "azs.security.jwt.refresh-token-ttl=8h",
                        "azs.security.jwt.customer-session-ttl=30m",
                        "azs.auth.bank.account-suffix=@sb24.ir")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(TokenSettings.class))
                            .isEqualTo(new TokenSettings(Duration.ofMinutes(15), Duration.ofHours(8)));
                    assertThat(context.getBean(BankLoginSettings.class).accountSuffix()).isEqualTo("@sb24.ir");
                    assertThat(context.getBean(OtpSettings.class)).isEqualTo(new OtpSettings(Duration.ofSeconds(120),
                            3, Duration.ofMinutes(15), Duration.ofMinutes(30))); // the defaults of azs.otp.*
                    String token = context.getBean(AccessTokenIssuerPort.class).issue(
                            new Session(UUID.randomUUID(), "ali", Role.BRANCH, "0123"), UUID.randomUUID(), "jti-1", Instant.now(),
                            Instant.now().plusSeconds(60));
                    assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("ali");
                    assertThat(context.getBean(JwtProperties.class).customerSessionTtl())
                            .isEqualTo(Duration.ofMinutes(30));
                });
    }

    @Test
    void aMissingSuffixSettingMeansNothingIsRemoved() {
        String privateKey = TestKeys.pem("PRIVATE KEY", TestKeys.pair().getPrivate().getEncoded());
        String publicKey = TestKeys.pem("PUBLIC KEY", TestKeys.pair().getPublic().getEncoded());

        new ApplicationContextRunner().withUserConfiguration(AuthConfiguration.class)
                .withBean(JwtBlacklistPort.class, () -> mock(JwtBlacklistPort.class))
                .withPropertyValues(
                        "azs.security.jwt.issuer=asanzeman",
                        "azs.security.jwt.access-token-ttl=15m",
                        "azs.security.jwt.refresh-token-ttl=8h")
                .withPropertyValues("azs.security.jwt.private-key-pem=" + privateKey.replace("\n", "\\n"),
                        "azs.security.jwt.public-key-pem=" + publicKey.replace("\n", "\\n"))
                .run(context -> assertThat(context.getBean(BankLoginSettings.class).accountSuffix()).isEmpty());
    }
}
