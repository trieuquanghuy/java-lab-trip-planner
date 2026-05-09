package com.tripplanner.auth.email;

import com.tripplanner.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @Async @EventListener — Pattern 5 from 02-RESEARCH.md.
 *
 * <p>Pinned to the named "authAsyncExecutor" bean (Plan 03 AsyncConfig) so the
 * MdcCopyingTaskDecorator carries traceId/requestId/userId across the thread switch (D-22).
 *
 * <p>D-04: on MailException, log only userId + exception class — NEVER body or token.
 * D-23: recovery is via re-signup, not retry.
 *
 * <p>Body verbatim per 02-UI-SPEC.md §Email Copy Contract. Em-dash sign-off uses U+2014.
 * Subject is hard-locked: "Verify your Trip Planner account".
 */
@Component
public class EmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationSender.class);

    private final JavaMailSender mailSender;
    private final AuthProperties props;

    public EmailVerificationSender(JavaMailSender mailSender, AuthProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async("authAsyncExecutor")
    @EventListener
    public void on(VerificationEmailRequestedEvent ev) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("Trip Planner <" + props.getMail().getFrom() + ">");
        msg.setTo(ev.email());
        msg.setSubject("Verify your Trip Planner account");
        msg.setText(buildBody(ev.token()));
        try {
            mailSender.send(msg);
            log.info("Verification email sent userId={}", ev.userId());
        } catch (MailException ex) {
            // D-04: never log body, token, or recipient address. Only userId + exception class.
            log.warn("Mail send failed userId={} class={}", ev.userId(), ex.getClass().getSimpleName());
        }
    }

    /**
     * UI-SPEC §Email Copy Contract — exact body, single LF separators, UTF-8.
     * Em-dash in sign-off is U+2014 ("—").
     */
    String buildBody(String token) {
        return "Welcome to Trip Planner!\n\n"
             + "Click the link below to verify your email and activate your account:\n\n"
             + props.getVerification().getLinkBase() + token + "\n\n"
             + "This link expires in 24 hours.\n\n"
             + "If you didn't create an account, you can safely ignore this email.\n\n"
             + "— The Trip Planner team";
    }
}
