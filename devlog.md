<h2>Goal</h2>
This exercise consists of building a proof of concept of that wallet service. You have to code endpoints for these
operations:

- Get a wallet using its identifier.
- Top-up money in that wallet using a credit card number. It has to charge that amount internally using a third-party
  platform.

<h2> Outside the poc</h2>

* but we will discuss possible solutions during the interview:

- How to spend money from the wallet.
- How to refund that money.

<h3>Data structure</h3>

> Wallet
> - walletId<long> (correlates with user id - can be switched to other type) - As we don't hold user creation, any new
    id's will create a new user within the wallet
> - createdAt<Date> (timestamp)
> - Amount<BigDecimal> (current funds)
> - accountStatus<Enum> (active, pending, etc)
> - version<long> (maybe used for optimistic locking)
> - transactionList<Transaction> (list of transactions)

> Transaction
> - transactionId<long> (unique identifier)
> - idempotencyKey<String> (unique key to prevent duplicate operations)
> - wallet<Wallet> (reference to the associated wallet)
> - amount<BigDecimal> (transaction amount)
> - paymentMethod<Enum> (payment method used - credit card, etc)
> - provider<Enum> (payment gateway provider)
> - createdAt<Date> (when transaction was initiated)
> - finishedAt<Date> (when transaction was completed)
> - paymentStatus<Enum> (current status of payment)
> - ttl<Date> (time-to-live for record retention - default 30 days)
>
> Transactions are automatically timestamped on creation (`@PrePersist`)
>
>TTL is set to 30 days from creation by default
>
>Status changes should be tracked (though full history is outside current POC scope)

< Project Structure>
Refactor the project structure so that Stripe will be under a PaymentGatewayFactory. This provides modularity
to handle more providers in the future.

### Stripe Simulator Testing

**Updated Test Summary Table**

| Test Case              | Simulator Response | Required Handling                 | Validation Location | Notes                  |
|------------------------|--------------------|-----------------------------------|---------------------|------------------------|
| **Basic Tests**        |                    |                                   |                     |                        |
| Malformed JSON         | 400 Bad Request    | Throw StripeServiceException      | N/A                 | Correct handling       |
| Correct Request        | 200 OK             | Accept                            | N/A                 | No idempotency control |
| **Amount Validation**  |                    |                                   |                     |                        |
| Amount <10 EUR         | 422 Unprocessable  | Throw AmountTooSmallException     | Client-side         | Must pre-filter        |
| Negative Amount        | 422 Unprocessable  | Throw InvalidAmountException      | Client-side         | Must validate          |
| Extremely Large Amount | Accepted           | Set max amount (e.g., 10,000 EUR) | Client-side         | Fraud prevention       |
| Decimal Precision      | No limits          | Limit to 2 decimal places         | Client-side         | Currency standards     |
| **Card Validation**    |                    |                                   |                     |                        |
| Empty Card             | 200 OK             | Throw InvalidCardException        | Client-side         | Must pre-validate      |
| Invalid Card Format    | 200 OK             | Throw InvalidCardException        | Client-side         | Must pre-validate      |
| Luhn-Invalid Card      | Accepted           | Implement Luhn check              | Client-side         | Basic card validation  |
| Expired Credit Card    | Ignored            | (Optional) Implement validation   | Client-side         | Not required           |
| **Request Structure**  |                    |                                   |                     |                        |
| Null Values            | Accepted           | Validate non-null fields          | Client-side         | Required fields        |
| Missing Fields         | 400 Bad Request    | N/A                               | Simulator           | Correct handling       |
| **Advanced Cases**     |                    |                                   |                     |                        |
| Idempotency Key Reuse  | Ignored            | (Optional) Implement              | Client-side         | Not required           |

