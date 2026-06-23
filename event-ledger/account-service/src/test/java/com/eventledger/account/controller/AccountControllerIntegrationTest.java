package com.eventledger.account.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Account Service REST API.
 *
 * <p>Boots the full Spring context with the in-memory H2 database (no DB mocking)
 * and exercises the controller, validation, exception handling, and persistence
 * layers end to end via {@link MockMvc}.
 *
 * <p>H2 is shared for the lifetime of the test class, so every test that writes
 * data uses a unique {@code accountId} to prevent state leakage between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postTransaction_credit_returns201WithTransactionDetails() throws Exception {
        String body = "{\"eventId\":\"e1\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(100.0));
    }

    @Test
    void postTransaction_debit_returns201() throws Exception {
        String body = "{\"eventId\":\"e2\",\"type\":\"DEBIT\",\"amount\":30.00,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-debit/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void getBalance_afterCreditAndDebit_returnsNetBalance() throws Exception {
        String credit = "{\"eventId\":\"bal-credit\",\"type\":\"CREDIT\",\"amount\":200.00,\"currency\":\"USD\"}";
        String debit = "{\"eventId\":\"bal-debit\",\"type\":\"DEBIT\",\"amount\":50.00,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-balance/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credit))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/acct-balance/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(debit))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/acct-balance/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.0));
    }

    @Test
    void getBalance_accountNotFound_returns404() throws Exception {
        mockMvc.perform(get("/accounts/nonexistent/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postTransaction_zeroAmount_returns400() throws Exception {
        String body = "{\"eventId\":\"zero-amt\",\"type\":\"CREDIT\",\"amount\":0,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-zero/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTransaction_negativeAmount_returns400() throws Exception {
        String body = "{\"eventId\":\"neg-amt\",\"type\":\"CREDIT\",\"amount\":-10,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-neg/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTransaction_missingCurrency_returns400() throws Exception {
        String body = "{\"eventId\":\"no-currency\",\"type\":\"CREDIT\",\"amount\":100.00}";

        mockMvc.perform(post("/accounts/acct-no-currency/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTransaction_missingEventId_returns400() throws Exception {
        String body = "{\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-no-eventid/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postTransaction_invalidType_returns400() throws Exception {
        String body = "{\"eventId\":\"bad-type\",\"type\":\"TRANSFER\",\"amount\":100.00,\"currency\":\"USD\"}";

        mockMvc.perform(post("/accounts/acct-bad-type/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpoint_returns200WithStatusUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
