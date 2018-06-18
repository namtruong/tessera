package com.github.nexus.enclave;

import com.github.nexus.api.model.ApiPath;
import com.github.nexus.enclave.model.MessageHash;
import com.github.nexus.key.KeyManager;
import com.github.nexus.nacl.Key;
import com.github.nexus.node.PartyInfoService;
import com.github.nexus.node.PostDelegate;
import com.github.nexus.transaction.PayloadEncoder;
import com.github.nexus.transaction.TransactionService;
import com.github.nexus.transaction.model.EncodedPayload;
import com.github.nexus.transaction.model.EncodedPayloadWithRecipients;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

public class EnclaveImpl implements Enclave {

    private final TransactionService transactionService;

    private final PartyInfoService partyInfoService;

    private final PayloadEncoder payloadEncoder;

    private final PostDelegate postDelegate;

    private final KeyManager keyManager;

    public EnclaveImpl(final TransactionService transactionService,
                       final PartyInfoService partyInfoService,
                       final PayloadEncoder payloadEncoder,
                       final PostDelegate postDelegate,
                       final KeyManager keyManager) {
        this.transactionService = requireNonNull(transactionService, "transactionService cannot be null");
        this.partyInfoService = requireNonNull(partyInfoService, "partyInfoService cannot be null");
        this.payloadEncoder = requireNonNull(payloadEncoder, "encoder cannot be null");
        this.postDelegate = requireNonNull(postDelegate, "postDelegate cannot be null");
        this.keyManager = requireNonNull(keyManager, "keyManager cannot be null");
    }

    @Override
    public boolean delete(final byte[] hashBytes) {
        final MessageHash messageHash = new MessageHash(hashBytes);

        return transactionService.delete(messageHash);
    }

    @Override
    public byte[] receive(final byte[] key, final byte[] to) {
        return transactionService.retrieveUnencryptedTransaction(new MessageHash(key), new Key(to));
    }

    @Override
    public MessageHash store(final Optional<byte[]> sender, final byte[][] recipients, final byte[] message) {

        final Key senderPublicKey = sender
            .map(Key::new)
            .orElseGet(keyManager::defaultPublicKey);

        final List<Key> recipientList = Stream
            .of(recipients)
            .map(Key::new)
            .collect(Collectors.toList());

        EncodedPayloadWithRecipients encryptedPayload =
            transactionService.encryptPayload(message, senderPublicKey, recipientList);

        MessageHash messageHash = transactionService.storeEncodedPayload(encryptedPayload);

        recipientList.forEach(recipient -> publishPayload(encryptedPayload, recipient));

        return messageHash;

    }

    @Override
    public MessageHash storePayload(final byte[] payload) {
        EncodedPayloadWithRecipients encodedPayloadWithRecipients =
            payloadEncoder.decodePayloadWithRecipients(payload);

        return transactionService.storeEncodedPayload(encodedPayloadWithRecipients);
    }

    @Override
    public void publishPayload(final EncodedPayloadWithRecipients encodedPayloadWithRecipients,
                               final Key recipientKey) {

        final String targetUrl = partyInfoService.getURLFromRecipientKey(recipientKey);

        if (!partyInfoService.getPartyInfo().getUrl().equals(targetUrl)) {

            final EncodedPayload encodedPayload = encodedPayloadWithRecipients.getEncodedPayload();

            final int index = encodedPayloadWithRecipients.getRecipientKeys().indexOf(recipientKey);

            final EncodedPayloadWithRecipients encodedPayloadWithOneRecipient =
                new EncodedPayloadWithRecipients(
                    new EncodedPayload(
                        encodedPayload.getSenderKey(),
                        encodedPayload.getCipherText(),
                        encodedPayload.getCipherTextNonce(),
                        singletonList(encodedPayload.getRecipientBoxes().get(index)),
                        encodedPayload.getRecipientNonce()
                    ),
                    emptyList()
                );

            final byte[] encoded = payloadEncoder.encode(encodedPayloadWithOneRecipient);

            postDelegate.doPost(targetUrl, ApiPath.PUSH, encoded);
        }
    }

    @Override
    public void resendAll(byte[] recipientPublicKey) {
        Key recipient = new Key(recipientPublicKey);
        Collection<EncodedPayloadWithRecipients> payloads = transactionService.retrieveAllForRecipient(recipient);

        payloads.forEach(payload -> {
            payload.getRecipientKeys().forEach(recipientKey -> {
                publishPayload(payload, recipientKey);
            });
        });
    }
}
