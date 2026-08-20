package com.transactioncore.paymentservice.client;

import com.transactioncore.paymentservice.client.dto.AccountAvailability;
import com.transactioncore.paymentservice.client.dto.AccountView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(@Value("${account-service.url}") String accountServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .build();
    }

    public AccountAvailability checkAvailability(String accountId) {
        try {
            AccountView accountView = restClient.get()
                    .uri("/accounts/{accountId}", accountId)
                    .retrieve()
                    .body(AccountView.class);

            if (accountView != null && "ACTIVE".equalsIgnoreCase(accountView.status())) {
                return AccountAvailability.ACTIVE;
            } else {
                return AccountAvailability.INACTIVE;
            }
        } catch (HttpClientErrorException.NotFound e) {
            return AccountAvailability.NOT_FOUND;
        }
    }

}
