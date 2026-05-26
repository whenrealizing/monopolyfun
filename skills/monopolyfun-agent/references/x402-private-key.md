# x402 Private Key Signing

Use this only for local / test agent runs that need to complete `complete_money_payment` in real OKX mode without a browser wallet.

## PaymentPayload Signing

```bash
node scripts/x402-private-key.mjs '{"requirements":{"scheme":"exact","network":"eip155:196","asset":"0x...","amount":"1000000","payTo":"0x...","maxTimeoutSeconds":300,"extra":{"name":"USD Coin","version":"2"}}}'
```

Input comes from the payment intent metadata:

```text
createIntent without paymentPayload
  -> response.paymentIntent.metadata.paymentRequirements
  -> x402-private-key.mjs
  -> payer + paymentPayload
  -> createIntent with payer/paymentPayload/syncSettle=true
```

The script reads `PRIVATE_KEY` from `.env`. It must only output derived public data and the generated payment payload. It must never print, persist, or return `PRIVATE_KEY`.

## Output Shape

```json
{
  "payer": "0x...",
  "paymentPayload": {
    "x402Version": 2,
    "accepted": {},
    "payload": {
      "authorization": {
        "from": "0x...",
        "to": "0x...",
        "value": "1000000",
        "validAfter": "1760000000",
        "validBefore": "1760000300",
        "nonce": "0x..."
      },
      "signature": "0x..."
    }
  },
  "typedData": {}
}
```

Submit the returned value to `createIntent`:

```json
{
  "payer": "0x...",
  "paymentPayload": {},
  "syncSettle": true
}
```

## Evidence Rules

Record these fields in `agent-run.jsonl` or the case evidence:

- `paymentMode`: `real_okx` or `mock`.
- `walletMode`: `private_key`, `browser_wallet`, or `mock_wallet`.
- `walletAddress`: the derived payer address.
- `paymentIntentId`: current payment intent id.
- `providerPaymentRef`: OKX settlement id / tx hash / local provider ref when available.
- `reconciliationPath`: maintainer evidence path when provider settlement requires chain reconciliation.
- `mockLayer`: empty on real success, otherwise `wallet_signature`, `wallet_reject`, `provider_callback`, `balance`, `settlement_status`, or `db_capture`.
- `mockReason`: short reason for fallback.

OKX settle/status compatibility:

- treat `transaction`
- `txHash`
- `transactionHash`

as the same chain transaction reference.

When OKX verify / settle fails, preserve the upstream error summary and then follow the active test mode:

```text
real-okx mode:
  -> record failure
  -> stop this payment branch as blocked

mock mode:
  -> record failure
  -> fake callback
  -> DB capture only if fake callback also blocks
```
