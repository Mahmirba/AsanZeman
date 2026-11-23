package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.InsufficientBalanceException;
import com.sb24.asanzeman.request.domain.model.InvalidCollateralException;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.request.domain.model.RequestNotSubmittableException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.in.ChooseCollateralCommand;
import com.sb24.asanzeman.request.domain.port.in.CustomerCollateralUseCase;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerCollateralControllerTest {

    private static final String CUSTOMER = "1000000011";
    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final String BASE = "/api/customer/requests/" + REQUEST;

    private CustomerCollateralUseCase collateral;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        collateral = mock(CustomerCollateralUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new CustomerCollateralController(collateral, messages))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(CUSTOMER).claim("role", "CUSTOMER")
                .claim("sid", SESSION.toString()).issuedAt(now).expiresAt(now.plusSeconds(1800)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theDepositsShowTheirPriceClassBalanceAndWhatTheyMayBeUsedFor() throws Exception {
        when(collateral.deposits(CUSTOMER, SESSION, REQUEST)).thenReturn(List.of(
                new CollateralDeposit("D-1", PriceClass.CHEAP, Rials.of(5_000_000_000L)),
                new CollateralDeposit("D-3", PriceClass.EXPENSIVE, Rials.of(3_000_000_000L))));

        mvc.perform(get(BASE + "/deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("D-1"))
                .andExpect(jsonPath("$[0].priceClass").value("CHEAP"))
                .andExpect(jsonPath("$[0].availableBalance").value(5_000_000_000L))
                .andExpect(jsonPath("$[0].allowedFor[0]").value("CASH"))
                .andExpect(jsonPath("$[0].allowedFor[1]").value("PLEDGE"))
                .andExpect(jsonPath("$[1].allowedFor.length()").value(1))
                .andExpect(jsonPath("$[1].allowedFor[0]").value("PLEDGE"));
    }

    @Test
    void choosingReturnsTheCalculatedCollateral() throws Exception {
        var plan = new CollateralPlan(List.of(
                new CollateralAllocation("D-1", CollateralKind.CASH, PriceClass.CHEAP, 20, BigDecimal.ONE,
                        Rials.of(200_000_000L), Rials.of(200_000_000L)),
                new CollateralAllocation("D-3", CollateralKind.PLEDGE, PriceClass.EXPENSIVE, 80,
                        new BigDecimal("1.05"), Rials.of(800_000_000L), Rials.of(840_000_000L))),
                Rials.of(200_000_000L), Rials.of(800_000_000L), Rials.of(1_040_000_000L));
        when(collateral.choose(CUSTOMER, SESSION, REQUEST,
                new ChooseCollateralCommand(20, List.of("D-1"), List.of("D-3")))).thenReturn(plan);

        mvc.perform(post(BASE + "/collateral").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashPercent\":20,\"cashDeposits\":[\"D-1\"],\"pledgeDeposits\":[\"D-3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(200_000_000L))
                .andExpect(jsonPath("$.pledgeBase").value(800_000_000L))
                .andExpect(jsonPath("$.totalCollateral").value(1_040_000_000L))
                .andExpect(jsonPath("$.allocations[1].kind").value("PLEDGE"))
                .andExpect(jsonPath("$.allocations[1].factor").value(1.05))
                .andExpect(jsonPath("$.allocations[1].collateral").value(840_000_000L));
    }

    @Test
    void aChoiceWithoutACashShareIs400() throws Exception {
        mvc.perform(post(BASE + "/collateral").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashDeposits\":[\"D-1\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.cashPercent").exists());
    }

    @Test
    void anInvalidChoiceAndAnInsufficientBalanceAre400WithThePersianTexts() throws Exception {
        when(collateral.choose(any(), any(), any(), any())).thenThrow(new InvalidCollateralException("x"),
                new InsufficientBalanceException());

        mvc.perform(post(BASE + "/collateral").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashPercent\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.collateral-invalid"));
        mvc.perform(post(BASE + "/collateral").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cashPercent\":100,\"cashDeposits\":[\"D-1\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.collateral-insufficient"))
                .andExpect(jsonPath("$.detail").value("مشتری گرامی! به دلیل کافی‌نبودن موجودی در سپرده‌های انتخابی "
                        + "برای وثیقه، ثبت درخواست امکان‌پذیر نیست. لطفاً سپرده‌هایی با موجودی کافی برای ادامه "
                        + "فرایند صدور ضمانت‌نامه انتخاب کنید."));
    }

    @Test
    void submittingReturnsTheNewStatusWithItsPersianLabel() throws Exception {
        when(collateral.submit(CUSTOMER, REQUEST)).thenReturn(RequestStatus.PENDING_BRANCH_REVIEW);

        mvc.perform(post(BASE + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_BRANCH_REVIEW"))
                .andExpect(jsonPath("$.statusLabel").value("در انتظار بررسی شعبه"));
        verify(collateral).submit(CUSTOMER, REQUEST);
    }

    @Test
    void aRequestThatCannotBeSubmittedIs409() throws Exception {
        when(collateral.submit(CUSTOMER, REQUEST)).thenThrow(new RequestNotSubmittableException("no collateral"));

        mvc.perform(post(BASE + "/submit")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.not-submittable"));
    }

    @Test
    void onlyACustomerSessionMayCallIt() {
        assertThat(CustomerCollateralController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CUSTOMER')");
    }
}
