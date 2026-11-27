ALTER TABLE wallet_withdrawals ADD COLUMN reminder_hours INTEGER CHECK (reminder_hours IN (24,12,6,1));
