package com.tripplanner.trip.api;

import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import com.tripplanner.trip.service.exception.DayNotInTripException;
import com.tripplanner.trip.service.exception.InvalidDateRangeException;
import com.tripplanner.trip.service.exception.ItemNotFoundException;
import com.tripplanner.trip.service.exception.ShortenConflictException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class TripControllerAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest req) {
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Request validation failed.");
        HttpHeaders out = new HttpHeaders();
        out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, out, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ProblemDetail> onNotFound(TripNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ErrorCode.TRIP_NOT_FOUND, "Trip not found.");
    }

    @ExceptionHandler(ShortenConflictException.class)
    public ResponseEntity<ProblemDetail> onShortenConflict(ShortenConflictException ex) {
        long totalItems = ex.getOrphanedDays().stream()
                .mapToLong(ShortenConflictException.OrphanedDayInfo::itemCount)
                .sum();
        int dayCount = ex.getOrphanedDays().size();
        String detail = String.format(
                "Shortening this trip would remove %d planned item%s from %d day%s",
                totalItems, totalItems != 1 ? "s" : "",
                dayCount, dayCount != 1 ? "s" : "");

        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.CONFLICT,
                ErrorCode.TRIP_SHORTEN_CONFLICT, detail);
        pd.setProperty("orphanedDays", ex.getOrphanedDays());
        HttpHeaders hdrs = new HttpHeaders();
        hdrs.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, hdrs, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ProblemDetail> onInvalidDates(InvalidDateRangeException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.TRIP_INVALID_DATES,
                "End date must be on or after start date.");
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ProblemDetail> onSerializationFailure(CannotAcquireLockException ex) {
        return body(HttpStatus.CONFLICT, ErrorCode.TRIP_CONCURRENT_MODIFICATION,
                "Concurrent modification detected. Please retry.");
    }

    @ExceptionHandler(DayNotInTripException.class)
    public ResponseEntity<ProblemDetail> onDayNotInTrip(DayNotInTripException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.TRIP_DAY_NOT_IN_TRIP,
                "Target day does not belong to this trip.");
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ProblemDetail> onItemNotFound(ItemNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ErrorCode.TRIP_ITEM_NOT_FOUND,
                "Itinerary item not found.");
    }

    private ResponseEntity<ProblemDetail> body(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, headers, status);
    }
}
