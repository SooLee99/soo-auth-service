package io.soo.springboot.core.domain.local.phone.sms;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import io.soo.springboot.core.support.error.CoreException;
import io.soo.springboot.core.support.error.ErrorType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "solapi.sms", name = {"enabled"}, havingValue = "true")
public class SolapiSmsSend implements SmsSend {
    private final SolapiProps props;
    private volatile DefaultMessageService messageService;

    public SolapiSmsSend(SolapiProps props) {
        this.props = props;
    }

    @Override
    public String provider() {
        return "SOLAPI";
    }

    @Override
    public String from() {
        return props.getSendFrom();
    }

    @Override
    public void send(String to, String text) {
        Message message = new Message();
        message.setFrom(props.getSendFrom());
        message.setTo(to);
        message.setText(text);

        try {
            messageService().send(message);
        } catch (Exception e) {
            throw new CoreException(
                ErrorType.DEFAULT_ERROR,
                java.util.Map.of(
                    "reason", "SOLAPI_SEND_FAILED",
                    "detail", e.getMessage() == null ? "unknown" : e.getMessage()
                )
            );
        }
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
}
