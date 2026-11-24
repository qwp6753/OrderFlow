-- 在已执行004的库中备份停写后执行一次。保留现有记录。
ALTER TABLE return_request DROP CHECK ck_return_status;
UPDATE return_request SET status = CASE status
  WHEN 'REQUESTED' THEN 'PENDING_REVIEW'
  WHEN 'APPROVED' THEN 'PENDING_RECEIPT'
  WHEN 'RECEIVED' THEN 'COMPLETED'
  ELSE status END;
ALTER TABLE return_request
  ADD COLUMN reviewed_by BIGINT NULL,
  ADD COLUMN reviewed_at DATETIME NULL,
  ADD COLUMN rejection_reason VARCHAR(500) NULL,
  ADD COLUMN received_by BIGINT NULL,
  ADD COLUMN received_at DATETIME NULL,
  ADD COLUMN receipt_remark VARCHAR(500) NULL,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD CONSTRAINT fk_return_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
  ADD CONSTRAINT fk_return_receiver FOREIGN KEY (received_by) REFERENCES sys_user(id),
  ADD CONSTRAINT ck_return_status CHECK (status IN ('PENDING_REVIEW','REJECTED','PENDING_RECEIPT','COMPLETED'));
CREATE INDEX idx_return_item_order_item ON return_item(order_item_id);
