package uk.gov.dwp.engineering.recruitment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.exception.RestExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CinemaTicketsService cinemaTicketsService;

    @InjectMocks
    private CinemaTicketsController cinemaTicketsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cinemaTicketsController)
                .setControllerAdvice(new RestExceptionHandler()) // Handles InvalidBookingException -> 400
                .build();
    }

    @Test
    @DisplayName("POST /cinema/bookings - 201 CREATED when purchase request is valid")
    void makeBooking_Success() throws Exception {
        Long accountId = 1234L;
        BookingConfirmation expectedConfirmation = new BookingConfirmation(accountId, 3, 69.48);

        when(cinemaTicketsService.purchaseTickets(eq(accountId), any(TicketRequest[].class)))
                .thenReturn(expectedConfirmation);

        String validRequestBody = """
        {
          "accountId": 1234,
          "ticketRequests": [
            { "type": "ADULT", "ticketCount": 2 },
            { "type": "CHILD", "ticketCount": 1 },
            { "type": "INFANT", "ticketCount": 1 }
          ]
        }
        """;

        mockMvc.perform(post("/cinema/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(validRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(1234))
                .andExpect(jsonPath("$.seatCount").value(3))
                .andExpect(jsonPath("$.totalCost").value(69.48));

        verify(cinemaTicketsService).purchaseTickets(eq(accountId), any(TicketRequest[].class));
    }

    @Test
    @DisplayName("POST /cinema/bookings - 400 BAD REQUEST when purchasing child tickets without an adult")
    void makeBooking_Rejected_ChildWithoutAdult() throws Exception {
        when(cinemaTicketsService.purchaseTickets(eq(1234L), any(TicketRequest[].class)))
                .thenThrow(new InvalidBookingException("Child and Infant tickets cannot be purchased without an Adult ticket."));

        String requestBody = """
        {
          "accountId": 1234,
          "ticketRequests": [
            { "type": "CHILD", "ticketCount": 2 }
          ]
        }
        """;

        mockMvc.perform(post("/cinema/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Booking Request"))
                .andExpect(jsonPath("$.detail").value("Child and Infant tickets cannot be purchased without an Adult ticket."));
    }

    @Test
    @DisplayName("POST /cinema/bookings - 400 BAD REQUEST when total tickets exceed 25")
    void makeBooking_Rejected_ExceedsMaxTickets() throws Exception {
        when(cinemaTicketsService.purchaseTickets(eq(1234L), any(TicketRequest[].class)))
                .thenThrow(new InvalidBookingException("Cannot purchase more than 25 tickets at a time."));

        String requestBody = """
        {
          "accountId": 1234,
          "ticketRequests": [
            { "type": "ADULT", "ticketCount": 20 },
            { "type": "CHILD", "ticketCount": 6 }
          ]
        }
        """;

        mockMvc.perform(post("/cinema/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Booking Request"))
                .andExpect(jsonPath("$.detail").value("Cannot purchase more than 25 tickets at a time."));
    }

    @Test
    @DisplayName("POST /cinema/bookings - 400 BAD REQUEST when account ID is invalid (<= 0)")
    void makeBooking_Rejected_InvalidAccountId() throws Exception {
        when(cinemaTicketsService.purchaseTickets(eq(0L), any(TicketRequest[].class)))
                .thenThrow(new InvalidBookingException("Account ID must be greater than zero."));

        String requestBody = """
        {
          "accountId": 0,
          "ticketRequests": [
            { "type": "ADULT", "ticketCount": 1 }
          ]
        }
        """;

        mockMvc.perform(post("/cinema/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Booking Request"))
                .andExpect(jsonPath("$.detail").value("Account ID must be greater than zero."));
    }

    @Test
    @DisplayName("POST /cinema/bookings - 400 BAD REQUEST when infant count exceeds adult count")
    void makeBooking_Rejected_InfantsExceedAdults() throws Exception {
        when(cinemaTicketsService.purchaseTickets(eq(1234L), any(TicketRequest[].class)))
                .thenThrow(new InvalidBookingException("Infant tickets cannot exceed Adult tickets (infants sit on adult laps)."));

        String requestBody = """
        {
          "accountId": 1234,
          "ticketRequests": [
            { "type": "ADULT", "ticketCount": 1 },
            { "type": "INFANT", "ticketCount": 2 }
          ]
        }
        """;

        mockMvc.perform(post("/cinema/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Booking Request"))
                .andExpect(jsonPath("$.detail").value("Infant tickets cannot exceed Adult tickets (infants sit on adult laps)."));
    }
}