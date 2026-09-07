package com.payments.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.dto.PaymentResult;
import com.payments.authorization.entity.IdempotencyRecord;
import com.payments.authorization.entity.IdempotencyStatus;
import com.payments.authorization.exception.ConflictException;
import com.payments.authorization.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentResult> checkOrReserve(String idempotencyKey) {
        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findById(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Chave de idempotência {} já está com status PROCESSING", idempotencyKey);
                throw new ConflictException(String.format(
                        "Uma transação com a chave de idempotência '%s' já está em processamento.", idempotencyKey));
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Chave de idempotência {} já COMPLETED. Retornando resposta cacheada.", idempotencyKey);
                try {
                    PaymentAuthorizationResponseDto cachedResponse = objectMapper.readValue(
                            existing.getResponseBody(), PaymentAuthorizationResponseDto.class);
                    return Optional.of(new PaymentResult(cachedResponse, true));
                } catch (JsonProcessingException e) {
                    log.error("Falha ao desserializar resposta cacheada de idempotência para chave {}", idempotencyKey, e);
                    throw new RuntimeException("Erro ao processar dados de idempotência em cache", e);
                }
            }

            // Se estava FAILED, atualiza para PROCESSING para permitir nova tentativa
            existing.setStatus(IdempotencyStatus.PROCESSING);
            existing.setUpdatedAt(Instant.now());
            idempotencyRecordRepository.saveAndFlush(existing);
            return Optional.empty();
        }

        // Nova chave de idempotência: insere com status PROCESSING
        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            idempotencyRecordRepository.saveAndFlush(newRecord);
            return Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Inserção concorrente detectada para a chave {}", idempotencyKey);
            throw new ConflictException(String.format(
                    "Uma transação com a chave de idempotência '%s' já está em processamento concorrente.", idempotencyKey));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String idempotencyKey, PaymentAuthorizationResponseDto responseDto) {
        IdempotencyRecord record = idempotencyRecordRepository.findById(idempotencyKey)
                .orElseGet(() -> IdempotencyRecord.builder()
                        .idempotencyKey(idempotencyKey)
                        .createdAt(Instant.now())
                        .build());

        try {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseBody(objectMapper.writeValueAsString(responseDto));
            record.setHttpStatus(200);
            record.setUpdatedAt(Instant.now());
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Erro ao serializar payload de resposta para idempotência", e);
            throw new RuntimeException("Erro ao serializar resposta de pagamento", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(String idempotencyKey) {
        idempotencyRecordRepository.findById(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setUpdatedAt(Instant.now());
            idempotencyRecordRepository.save(record);
        });
    }
}
