# Wallet Service Proof of Concept

## Goal

Build a wallet service with these core features:

1. Get wallet details by identifier
2. Top up money using a credit card via a third-party payment platform

## Out of Scope (Discussion Topics)

We'll discuss these potential features during the interview:

- Spending money from the wallet
- Processing refunds

## Data Structure

### Wallet

- `walletId` (Long) - Unique identifier (can link to user ID)
    - New IDs automatically create new wallets (no separate user creation)
- `createdAt` (Date) - Creation timestamp
- `amount` (BigDecimal) - Current balance
- `accountStatus` (Enum) - Active, Pending, etc.
- `version` (Long) - For optimistic locking
- `transactionList` - Last 10 transactions (sorted by date)

### Transaction

- `transactionId` (Long) - Unique ID
- `idempotencyKey` (String) - Prevents duplicate operations
- `wallet` - Linked wallet reference
- `amount` (BigDecimal) - Transaction value
- `paymentMethod` (Enum) - Credit card, etc.
- `provider` (Enum) - Payment gateway
- `createdAt` (Date) - Start timestamp
- `finishedAt` (Date) - Completion timestamp
- `paymentStatus` (Enum) - Current state
- `ttl` (Date) - Auto-delete after 30 days (default)
- `version` used for optimistic locking

Dto's for fields exposure

Features:

- Automatic timestamping on creation (`@PrePersist`)
- Status changes tracked (full history not implemented)

## Project Structure

- Payment gateways organized under `PaymentGatewayFactory`
- Modular design for easy addition of new providers

## Stripe Simulator Tests

| Test Case              | Response          | Handling Required             | Validation Level | Notes                  |
|------------------------|-------------------|-------------------------------|------------------|------------------------|
| **Basic Tests**        |                   |                               |                  |                        |
| Malformed JSON         | 400 Bad Request   | Throw StripeServiceException  | N/A              | Correct                |
| Valid Request          | 200 OK            | Accept                        | N/A              | No idempotency control |
| **Amount Checks**      |                   |                               |                  |                        |
| Amount <10 EUR         | 422 Unprocessable | Throw AmountTooSmallException | Client           | Pre-filter             |
| Negative Amount        | 422 Unprocessable | Throw InvalidAmountException  | Client           | Must validate          |
| Very Large Amount      | Accepted          | Set max (e.g., 10,000 EUR)    | Client           | Fraud prevention       |
| Decimal Precision      | No limits         | Limit to 2 decimals           | Client           | Currency standard      |
| **Card Validation**    |                   |                               |                  |                        |
| Empty Card             | 200 OK            | Throw InvalidCardException    | Client           | Pre-validate           |
| Invalid Card Format    | 200 OK            | Throw InvalidCardException    | Client           | Pre-validate           |
| Invalid Luhn Card      | Accepted          | Add Luhn check                | Client           | Basic validation       |
| Expired Card           | Ignored           | (Optional) Add validation     | Client           | Not required           |
| **Request Validation** |                   |                               |                  |                        |
| Null Values            | Accepted          | Validate required fields      | Client           | Required fields        |
| Missing Fields         | 400 Bad Request   | N/A                           | Simulator        | Correct                |
| **Advanced Cases**     |                   |                               |                  |                        |
| Duplicate Idempotency  | Ignored           | (Optional) Implement          | Client           | Not required           |

## Development Notes

### Transaction Status Flow

Proposed states:  
`CREATED → SUBMITTED → SUCCESSFUL/FINALIZED/DENIED`  
(Implement state machine for transitions)

### Nice to have Features

- Multiple payment methods
- Payment gateway factory
- Circuit breaker for charge calls
- Idempotency
- Concurrency control ** Use optimistic locking

### Out of Scope

- User creation (auto-handled)
- Credit card/funds validation
    - Luhn algorithm
- Denied payment handling
- Scheduled jobs for:
    - Resubmitting unsubmitted transactions
    - Cancelling stale transactions
    - Checking pending responses
    - Finalizing successful transactions
    - Cleaning old transactions (>30 days)
- Retry mechanism in case external API fails
    - can easily be added later (avoided to not deal with failure scenarios other than CB)
- Implement exponential backoff for 422 responses
- Cache wallet balances with short TTL (30s)
- Currency support

## Workflows

### Get Wallet Flow

1. Find wallet by user ID
2. Create new wallet if not found
3. Return wallet with last 10 transactions

### Top-Up Flow

1. Verify wallet exists (404 if not)
2. Create transaction record
3. Start async payment process
4. Return 201 Accepted with transaction

Async Steps:

1. Submit payment request
2. Update transaction from payment response
3. Update wallet if successful

