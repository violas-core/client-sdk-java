// Copyright (c) The Diem Core Contributors
// SPDX-License-Identifier: Apache-2.0

package com.diem;

import com.diem.jsonrpc.DiemJsonRpcClient;
import com.diem.jsonrpc.InvalidResponseException;
import com.diem.jsonrpc.Retry;
import com.novi.bcs.BcsDeserializer;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.diem.utils.CurrencyCode;
import com.diem.utils.Hex;
import com.diem.types.ChainId;
import com.diem.types.SignedTransaction;
import com.diem.types.TypeTag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Testnet is utility class for handing Testnet specific data and functions.
 */
public class Testnet {
    public static String JSON_RPC_URL = "http://dev.testnet.diem.com/v1";
    public static String FAUCET_SERVER_URL = "http://dev.testnet.diem.com/mint";
    public static ChainId CHAIN_ID = new ChainId((byte) 3);
    public static String DD_ADDRESS = "000000000000000000000000000000DD";

    public static final String XUS = "XUS";
    public static final TypeTag XUS_TYPE = CurrencyCode.typeTag(XUS);

    private static final int DEFAULT_TIMEOUT = 10 * 1000;

    /**
     * Create a DiemClient connects to Testnet.
     *
     * @return `DiemClient`
     */
    public static DiemClient createClient() {
        return new DiemJsonRpcClient(JSON_RPC_URL, CHAIN_ID);
    }

    /**
     * Mint coins for given authentication key derived account address.
     *
     * @param client       a client connects to Testnet
     * @param amount       amount of coins to mint
     * @param authKey      authentication key of the account, if account does not exist onchain, a new onchain account will be created.
     * @param currencyCode currency code of the minted coins
     */
    public static void mintCoins(DiemClient client, long amount, String authKey, String currencyCode) {
        Retry<Integer> retry = new Retry<>(10, 500, Exception.class);
        try {
            retry.execute(() -> {
                List<SignedTransaction> txns = mintCoinsAsync(amount, authKey.toLowerCase(), currencyCode);
                for (SignedTransaction txn : txns) {
                    client.waitForTransaction(txn, DEFAULT_TIMEOUT);
                }
                return 0;
            });
        } catch (Exception e) {
            throw new RuntimeException("Mint coins failed", e);
        }
    }

    /**
     * This function calls to Faucet service for minting coins, but won't wait for the minting transactions executed.
     * Caller should handle waiting for returned transactions executed successfully and retry if any of the transactions failed.
     *
     * @param amount       amount to mint.
     * @param authKey      authentication key of the account receives minted coins.
     * @param currencyCode currency code of the minted coins.
     * @return List of SignedTransaction submitted by Faucet service for minting the coins
     * @throws Exception retry if exception is thrown.
     */
    public static List<SignedTransaction> mintCoinsAsync(long amount, String authKey, String currencyCode) throws Exception {
        URIBuilder builder = new URIBuilder(FAUCET_SERVER_URL);
        builder.setParameter("amount", String.valueOf(amount)).setParameter("auth_key", authKey)
                .setParameter("currency_code", currencyCode).setParameter("return_txns", "true");
        URI build = builder.build();

        HttpPost post = new HttpPost(build);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = httpClient.execute(post);
        String body = EntityUtils.toString(response.getEntity());
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new InvalidResponseException(response.getStatusLine().getStatusCode(), body);
        }
        BcsDeserializer de = new BcsDeserializer(Hex.decode(body));
        long length = de.deserialize_len();
        List<SignedTransaction> txns = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            txns.add(SignedTransaction.deserialize(de));
        }
        return txns;
    }
}
