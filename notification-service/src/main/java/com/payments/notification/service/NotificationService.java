package com.payments.notification.service;

import com.payments.notification.event.PaymentAuthorizedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final List<PaymentAuthorizedEvent> dispatchedNotifications = Collections.synchronizedList(new ArrayList<>());

    public void dispatchPaymentNotification(PaymentAuthorizedEvent event) {
        log.info("[NOTIFICATION DISPATCHED] Push/SMS enviado para a conta {}: Pagamento {} no valor de {} {} aprovado com sucesso.",
                event.getAccountId(), event.getPaymentId(), event.getAmount(), event.getCurrency());

        dispatchedNotifications.add(event);
    }

    public List<PaymentAuthorizedEvent> getDispatchedNotifications() {
        return Collections.unmodifiableList(dispatchedNotifications);
    }
}
