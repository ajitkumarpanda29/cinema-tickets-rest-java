package uk.gov.dwp.engineering.recruitment;

import org.springframework.stereotype.Service;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CinemaTicketsServiceImpl implements CinemaTicketsService {

  private static final int MAX_TICKETS = 25;

  private static final Map<TicketType, BigDecimal> TICKET_PRICES = Map.of(
          TicketType.INFANT, BigDecimal.ZERO,
          TicketType.CHILD, new BigDecimal("17.50"),
          TicketType.ADULT, new BigDecimal("25.99")
  );

  private final PaymentService paymentService;
  private final SeatReservationService seatReservationService;

  public CinemaTicketsServiceImpl(PaymentService paymentService,
                                  SeatReservationService seatReservationService) {
    this.paymentService = paymentService;
    this.seatReservationService = seatReservationService;
  }

  @Override
  public BookingConfirmation purchaseTickets(final Long accountId, final TicketRequest... ticketRequests)
          throws InvalidBookingException {

    validateAccountId(accountId);
    validateTicketRequestsArray(ticketRequests);

    Map<TicketType, Integer> aggregatedTickets = aggregateTicketCounts(ticketRequests);

    validateBusinessRules(aggregatedTickets);

    BigDecimal totalCost = calculateTotalCost(aggregatedTickets);
    int totalSeats = calculateTotalSeats(aggregatedTickets);

    paymentService.debitAccount(accountId, totalCost);
    seatReservationService.reserveSeats(accountId, (long) totalSeats);

    return new BookingConfirmation(accountId, totalSeats, totalCost.doubleValue());
  }

  private void validateAccountId(Long accountId) {
    if (accountId == null || accountId <= 0) {
      throw new InvalidBookingException("Account ID must be greater than zero.");
    }
  }

  private void validateTicketRequestsArray(TicketRequest... ticketRequests) {
    if (ticketRequests == null || ticketRequests.length == 0) {
      throw new InvalidBookingException("At least one ticket request must be provided.");
    }
  }

  private Map<TicketType, Integer> aggregateTicketCounts(TicketRequest... ticketRequests) {
    return Arrays.stream(ticketRequests)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(
                    TicketRequest::type,
                    Collectors.summingInt(TicketRequest::ticketCount)
            ));
  }

  private void validateBusinessRules(Map<TicketType, Integer> ticketCounts) {
    boolean hasNegativeCounts = ticketCounts.values().stream().anyMatch(count -> count < 0);
    if (hasNegativeCounts) {
      throw new InvalidBookingException("Ticket count cannot be negative.");
    }

    int totalTickets = ticketCounts.values().stream().mapToInt(Integer::intValue).sum();

    if (totalTickets <= 0) {
      throw new InvalidBookingException("Total ticket quantity must be greater than zero.");
    }

    if (totalTickets > MAX_TICKETS) {
      throw new InvalidBookingException("Cannot purchase more than " + MAX_TICKETS + " tickets at a time.");
    }

    int adultCount = ticketCounts.getOrDefault(TicketType.ADULT, 0);
    int childCount = ticketCounts.getOrDefault(TicketType.CHILD, 0);
    int infantCount = ticketCounts.getOrDefault(TicketType.INFANT, 0);

    if (adultCount == 0 && (childCount > 0 || infantCount > 0)) {
      throw new InvalidBookingException("Child and Infant tickets cannot be purchased without an Adult ticket.");
    }

    if (infantCount > adultCount) {
      throw new InvalidBookingException("Infant tickets cannot exceed Adult tickets (infants sit on adult laps).");
    }
  }

  private BigDecimal calculateTotalCost(Map<TicketType, Integer> ticketCounts) {
    return ticketCounts.entrySet().stream()
            .map(entry -> TICKET_PRICES.get(entry.getKey()).multiply(BigDecimal.valueOf(entry.getValue())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private int calculateTotalSeats(Map<TicketType, Integer> ticketCounts) {
    int adultCount = ticketCounts.getOrDefault(TicketType.ADULT, 0);
    int childCount = ticketCounts.getOrDefault(TicketType.CHILD, 0);
    return adultCount + childCount;
  }
}