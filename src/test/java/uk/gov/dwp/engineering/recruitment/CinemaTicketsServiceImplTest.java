package uk.gov.dwp.engineering.recruitment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsServiceImplTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private SeatReservationService seatReservationService;

    private CinemaTicketsServiceImpl cinemaTicketsService;

    @BeforeEach
    void setUp() {
        cinemaTicketsService = new CinemaTicketsServiceImpl(paymentService, seatReservationService);
    }

    @Test
    @DisplayName("Should process valid purchase with correct cost and seat count")
    void shouldProcessValidPurchase() {
        Long accountId = 1L;
        TicketRequest adultRequest = new TicketRequest(TicketType.ADULT, 2);
        TicketRequest childRequest = new TicketRequest(TicketType.CHILD, 1);
        TicketRequest infantRequest = new TicketRequest(TicketType.INFANT, 1);

        // Price Calculation: (2 * 25.99) + (1 * 17.50) + (1 * 0.00) = 69.48
        // Seat Calculation: 2 Adults + 1 Child = 3 seats
        BookingConfirmation confirmation = cinemaTicketsService.purchaseTickets(
                accountId, adultRequest, childRequest, infantRequest);

        assertEquals(accountId, confirmation.accountId());
        assertEquals(3, confirmation.seatCount());
        assertEquals(69.48, confirmation.totalCost());

        verify(paymentService, times(1)).debitAccount(accountId, new BigDecimal("69.48"));
        verify(seatReservationService, times(1)).reserveSeats(accountId, 3L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L})
    @DisplayName("Should throw exception when account ID is invalid")
    void shouldRejectInvalidAccountId(Long invalidAccountId) {
        TicketRequest adultRequest = new TicketRequest(TicketType.ADULT, 1);

        InvalidBookingException exception = assertThrows(
                InvalidBookingException.class,
                () -> cinemaTicketsService.purchaseTickets(invalidAccountId, adultRequest)
        );

        assertEquals("Account ID must be greater than zero.", exception.getMessage());
        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Test
    @DisplayName("Should throw exception when purchasing more than 25 tickets")
    void shouldRejectExceedingMaxTickets() {
        Long accountId = 1L;
        TicketRequest adultRequest = new TicketRequest(TicketType.ADULT, 20);
        TicketRequest childRequest = new TicketRequest(TicketType.CHILD, 6); // Total = 26

        assertThrows(
                InvalidBookingException.class,
                () -> cinemaTicketsService.purchaseTickets(accountId, adultRequest, childRequest)
        );

        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Test
    @DisplayName("Should throw exception when purchasing child tickets without adult")
    void shouldRejectChildWithoutAdult() {
        Long accountId = 1L;
        TicketRequest childRequest = new TicketRequest(TicketType.CHILD, 2);

        assertThrows(
                InvalidBookingException.class,
                () -> cinemaTicketsService.purchaseTickets(accountId, childRequest)
        );

        verifyNoInteractions(paymentService, seatReservationService);
    }

    @Test
    @DisplayName("Should throw exception when infant count exceeds adult count")
    void shouldRejectInfantsExceedingAdults() {
        Long accountId = 1L;
        TicketRequest adultRequest = new TicketRequest(TicketType.ADULT, 1);
        TicketRequest infantRequest = new TicketRequest(TicketType.INFANT, 2);

        assertThrows(
                InvalidBookingException.class,
                () -> cinemaTicketsService.purchaseTickets(accountId, adultRequest, infantRequest)
        );

        verifyNoInteractions(paymentService, seatReservationService);
    }
}
