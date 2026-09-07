package com.payments.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.dto.PaymentResult;
import com.payments.authorization.entity.IdempotencyRecord;
import com.payments.authorization.entity.IdempotencyStatus;
import com.payments.authorization.exception.ConflictException;
import com.payments.authorization.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("Deve registrar chave nova com status PROCESSING e retornar vazio")
    void shouldRegisterNewKeyWithProcessingStatus() {
        String key = UUID.randomUUID().toString();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.empty());

        Optional<PaymentResult> result = idempotencyService.checkOrReserve(key);

        assertThat(result).isEmpty();
        verify(idempotencyRecordRepository).saveAndFlush(argThat(record ->
                record.getIdempotencyKey().equals(key) && record.getStatus() == IdempotencyStatus.PROCESSING));
    }

    @Test
    @DisplayName("Deve lançar ConflictException quando chave já estiver com status PROCESSING")
    void shouldThrowConflictWhenKeyIsProcessing() {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.PROCESSING)
                .build();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.checkOrReserve(key))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já está em processamento");
    }

    @Test
    @DisplayName("Deve retornar resultado em cache quando chave já estiver com status COMPLETED")
    void shouldReturnCachedResultWhenCompleted() throws JsonProcessingException {
        String key = UUID.randomUUID().toString();
        String json = "{\"status\":\"APPROVED\"}";
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(json)
                .build();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.of(existing));

        PaymentAuthorizationResponseDto cachedDto = PaymentAuthorizationResponseDto.builder()
                .status("APPROVED")
                .build();
        when(objectMapper.readValue(json, PaymentAuthorizationResponseDto.class)).thenReturn(cachedDto);

        Optional<PaymentResult> result = idempotencyService.checkOrReserve(key);

        assertThat(result).isPresent();
        assertThat(result.get().isReplay()).isTrue();
        assertThat(result.get().responseDto().getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Deve atualizar chave para PROCESSING quando status anterior for FAILED")
    void shouldUpdateToProcessingWhenStatusWasFailed() {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.FAILED)
                .build();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.of(existing));

        Optional<PaymentResult> result = idempotencyService.checkOrReserve(key);

        assertThat(result).isEmpty();
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        verify(idempotencyRecordRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("Deve lançar ConflictException em caso de violação de integridade por concorrência")
    void shouldThrowConflictOnDataIntegrityViolation() {
        String key = UUID.randomUUID().toString();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertThatThrownBy(() -> idempotencyService.checkOrReserve(key))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("concorrente");
    }

    @Test
    @DisplayName("Deve completar idempotência gravando payload serializado e status COMPLETED")
    void shouldCompleteIdempotencySuccessfully() throws JsonProcessingException {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.PROCESSING)
                .build();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.of(existing));

        PaymentAuthorizationResponseDto responseDto = PaymentAuthorizationResponseDto.builder()
                .status("APPROVED")
                .build();
        when(objectMapper.writeValueAsString(responseDto)).thenReturn("{\"status\":\"APPROVED\"}");

        idempotencyService.complete(key, responseDto);

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(existing.getHttpStatus()).isEqualTo(200);
        assertThat(existing.getResponseBody()).isEqualTo("{\"status\":\"APPROVED\"}");
        verify(idempotencyRecordRepository).save(existing);
    }

    @Test
    @DisplayName("Deve marcar status como FAILED quando solicitado")
    void shouldMarkAsFailed() {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .status(IdempotencyStatus.PROCESSING)
                .build();
        when(idempotencyRecordRepository.findById(key)).thenReturn(Optional.of(existing));

        idempotencyService.markAsFailed(key);

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        verify(idempotencyRecordRepository).save(existing);
    }
}
