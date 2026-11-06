package com.ledgerline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.enums.IdempotencyStatus;
import com.ledgerline.exception.IdempotencyConflictException;
import com.ledgerline.repository.IdempotencyRecordRepository;
import com.ledgerline.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks or initiates an idempotency record.
     * Uses REQUIRES_NEW to immediately commit the initial lock in the database.
     *
     * @return Optional containing cached response if already processed, or empty if new.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> checkOrLock(String idempotencyKey, Long userId, String requestPath, Object payload, Class<T> responseType) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency-Key header is required for this operation");
        }

        String payloadJson = serializePayload(payload);
        String requestHash = HashUtil.sha256(payloadJson);

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();

            // Validate that the payload matches the original request
            if (!existing.getRequestHash().equals(requestHash)) {
                log.warn("Idempotency key {} reused with different payload!", idempotencyKey);
                throw new IdempotencyConflictException("Idempotency key reuse detected with different request parameters");
            }

            if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Concurrent request with idempotency key {} is already processing", idempotencyKey);
                throw new IdempotencyConflictException("A request with this idempotency key is currently being processed. Please retry shortly.");
            }

            if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Returning cached completed result for idempotency key {}", idempotencyKey);
                return Optional.ofNullable(deserializeResponse(existing.getResponseBody(), responseType));
            }

            if (existing.getStatus() == IdempotencyStatus.FAILED) {
                log.info("Returning cached failure result for idempotency key {}", idempotencyKey);
                throw new IdempotencyConflictException("Original request failed: " + existing.getResponseBody());
            }
        }

        // Key does not exist: create record in PROCESSING state
        try {
            IdempotencyRecord newRecord = new IdempotencyRecord(idempotencyKey, userId, requestPath, requestHash);
            idempotencyRecordRepository.saveAndFlush(newRecord);
            return Optional.empty();
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted between our read and write!
            log.warn("Concurrent insert race condition caught for key {}", idempotencyKey);
            throw new IdempotencyConflictException("A request with this idempotency key is currently being processed. Please retry shortly.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String idempotencyKey, int responseCode, Object responseBody) {
        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseCode(responseCode);
            record.setResponseBody(serializePayload(responseBody));
            idempotencyRecordRepository.saveAndFlush(record);
            log.info("Idempotency key {} marked COMPLETED", idempotencyKey);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String idempotencyKey, int responseCode, String errorMessage) {
        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setResponseCode(responseCode);
            record.setResponseBody(errorMessage);
            idempotencyRecordRepository.saveAndFlush(record);
            log.info("Idempotency key {} marked FAILED", idempotencyKey);
        });
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }

    private <T> T deserializeResponse(String json, Class<T> clazz) {
        if (json == null || clazz == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize cached response: {}", e.getMessage());
            return null;
        }
    }
}
