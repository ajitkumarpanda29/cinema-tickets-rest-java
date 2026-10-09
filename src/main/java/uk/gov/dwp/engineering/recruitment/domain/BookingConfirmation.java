package uk.gov.dwp.engineering.recruitment.domain;

public record BookingConfirmation(Long accountId, int seatCount, double totalCost) {

    public BookingConfirmation(Long accountId) {
        this(accountId, 0, 0.0);
    }

}
