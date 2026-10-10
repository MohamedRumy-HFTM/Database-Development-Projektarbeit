package ch.hftm.seelandmobil.common;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Übersetzt Fehler in verständliche Antworten (RFC 9457 Problem Details).
 * Bei ungültiger Eingabe (400) steht pro Feld die Fehlermeldung unter "fehler".
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private record Regel(HttpStatus status, String meldung) {
    }

    /** Meldungen für verletzte Datenbankregeln, erkannt am Namen des Constraints. */
    private static final Map<String, Regel> DATENBANKREGELN = Map.of(
            "uq_fahrzeug_kennzeichen",
            new Regel(HttpStatus.CONFLICT, "Ein Fahrzeug mit diesem Kennzeichen existiert bereits."),
            "uq_kunde_email",
            new Regel(HttpStatus.CONFLICT, "Ein Kunde mit dieser E-Mail-Adresse existiert bereits."),
            "ex_reservation_ueberschneidung",
            new Regel(HttpStatus.CONFLICT, "Das Fahrzeug ist im gewünschten Zeitraum bereits reserviert."),
            "ck_fahrt_km",
            new Regel(HttpStatus.BAD_REQUEST, "Der km-Stand bei Rückgabe ist kleiner als der bisherige km-Stand des Fahrzeugs."),
            "uq_fahrt_reservation",
            new Regel(HttpStatus.CONFLICT, "Für diese Reservation wurde die Fahrt bereits abgeschlossen."));

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fehler = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fehler.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.");
        problem.setProperty("fehler", fehler);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Verletzte Regeln an Query-Parametern, z. B. groesse > 100. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fehler = new TreeMap<>();
        ex.getParameterValidationResults().forEach(ergebnis -> ergebnis.getResolvableErrors().forEach(
                f -> fehler.putIfAbsent(ergebnis.getMethodParameter().getParameterName(), f.getDefaultMessage())));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.");
        problem.setProperty("fehler", fehler);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Query-Parameter mit falschem Typ, z. B. status=UNBEKANNT oder ein ungültiges Datum. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Die Eingabe ist ungültig.");
        problem.setProperty("fehler", Map.of(String.valueOf(name), "ungültiger Wert '" + ex.getValue() + "'"));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Der Request-Body ist kein gültiges JSON oder enthält einen unbekannten Wert.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(UngueltigeAnfrageException.class)
    ProblemDetail ungueltig(UngueltigeAnfrageException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail nichtGefunden(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail konflikt(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail gleichzeitigGeaendert(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Die Daten wurden zwischenzeitlich geändert. Bitte neu laden.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail datenbankregelVerletzt(DataIntegrityViolationException ex) {
        String ursache = String.valueOf(ex.getMostSpecificCause().getMessage());
        // PostgreSQL nennt den Constraint in Anführungszeichen; so passt "ck_fahrt_km"
        // nicht versehentlich auf "ck_fahrt_km_start".
        Regel regel = DATENBANKREGELN.entrySet().stream()
                .filter(eintrag -> ursache.contains("\"" + eintrag.getKey() + "\""))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new Regel(HttpStatus.CONFLICT, "Die Änderung verletzt eine Datenbankregel."));
        return ProblemDetail.forStatusAndDetail(regel.status(), regel.meldung());
    }
}
