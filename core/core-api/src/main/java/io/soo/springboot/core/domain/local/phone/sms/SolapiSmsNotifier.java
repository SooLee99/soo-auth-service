package io.soo.springboot.core.domain.local.phone.sms;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import io.soo.springboot.core.domain.SmsAdmin;
import io.soo.springboot.core.domain.local.phone.PhoneNotifier;
import io.soo.springboot.core.support.error.CoreException;
import io.soo.springboot.core.support.error.ErrorType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = {"enabled"}, havingValue = "true")
public class SolapiSmsNotifier implements PhoneNotifier {
    private final SolapiProps props;
    private final SmsAdmin smsAdmin;
    private volatile DefaultMessageService messageService;

    public SolapiSmsNotifier(SolapiProps props, SmsAdmin smsAdmin) {
        this.props = props;
        this.smsAdmin = smsAdmin;
    }

    private DefaultMessageService messageService() {
        if (messageService != null) {
            return messageService;
        }
        synchronized (this) {
            if (messageService != null) {
                return messageService;
            }
            if (props.getApiKey().isBlank() || props.getApiSecret().isBlank() || props.getSendFrom().isBlank()) {
                throw new CoreException(
                    ErrorType.INVALID_REQUEST,
                    java.util.Map.of("reason", "SOLAPI_CONFIG_MISSING")
                );
            }
            messageService = SolapiClient.INSTANCE.createInstance(props.getApiKey(), props.getApiSecret());
            return messageService;
        }
    }

    @Override
    public void sendCode(String phoneNumber, String code, long expiresInSec) {
        String text = "[soo-auth] 인증번호 [" + code + "] (유효 " + (expiresInSec / 60) + "분)";
        Message message = new Message();
        message.setFrom(props.getSendFrom());
        message.setTo(phoneNumber);
        message.setText(text);

        try {
            messageService().send(message);
            smsAdmin.ok(phoneNumber, props.getSendFrom(), text, "SOLAPI");
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "unknown" : e.getMessage();
            smsAdmin.fail(phoneNumber, props.getSendFrom(), text, "SOLAPI", null, detail);
            throw new CoreException(
                ErrorType.DEFAULT_ERROR,
                java.util.Map.of(
                    "reason", "SOLAPI_SEND_FAILED",
                    "detail", detail
                )
            );
        }
    }
}
