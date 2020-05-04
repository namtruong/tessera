package com.quorum.tessera.p2p;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.partyinfo.PublishPayloadException;
import com.quorum.tessera.partyinfo.PushBatchRequest;
import com.quorum.tessera.partyinfo.ResendBatchPublisher;
import com.quorum.tessera.sync.ResendClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RestResendBatchPublisher implements ResendBatchPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestResendBatchPublisher.class);

    private final PayloadEncoder payloadEncoder;

    private final ResendClient resendClient;

    public RestResendBatchPublisher(ResendClient resendClient) {
        this(PayloadEncoder.create(), resendClient);
    }

    public RestResendBatchPublisher(final PayloadEncoder payloadEncoder, final ResendClient resendClient) {
        this.payloadEncoder = Objects.requireNonNull(payloadEncoder);
        this.resendClient = Objects.requireNonNull(resendClient);
    }

    @Override
    public void publishBatch(final List<EncodedPayload> payloads, final String targetUrl) {

        LOGGER.info("Publishing message to {}", targetUrl);

        final List<byte[]> encodedPayloads = payloads.stream().map(payloadEncoder::encode).collect(Collectors.toList());

        final PushBatchRequest pushBatchRequest = new PushBatchRequest(encodedPayloads);

        final boolean result = resendClient.pushBatch(targetUrl, pushBatchRequest);

        if (!result) {
            throw new PublishPayloadException("Unable to push payload batch to recipient " + targetUrl);
        }

        LOGGER.info("Published to {}", targetUrl);
    }
}