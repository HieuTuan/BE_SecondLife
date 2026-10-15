package com.secondlife.secondlife.service.payment;
import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.regex.Pattern;
@Component @RequiredArgsConstructor
public class SePayTransferLedger {
    private final JdbcTemplate jdbc;
    private static final Pattern REFERENCE=Pattern.compile("(?<![A-Z0-9])SCL[0-9]{6}(?![A-Z0-9])",Pattern.CASE_INSENSITIVE);
    public String reference(SePayWebhookDTO event) {
        if(event.getCode()!=null && event.getCode().matches("(?i)SCL[0-9]{6}")) return event.getCode().toUpperCase(java.util.Locale.ROOT);
        var matcher=REFERENCE.matcher(event.getContent()==null?"":event.getContent());
        if(!matcher.find()) return null;
        String result=matcher.group().toUpperCase(java.util.Locale.ROOT);
        if(matcher.find()) throw new BadRequestException("Transfer contains multiple payment references");
        return result;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean claim(SePayWebhookDTO event,String code) {
        if(event.getId()==null || event.getId()<=0 || event.getTransferAmount()==null || event.getTransferAmount().signum()<=0
            || event.getTransferAmount().stripTrailingZeros().scale()>0 || event.getTransferAmount().precision()>16)
            throw new BadRequestException("SePay transaction requires a positive ID and a positive whole VND amount");
        return jdbc.update("INSERT INTO sepay_transfer_receipts(transaction_id,code,amount,received_at) VALUES (?,?,?,now()) ON CONFLICT(transaction_id) DO NOTHING",
            event.getId(),code,event.getTransferAmount())==1;
    }
}
