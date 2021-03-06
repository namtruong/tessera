package com.quorum.tessera.p2p;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.enclave.PrivacyMode;
import com.quorum.tessera.p2p.recovery.PushBatchRequest;
import com.quorum.tessera.recovery.workflow.BatchResendManager;
import com.quorum.tessera.transaction.TransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.ws.rs.core.Response;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class RecoveryResourceTest {

    private RecoveryResource recoveryResource;

    private BatchResendManager resendManager;

    private TransactionManager transactionManager;

    private PayloadEncoder payloadEncoder;

    @Before
    public void onSetup() {
        resendManager = mock(BatchResendManager.class);
        transactionManager = mock(TransactionManager.class);
        payloadEncoder = mock(PayloadEncoder.class);
        recoveryResource = new RecoveryResource(transactionManager, resendManager, payloadEncoder);

    }

    @After
    public void onTearDown() {
        verifyNoMoreInteractions(transactionManager, resendManager, payloadEncoder);
    }

    @Test
    public void pushBatch() {
        PushBatchRequest pushBatchRequest = new PushBatchRequest(Collections.singletonList("SomeData".getBytes()));
        Response result = recoveryResource.pushBatch(pushBatchRequest);
        assertThat(result.getStatus()).isEqualTo(200);
        ArgumentCaptor<com.quorum.tessera.recovery.resend.PushBatchRequest> argCaptor =
                ArgumentCaptor.forClass(com.quorum.tessera.recovery.resend.PushBatchRequest.class);
        verify(resendManager).storeResendBatch(argCaptor.capture());

        com.quorum.tessera.recovery.resend.PushBatchRequest capturedRequest = argCaptor.getValue();

        assertThat(capturedRequest).isNotNull();
        assertThat(capturedRequest.getEncodedPayloads()).containsExactly("SomeData".getBytes());
    }

    @Test
    public void pushAllowedForStandardPrivate() {
        final byte[] someData = "SomeData".getBytes();
        final EncodedPayload payload = mock(EncodedPayload.class);
        when(payload.getPrivacyMode()).thenReturn(PrivacyMode.STANDARD_PRIVATE);
        when(payloadEncoder.decode(someData)).thenReturn(payload);

        final Response result = recoveryResource.push(someData);

        assertThat(result.getStatus()).isEqualTo(201);
        assertThat(result.hasEntity()).isTrue();
        verify(transactionManager).storePayload(payload);
        verify(payloadEncoder).decode(someData);
    }

    @Test
    public void pushNotAllowedForEnhancedPrivacy() {
        final byte[] someData = "SomeData".getBytes();
        final EncodedPayload payload = mock(EncodedPayload.class);
        when(payload.getPrivacyMode()).thenReturn(PrivacyMode.PRIVATE_STATE_VALIDATION);
        when(payloadEncoder.decode(someData)).thenReturn(payload);

        final Response result = recoveryResource.push(someData);

        assertThat(result.getStatus()).isEqualTo(403);
        verify(payloadEncoder).decode(someData);
    }
}
