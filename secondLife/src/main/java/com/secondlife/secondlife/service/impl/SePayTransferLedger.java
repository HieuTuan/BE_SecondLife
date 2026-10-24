package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.repository.SePayTransferReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

import java.util.Locale;
import java.util.regex.Pattern;

@Component @RequiredArgsConstructor
public class SePayTransferLedger {
    private final SePayTransferReceiptRepository receipts;
    private static final Pattern REFERENCE=Pattern.compile("(?<![A-Z0-9])SCL[0-9]{6}(?![A-Z0-9])",Pattern.CASE_INSENSITIVE);
    public String reference(SePayWebhookDTO event) {
        if(event.code()!=null && event.code().matches("(?i)SCL[0-9]{6}")) return event.code().toUpperCase(Locale.ROOT);
        var matcher=REFERENCE.matcher(event.content()==null?"":event.content());
        if(!matcher.find()) return null;
        String result=matcher.group().toUpperCase(Locale.ROOT);
        if(matcher.find()) throw new BadRequestException("Transfer contains multiple payment references");
        return result;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean claim(SePayWebhookDTO event,String code) {
        if(event.id()==null || event.id()<=0 || event.transferAmount()==null || event.transferAmount().signum()<=0
            || event.transferAmount().stripTrailingZeros().scale()>0 || event.transferAmount().precision()>16)
            throw new BadRequestException("SePay transaction requires a positive ID and a positive whole VND amount");
        return receipts.claimTransfer(event.id(),code,event.transferAmount());
    }
}
