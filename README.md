# Wallets Service

In Playtomic, we have a service to manage our wallets. Our players can top-up their wallets using a credit card and
spend that money on our platform (bookings, racket rentals, ...)

That service has the following operations:

- You can query your balance.
- You can top-up your wallet. In this case, we charge the amount using a third-party payments platform (stripe, paypal,
  redsys).
- You can spend your balance on purchases in Playtomic.
- You can return these purchases, and your money is refunded.
- You can check your history of transactions.

This exercise consists of building a proof of concept of that wallet service.
You have to code endpoints for these operations:

1. Get a wallet using its identifier.
1. Top-up money in that wallet using a credit card number. It has to charge that amount internally using a third-party
   platform.

You don't have to write the following operations, but we will discuss possible solutions during the interview:

1. How to spend money from the wallet.
1. How to refund that money.

The basic structure of a wallet is its identifier and its current balance. If you think you need extra fields, add them.
We will discuss it in the interview.

So you can focus on these problems, you have here a maven project with a Spring Boot application. It already contains
the basic dependencies and an H2 database. There are development and test profiles.

You can also find an implementation of the service that would call to the real payments platform (StripePaymentService).
This implementation is calling to a simulator deployed in one of our environments. Take into account
that this simulator will return 422 http error codes under certain conditions.

Consider that this service must work in a microservices environment in high availability. You should care about
concurrency too.

You can spend as much time as you need but we think that 4 hours is enough to
show [the requirements of this job.](OFFER.md)
You don't have to document your code, but you can write down anything you want to explain or anything you have skipped.
You don't need to write tests for everything, but we would like to see different types of tests.

Here's a simple Markdown API specification document that anyone can use to test the WalletController API:

# Wallet API Specification

```markdown

## Base URL

`http://localhost:8090/api/wallet`

## Endpoints

### 1. Get or Create Wallet

**Endpoint:** `GET /`

**Headers:**

- `account_id`: (string, required) - User account ID (5-50 characters)
- `session_id`: (string, required) - Session ID (10-100 characters)

**Success Response:**

```json
{
  "walletId": 1,
  "accountId": "user123",
  "amount": 100.00,
  "recentTransactions": [
    {
      "transactionId": 1,
      "amount": 50.00,
      "status": "COMPLETED",
      "paymentMethod": "CARD",
      "paymentGateway": "STRIPE",
      "createdAt": "2023-01-01T00:00:00Z",
      "finishedAt": "2023-01-01T00:00:05Z"
    }
  ]
}
```

### 2. Recharge Wallet

**Endpoint:** `POST /recharge`

**Headers:**

- `Content-Type`: `application/json`

**Request Body:**

```json
{
  "account_id": "user123",
  "credit_card": "4111111111111111",
  "amount": 50.00,
  "session_id": "session1234567890"
}
```

**Field Validations:**

- `account_id`: Required, 5-50 characters
- `credit_card`: Required, 13-19 digits
- `amount`: Required, min 0.01, max 1,000,000.00
- `session_id`: Required, 10-100 characters

**Success Response:**

```json
{
  "transactionId": 2,
  "amount": 50.00,
  "status": "CREATED",
  "paymentMethod": "CARD",
  "paymentGateway": "null",
  "createdAt": "2023-01-01T00:00:00Z",
  "finishedAt": null
}
```

**Error Responses:**

- `404 Not Found`: If wallet doesn't exist

```json
{
  "message": "Wallet not found"
}
```

- `422 Unprocessable Entity`: If idempotency violation occurs

```json
{
  "message": "Transaction already exists with same idempotency key",
  "existing_transaction": {
    "transactionId": 2,
    "amount": 50.00,
    "status": "PENDING",
    "paymentMethod": "CARD",
    "paymentGateway": "STRIPE",
    "createdAt": "2023-01-01T00:00:00Z",
    "finishedAt": null
  }
}
```

## Sample Requests

### cURL Examples

**Get Wallet:**

```bash
curl -X GET "http://localhost:8090/api/wallet/" \
  -H "account_id: abc123" \
  -H "session_id: 1231211044al11"
```

Response
``
{
    "walletId": 1,
    "accountId": "abc123",
    "amount": 0,
    "recentTransactions": []
}``

**Recharge Wallet:**

```bash
curl -X POST "http://localhost:8090/api/wallet/recharge" \
  -H "Content-Type: application/json" \
  -d '{
    "account_id":"abc123",
    "credit_card":4242424242424242,
    "amount":21,
    "session_id":"1231211044al11"
}'
```

Response
``
{
    "transactionId": 1,
    "amount": 21,
    "status": "CREATED",
    "paymentMethod": "CARD",
    "paymentGateway": null,
    "createdAt": "2025-04-16T14:47:49.492+00:00",
    "finishedAt": null
}
``

```bash
curl -X GET "http://localhost:8090/api/wallet/" \
  -H "account_id: abc123" \
  -H "session_id: 1231211044al11"
```

Response
``
{
    "walletId": 1,
    "accountId": "abc123",
    "amount": 21.00,
    "recentTransactions": [
        {
            "transactionId": 1,
            "amount": 21.00,
            "status": "FINALIZED",
            "paymentMethod": "CARD",
            "paymentGateway": "STRIPE",
            "createdAt": "2025-04-16T14:47:49.492+00:00",
            "finishedAt": "2025-04-16T14:47:49.926+00:00"
        }
    ]
}``

## Testing Notes

1. The API requires both `account_id` and `session_id` headers for all requests
2. For recharge requests, the credit card number should be a valid test number (e.g., 4111111111111111 for Visa test
   card)
3. The same request with identical parameters will return a 422 error due to idempotency protection
4. You can specify a custom idempotency key in the headers
5. Amounts should be between 0.01 and 1,000,000.00

```