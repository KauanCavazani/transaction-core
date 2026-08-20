package com.transactioncore.ledgerservice.client;

import com.transactioncore.ledgerservice.client.dto.AccountOperationRequest;
import com.transactioncore.ledgerservice.client.dto.OperationResult;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(@Value("${account-service.url}") String accountServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(accountServiceUrl)
                .build();
    }

    public OperationResult debit(UUID accountId, UUID operationId, Money amount) {
        AccountOperationRequest requestBody = new AccountOperationRequest(operationId, amount.amount(), amount.currency().getCurrencyCode());
        OperationResult result;
        try {
            restClient.post()
                    .uri("/accounts/{accountId}/debits", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            result = OperationResult.SUCCESS;
        } catch (HttpClientErrorException.NotFound ex) {
            result =  OperationResult.ACCOUNT_NOT_FOUND;
        } catch (HttpClientErrorException.UnprocessableContent ex) {
            result = OperationResult.INSUFFICIENT_FUNDS;
        }

        return result;
    }

    public OperationResult credit(UUID accountId, UUID operationId, Money amount) {
        AccountOperationRequest requestBody = new AccountOperationRequest(operationId, amount.amount(), amount.currency().getCurrencyCode());
        OperationResult result;
        try {
            restClient.post()
                    .uri("/accounts/{accountId}/credits", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            result = OperationResult.SUCCESS;
        } catch (HttpClientErrorException.NotFound ex) {
            result =  OperationResult.ACCOUNT_NOT_FOUND;
        } catch (HttpClientErrorException.UnprocessableContent ex) {
            result = OperationResult.INSUFFICIENT_FUNDS;
        }

        return result;
    }

}
