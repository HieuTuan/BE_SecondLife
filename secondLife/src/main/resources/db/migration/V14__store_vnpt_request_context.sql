ALTER TABLE seller_verifications
    ADD COLUMN vnpt_client_session VARCHAR(255),
    ADD COLUMN vnpt_request_token VARCHAR(255);
