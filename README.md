# Cinema Tickets Booking Service

A Spring Boot API written in Java 21 for booking cinema tickets. It handles ticket price calculations, seat allocations, business rule checks, and calls external services for payments and seat reservations.

---

## Approach & Key Decisions

### 1. Booking Confirmation Record Extension
To make the API response more useful, I extended `BookingConfirmation` to include `seatCount` and `totalCost`. I kept the original single-argument constructor (`new BookingConfirmation(accountId)`) as an overload so any legacy calls or existing code still work without breaking.

In modern API design, returning a meaningful payload from a POST mutation endpoint is standard industry practice for several key reasons:
1. **Developer Experience & Frontend Usability** :  When a front-end client or external consumer calls a booking endpoint, receiving only {"accountId": 1234} forces them to compute or guess what actually happened. By returning seatCount and totalCost:
   * The UI can immediately display a rich confirmation banner ("Reserved 3 seats for £69.48").
   * The caller gets immediate, explicit verification of the calculated state without making a second GET request.
2. **Adherence to Open/Closed Principle**
   The rule explicitly stated: "BookingConfirmation... may be extended in a non-breaking manner if you choose to do so." 
   * Open for extension: Adding useful metadata (seatCount, totalCost).
   * Closed for modification: Preserving public BookingConfirmation(Long accountId) so legacy callers, existing unit tests, or contract verifiers never break.
3. **Backward Compatibility**
   Because Java 21 records allow custom constructor overloads, providing:

``` JAVA
public record BookingConfirmation(
    Long accountId,
    int seatCount,
    double totalCost
    ) {
/**
* Backward-compatible constructor for legacy callers or minimal initialization.
*/
public BookingConfirmation(Long accountId) {
    this(accountId, 0, 0.0);
    }
}
```
ensures that:
1. confirmation.accountId() works as before.
2. new BookingConfirmation(1234L) remains a valid, non-breaking invocation.
3. Modern callers receive a rich JSON payload:
  ``` JSON
   {
   "accountId": 1234,
   "seatCount": 3,
   "totalCost": 69.48
   }
``` 

### 2. Business Logic Implementation (CinemaTicketsServiceImpl)
Input ticket requests are aggregated by type via Java Streams, financial pricing is calculated using exact `BigDecimal` arithmetic, and all business rules are validated upfront to prevent payment or seat reservation side effects on invalid requests.

### 3. Error Handling (RestExceptionHandler)
Updated RestExceptionHandler to intercept InvalidBookingException and return ProblemDetail payload with an HTTP status of `400 Bad Request` (replacing the default 501 Not Implemented).
1. **REST Semantics**: Validation failures (e.g., child ticket without adult, exceeding 25 tickets) are client-side input errors and should return HTTP status 400 Bad Request, not 501 Not Implemented.
2. **RFC 9457 Alignment**: Setting both title and detail produces a standard, structured JSON error response:

``` JSON
{
"type": "about:blank",
"title": "Invalid Booking Request",
"status": 400,
"detail": "Child and Infant tickets cannot be purchased without an Adult ticket.",
"instance": "/cinema/bookings"
}
```

### 4. Testing & Verification Strategy
The application is tested via Mockito unit tests to verify business math, boundary rules, and isolated side effects, alongside standalone `MockMvc` controller tests to validate HTTP routing, JSON mapping, and ProblemDetail error responses without Spring context overhead.


## Business Rules & Assumptions

1. **Max Tickets**: Up to 25 tickets can be booked per request.

2. **Adult Required**: Children and Infants cannot book tickets without at least 1 Adult present.

3. **Infant Lap Rule**: Infants do not get a seat and must sit on an adult's lap. Therefore, the number of infants cannot exceed the number of adults.

4. **Seat Count**: Adults and Children each receive 1 allocated seat. Infants receive 0 seats.

5. **Account Check**: Account ID must be a positive integer greater than zero (accountId > 0).


## How to Run & Test
### 1. Run Unit and Controller Tests
``` BASH
Bash
mvn clean test
```
### 2. Build Runnable Application
``` BASH
Bash
mvn clean package
```

## Example API Request & Response

**Request**
POST /cinema/bookings

```JSON
{
"accountId": 1234,
"ticketRequests": [
{ "type": "ADULT", "ticketCount": 2 },
{ "type": "CHILD", "ticketCount": 1 },
{ "type": "INFANT", "ticketCount": 1 }
]
}
```

**Successful Response (201 Created)**

```JSON
{
"accountId": 1234,
"seatCount": 3,
"totalCost": 69.48
}
```

**Invalid Request Response (400 Bad Request)**
```JSON
{
"type": "about:blank",
"title": "Invalid Booking Request",
"status": 400,
"detail": "Child and Infant tickets cannot be purchased without an Adult ticket.",
"instance": "/cinema/bookings"
}
```