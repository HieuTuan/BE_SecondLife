package com.secondlife.secondlife.exception;

public class InsufficientProductInformationException extends AiProviderException {
    public InsufficientProductInformationException() {
        super("Chưa đủ thông tin để định giá. Hãy bổ sung loại sản phẩm, tình trạng hoạt động hoặc khuyết điểm đã biết trong mô tả.");
    }
}
